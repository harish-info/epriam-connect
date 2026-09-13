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
    private var onPendingConfirmed: (() -> Unit)? = null
    private var onPendingCancelled: (() -> Unit)? = null

    var pendingRequest: RockingRequest? = null
        private set

    fun schedule(
        request: RockingRequest,
        onConfirmed: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null,
    ) {
        pendingRequest = request
        if (onConfirmed != null) onPendingConfirmed = onConfirmed
        if (onCancelled != null) onPendingCancelled = onCancelled
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

    fun observe(notification: RockingNotification) {
        val pending = pendingRequest ?: return
        if (notification.error != null) {
            finishPendingUpdate(pending, confirmed = false)
            return
        }
        if (
            notification.intensity == pending.intensity &&
            notification.configuredSeconds == pending.durationSeconds &&
            notification.linkLossFlagSet == pending.linkLossFlagSet
        ) {
            onDiagnostic("Rocking adjustment confirmed")
            finishPendingUpdate(pending, confirmed = true)
        }
    }

    fun cancel() {
        updateJob?.cancel()
        updateJob = null
        val onCancelled = onPendingCancelled
        pendingRequest = null
        onPendingConfirmed = null
        onPendingCancelled = null
        onCancelled?.invoke()
    }

    private suspend fun waitForConfirmation(request: RockingRequest) {
        delay(UPDATE_CONFIRMATION_MILLIS)
        if (pendingRequest == request) {
            onError("Rocking adjustment was not confirmed by the stroller")
            finishPendingUpdate(request, confirmed = false)
        }
    }

    private fun handleWriteFailure(request: RockingRequest, error: Throwable) {
        if (error is CancellationException) throw error
        if (pendingRequest == request) {
            onError("Rocking adjustment failed: ${error.message}")
            finishPendingUpdate(request, confirmed = false)
        }
    }

    private fun finishPendingUpdate(request: RockingRequest, confirmed: Boolean) {
        if (pendingRequest != request) return
        val callback = if (confirmed) onPendingConfirmed else onPendingCancelled
        updateJob = null
        pendingRequest = null
        onPendingConfirmed = null
        onPendingCancelled = null
        callback?.invoke()
    }

    private companion object {
        const val UPDATE_DEBOUNCE_MILLIS = 250L
        const val UPDATE_CONFIRMATION_MILLIS = 3_000L
    }
}
