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
    val batteryPercent: Int?,
    val palette: ThemePalette,
    val themeMode: ThemeMode,
)

internal fun PriamUiState.toWidgetPresentation(): PriamWidgetPresentation {
    val rocking = rockingState
    val motionDetail = when (rocking) {
        is RockingState.Active -> formatWidgetRemaining(rocking.remainingSeconds)
        is RockingState.Starting, is RockingState.Stopping -> "Please wait"
        is RockingState.Unconfirmed -> "Status uncertain"
        else -> null
    }

    if (motionMayBeActive) {
        val title = when (rocking) {
            is RockingState.Active -> if (connectionPhase == ConnectionPhase.RECONNECTING) "Link lost" else "Rocking"
            is RockingState.Starting -> "Starting"
            is RockingState.Stopping -> "Stopping"
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
            batteryPercent = batteryPercent.takeIf { isReady },
            palette = themePalette,
            themeMode = themeMode,
        )
    }

    pendingWidgetStartDurationMinutes?.let { minutes ->
        return PriamWidgetPresentation(
            title = "Connecting",
            detail = "Will rock $minutes min",
            actionLabel = "Cancel start",
            action = WidgetAction.STOP_ROCKING,
            primaryEnabled = true,
            selectedDurationMinutes = minutes,
            durationEnabled = false,
            batteryPercent = null,
            palette = themePalette,
            themeMode = themeMode,
        )
    }

    val title = when (connectionPhase) {
        ConnectionPhase.READY, ConnectionPhase.DEMO -> connectedDeviceName ?: "Connected"
        ConnectionPhase.SCANNING -> "Scanning"
        ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING -> "Connecting"
        ConnectionPhase.RECONNECTING -> "Reconnecting"
        ConnectionPhase.ERROR -> "Can't connect"
        ConnectionPhase.IDLE -> "Not connected"
    }
    val detail = when (connectionPhase) {
        ConnectionPhase.READY, ConnectionPhase.DEMO -> "Ready to rock"
        ConnectionPhase.SCANNING -> "Stroller nearby?"
        ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING, ConnectionPhase.RECONNECTING -> "Stroller nearby?"
        ConnectionPhase.ERROR -> "Tap to retry"
        ConnectionPhase.IDLE -> if (safetyAccepted) "Tap to start" else "Open app to set up"
    }
    return PriamWidgetPresentation(
        title = title,
        detail = detail,
        actionLabel = if (!safetyAccepted) "Set up app"
        else if (isReady) "Start rocking" else "Connect & start",
        action = WidgetAction.START_ROCKING,
        primaryEnabled = safetyAccepted,
        selectedDurationMinutes = selectedDurationMinutes,
        durationEnabled = true,
        batteryPercent = batteryPercent.takeIf { isReady },
        palette = themePalette,
        themeMode = themeMode,
    )
}

internal fun formatWidgetRemaining(seconds: Int): String {
    val minutes = (seconds.coerceAtLeast(1) + 59) / 60
    return if (minutes == 1) "1 min remaining" else "$minutes min remaining"
}
