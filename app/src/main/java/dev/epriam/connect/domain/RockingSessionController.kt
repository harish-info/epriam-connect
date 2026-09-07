package dev.epriam.connect.domain

import dev.epriam.connect.protocol.PriamProtocol
import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.protocol.RockingNotification
import dev.epriam.connect.protocol.RockingProtocolError
import dev.epriam.connect.protocol.RockingRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class RockingSessionController(
    private val scope: CoroutineScope,
    private val currentState: () -> PriamUiState,
    private val updateState: ((PriamUiState) -> PriamUiState) -> Unit,
    private val writeRocking: suspend (ByteArray) -> Unit,
    private val onDiagnostic: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var demoCountdown: Job? = null
    private val liveUpdates = RockingUpdateController(
        scope = scope,
        writeRocking = writeRocking,
        onDiagnostic = onDiagnostic,
        onError = onError,
    )

    fun updateIntensity(intensity: RockingIntensity) {
        val active = currentState().rockingState as? RockingState.Active ?: return
        if (active.intensity == intensity && liveUpdates.pendingRequest == null) return
        if (currentState().isDemo) {
            runDemoCountdown(intensity, active.remainingSeconds)
            return
        }
        val request = (liveUpdates.pendingRequest ?: active.toUpdateRequest()).copy(intensity = intensity)
        liveUpdates.schedule(request)
    }

    fun updateDuration(durationMinutes: Int) {
        val active = currentState().rockingState as? RockingState.Active ?: return
        val durationSeconds = durationMinutes * 60
        if (currentState().isDemo) {
            runDemoCountdown(active.intensity, durationSeconds)
            return
        }
        val request = (liveUpdates.pendingRequest ?: active.toUpdateRequest()).copy(
            durationSeconds = durationSeconds,
        )
        liveUpdates.schedule(request)
    }

    fun start() {
        val state = currentState()
        if (!state.isReady || state.motionMayBeActive) return

        liveUpdates.cancel()
        val durationSeconds = state.selectedDurationMinutes * 60
        updateState {
            it.copy(rockingState = RockingState.Starting(state.selectedIntensity, durationSeconds))
        }
        if (state.isDemo) {
            runDemoCountdown(state.selectedIntensity, durationSeconds)
            return
        }
        sendStartCommand(state.selectedIntensity, durationSeconds)
    }

    fun stop() {
        liveUpdates.cancel()
        demoCountdown?.cancel()
        if (currentState().isDemo) {
            updateState { it.copy(rockingState = RockingState.Off) }
            onDiagnostic("Demo rocking stopped")
            return
        }
        if (!currentState().isReady) return

        updateState { it.copy(rockingState = RockingState.Stopping) }
        sendStopCommand()
    }

    fun acknowledgeStopped() {
        if (currentState().rockingState !is RockingState.Unconfirmed) return
        updateState { state ->
            state.copy(rockingState = RockingState.Off, statusMessage = state.connectionStatus())
        }
        onDiagnostic("Operator verified the stroller is stopped")
    }

    fun observe(notification: RockingNotification) {
        liveUpdates.observe(notification)
        updateState { it.copy(rockingState = notification.toRockingState()) }
    }

    fun cancelPendingWork() {
        liveUpdates.cancel()
        demoCountdown?.cancel()
        demoCountdown = null
    }

    private fun sendStartCommand(intensity: RockingIntensity, durationSeconds: Int) {
        scope.launch {
            val packet = PriamProtocol.encodeRocking(RockingRequest(intensity, durationSeconds))
            runCatching { writeRocking(packet) }
                .onSuccess {
                    onDiagnostic("Rocking write ${PriamProtocol.toHex(packet)}")
                    delay(START_CONFIRMATION_MILLIS)
                    updateState { state ->
                        if (state.rockingState is RockingState.Starting) {
                            state.copy(
                                rockingState = RockingState.Unconfirmed(
                                    "Command sent but the stroller did not confirm it",
                                ),
                            )
                        } else {
                            state
                        }
                    }
                }
                .onFailure { error ->
                    onError("Rocking write failed: ${error.message}")
                    updateState { state ->
                        state.copy(
                            rockingState = RockingState.Unconfirmed(
                                "Rocking write was not confirmed; physically verify the stroller is still",
                            ),
                        )
                    }
                }
        }
    }

    private fun sendStopCommand() {
        scope.launch {
            val packet = PriamProtocol.encodeStopCandidate()
            runCatching { writeRocking(packet) }
                .onSuccess {
                    onDiagnostic("Stop write ${PriamProtocol.toHex(packet)}")
                    delay(STOP_CONFIRMATION_MILLIS)
                    updateState { state ->
                        if (state.rockingState is RockingState.Stopping) {
                            state.copy(
                                rockingState = RockingState.Unconfirmed(
                                    "Stop sent; verify the stroller stopped",
                                ),
                            )
                        } else {
                            state
                        }
                    }
                }
                .onFailure { error ->
                    onError("Stop write failed: ${error.message}")
                    updateState { state ->
                        state.copy(
                            rockingState = RockingState.Unconfirmed(
                                "Stop was not confirmed; physically verify the stroller stopped",
                            ),
                        )
                    }
                }
        }
    }

    private fun runDemoCountdown(intensity: RockingIntensity, durationSeconds: Int) {
        demoCountdown?.cancel()
        demoCountdown = scope.launch {
            var remaining = durationSeconds
            while (remaining > 0) {
                updateState {
                    it.copy(
                        rockingState = RockingState.Active(
                            intensity = intensity,
                            remainingSeconds = remaining,
                            configuredSeconds = durationSeconds,
                            linkLossFlagSet = false,
                        ),
                    )
                }
                delay(1_000)
                remaining--
            }
            updateState { it.copy(rockingState = RockingState.Off) }
        }
    }

    private fun RockingNotification.toRockingState(): RockingState = when {
        error is RockingProtocolError.BrakeNotEngaged -> RockingState.Rejected(
            error,
            "Engage the parking brake and lock the front wheels",
        )
        error != null -> RockingState.Rejected(error, "The stroller rejected the rocking command")
        isActive -> RockingState.Active(
            intensity = checkNotNull(intensity),
            remainingSeconds = remainingSeconds,
            configuredSeconds = configuredSeconds,
            linkLossFlagSet = linkLossFlagSet,
        )
        else -> RockingState.Off
    }

    private fun PriamUiState.connectionStatus(): String = when (connectionPhase) {
        ConnectionPhase.READY -> "Connected"
        ConnectionPhase.DEMO -> "Demo stroller connected"
        else -> statusMessage
    }

    private companion object {
        const val START_CONFIRMATION_MILLIS = 3_000L
        const val STOP_CONFIRMATION_MILLIS = 2_000L
    }
}

internal fun RockingState.Active.toUpdateRequest(
    intensity: RockingIntensity = this.intensity,
    durationSeconds: Int = remainingSeconds.coerceAtLeast(1),
) = RockingRequest(
    intensity = intensity,
    durationSeconds = durationSeconds,
    linkLossFlagSet = linkLossFlagSet,
)
