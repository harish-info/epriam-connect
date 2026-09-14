package dev.epriam.connect.domain

import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.protocol.RockingProtocolError

enum class ConnectionPhase {
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    RECONNECTING,
    READY,
    DEMO,
    ERROR,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ThemePalette(val displayName: String) {
    MINT("Mint"),
    ROSE_GOLD("Rose gold"),
    OCEAN("Ocean"),
}

data class DeviceCandidate(
    val id: String,
    val name: String,
    val rssi: Int,
    val addressHint: String,
    val identityKey: String = id,
    val lastSeenElapsedRealtimeMillis: Long = 0L,
)

internal fun List<DeviceCandidate>.updatedWith(candidate: DeviceCandidate): List<DeviceCandidate> =
    (filterNot { it.identityKey == candidate.identityKey } + candidate)
        .sortedByDescending(DeviceCandidate::rssi)

internal fun DeviceCandidate.isFresh(
    nowElapsedRealtimeMillis: Long,
    maximumAgeMillis: Long,
): Boolean = nowElapsedRealtimeMillis - lastSeenElapsedRealtimeMillis in 0..maximumAgeMillis

sealed interface DriveState {
    data object Unknown : DriveState
    data class Applying(val mode: DriveMode) : DriveState
    data class Commanded(val mode: DriveMode) : DriveState
    data class Observed(val mode: DriveMode) : DriveState
}

sealed interface RockingState {
    data object Off : RockingState
    data class Starting(val intensity: RockingIntensity, val configuredSeconds: Int) : RockingState
    data class Active(
        val intensity: RockingIntensity,
        val remainingSeconds: Int,
        val configuredSeconds: Int,
        val linkLossFlagSet: Boolean,
    ) : RockingState
    data object Stopping : RockingState
    data class Rejected(val error: RockingProtocolError, val message: String) : RockingState
    data class Unconfirmed(val message: String) : RockingState
}

data class DiagnosticEvent(
    val timestampMillis: Long,
    val message: String,
)

data class PriamUiState(
    val safetyAccepted: Boolean = false,
    val connectionPhase: ConnectionPhase = ConnectionPhase.IDLE,
    val statusMessage: String = "Not connected",
    val candidates: List<DeviceCandidate> = emptyList(),
    val connectedDeviceName: String? = null,
    val batteryPercent: Int? = null,
    val batteryRawValue: Int? = null,
    val batteryLeds: Int? = null,
    val driveState: DriveState = DriveState.Unknown,
    val rockingState: RockingState = RockingState.Off,
    val selectedIntensity: RockingIntensity = RockingIntensity.MEDIUM,
    val selectedDurationMinutes: Int = 30,
    val continueRockingWhenDisconnected: Boolean = false,
    val pendingContinueRockingWhenDisconnected: Boolean? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: ThemePalette = ThemePalette.ROSE_GOLD,
    val canReconnect: Boolean = false,
    val isDemo: Boolean = false,
    val diagnostics: List<DiagnosticEvent> = emptyList(),
) {
    val isReady: Boolean = connectionPhase == ConnectionPhase.READY || connectionPhase == ConnectionPhase.DEMO
    val isRocking: Boolean = rockingState is RockingState.Starting || rockingState is RockingState.Active
    val motionMayBeActive: Boolean = rockingState is RockingState.Starting ||
        rockingState is RockingState.Active ||
        rockingState is RockingState.Stopping ||
        rockingState is RockingState.Unconfirmed
}
