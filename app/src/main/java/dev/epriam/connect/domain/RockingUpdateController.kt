package dev.epriam.connect.domain

import dev.epriam.connect.protocol.PriamProtocol
import dev.epriam.connect.protocol.RockingNotification
import dev.epriam.connect.protocol.RockingRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class RockingUpdateController(
    private val scope: CoroutineScope,
    private val writeRocking: suspend (ByteArray) -> Unit,
    private val onDiagnostic: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var updateJob: Job? = null

    var pendingRequest: RockingRequest? = null
        private set

    fun schedule(request: RockingRequest) {
        pendingRequest = request
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(UPDATE_DEBOUNCE_MILLIS)
            val packet = PriamProtocol.encodeRocking(request)
            runCatching { writeRocking(packet) }
                .onSuccess {
                    onDiagnostic("Rocking adjustment ${PriamProtocol.toHex(packet)}")
                    waitForConfirmation(request)
                }
                .onFailure { error -> handleWriteFailure(request, error) }
        }
    }

    fun confirm(notification: RockingNotification) {
        val pending = pendingRequest ?: return
        if (
            notification.intensity == pending.intensity &&
            notification.configuredSeconds == pending.durationSeconds
        ) {
            pendingRequest = null
            onDiagnostic("Rocking adjustment confirmed")
        }
    }

    fun cancel() {
        updateJob?.cancel()
        updateJob = null
        pendingRequest = null
    }

    private suspend fun waitForConfirmation(request: RockingRequest) {
        delay(UPDATE_CONFIRMATION_MILLIS)
        if (pendingRequest == request) {
            pendingRequest = null
            onError("Rocking adjustment was not confirmed by the stroller")
        }
    }

    private fun handleWriteFailure(request: RockingRequest, error: Throwable) {
        if (error is CancellationException) throw error
        if (pendingRequest == request) pendingRequest = null
        onError("Rocking adjustment failed: ${error.message}")
    }

    private companion object {
        const val UPDATE_DEBOUNCE_MILLIS = 250L
        const val UPDATE_CONFIRMATION_MILLIS = 3_000L
    }
}
