package dev.epriam.connect.widget

import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.domain.ThemeMode
import dev.epriam.connect.domain.ThemePalette

internal enum class WidgetAction {
    START_ROCKING,
    STOP_ROCKING,
}

internal data class PriamWidgetPresentation(
    val title: String,
    val detail: String,
    val actionLabel: String,
    val action: WidgetAction,
    val primaryEnabled: Boolean,
    val selectedDurationMinutes: Int,
    val durationEnabled: Boolean,
    val palette: ThemePalette,
    val themeMode: ThemeMode,
)

internal fun PriamUiState.toWidgetPresentation(): PriamWidgetPresentation {
    val rocking = rockingState
    val motionDetail = when (rocking) {
        is RockingState.Active -> {
            val time = formatWidgetRemaining(rocking.remainingSeconds)
            if (connectionPhase == ConnectionPhase.RECONNECTING) "Connection lost · $time" else time
        }
        is RockingState.Starting -> "Preparing ${rocking.intensity.displayName.lowercase()} intensity"
        is RockingState.Stopping -> "Waiting for stroller confirmation"
        is RockingState.Unconfirmed -> "Status unconfirmed · check stroller"
        else -> null
    }

    if (motionMayBeActive) {
        val title = when (rocking) {
            is RockingState.Active -> "Rocking · ${rocking.intensity.displayName}"
            is RockingState.Starting -> "Starting rocking"
            is RockingState.Stopping -> "Stopping rocking"
            else -> "Check stroller"
        }
        return PriamWidgetPresentation(
            title = title,
            detail = requireNotNull(motionDetail),
            actionLabel = "Stop",
            action = WidgetAction.STOP_ROCKING,
            primaryEnabled = true,
            selectedDurationMinutes = selectedDurationMinutes,
            durationEnabled = rocking is RockingState.Active,
            palette = themePalette,
            themeMode = themeMode,
        )
    }

    val title = when (connectionPhase) {
        ConnectionPhase.READY, ConnectionPhase.DEMO -> connectedDeviceName ?: "Stroller connected"
        ConnectionPhase.SCANNING -> "Looking for stroller"
        ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING -> "Connecting"
        ConnectionPhase.RECONNECTING -> "Reconnecting"
        ConnectionPhase.ERROR -> "Connection needs attention"
        ConnectionPhase.IDLE -> "Not connected"
    }
    val detail = when (connectionPhase) {
        ConnectionPhase.READY, ConnectionPhase.DEMO -> batteryPercent?.let { "Battery $it%" } ?: "Ready to rock"
        ConnectionPhase.SCANNING -> "Keep the stroller nearby"
        ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING, ConnectionPhase.RECONNECTING -> "Keep the stroller nearby"
        ConnectionPhase.ERROR -> "Open the app to retry"
        ConnectionPhase.IDLE -> "Open the app to connect"
    }
    return PriamWidgetPresentation(
        title = title,
        detail = detail,
        actionLabel = "Start rocking",
        action = WidgetAction.START_ROCKING,
        primaryEnabled = isReady,
        selectedDurationMinutes = selectedDurationMinutes,
        durationEnabled = true,
        palette = themePalette,
        themeMode = themeMode,
    )
}

internal fun formatWidgetRemaining(seconds: Int): String {
    val minutes = (seconds.coerceAtLeast(1) + 59) / 60
    return if (minutes == 1) "1 min remaining" else "$minutes min remaining"
}
