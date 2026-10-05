package dev.epriam.connect.widget

import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.domain.ThemeMode
import dev.epriam.connect.domain.ThemePalette
import dev.epriam.connect.protocol.RockingIntensity
import org.junit.Assert.assertEquals
import org.junit.Test

class PriamWidgetPresentationTest {
    @Test
    fun firstRunOpensSetupButKeepsTimerPresetsAvailable() {
        val presentation = PriamUiState().toWidgetPresentation()

        assertEquals("Not connected", presentation.title)
        assertEquals("Open app to set up", presentation.detail)
        assertEquals("Set up app", presentation.actionLabel)
        assertEquals(null, presentation.batteryPercent)
        assertEquals(WidgetAction.START_ROCKING, presentation.action)
        assertEquals(false, presentation.primaryEnabled)
        assertEquals(30, presentation.selectedDurationMinutes)
        assertEquals(true, presentation.durationEnabled)
        assertEquals(ThemePalette.ROSE_GOLD, presentation.palette)
    }

    @Test
    fun configuredDisconnectedStateCanConnectAndStartFromWidget() {
        val presentation = PriamUiState(safetyAccepted = true).toWidgetPresentation()

        assertEquals("Tap to start", presentation.detail)
        assertEquals("Connect & start", presentation.actionLabel)
        assertEquals(true, presentation.primaryEnabled)
    }

    @Test
    fun pendingWidgetStartOffersCancelAndLocksDuration() {
        val presentation = PriamUiState(
            safetyAccepted = true,
            connectionPhase = ConnectionPhase.SCANNING,
            pendingWidgetStartDurationMinutes = 60,
        ).toWidgetPresentation()

        assertEquals("Connecting", presentation.title)
        assertEquals("Will rock 60 min", presentation.detail)
        assertEquals("Cancel start", presentation.actionLabel)
        assertEquals(WidgetAction.STOP_ROCKING, presentation.action)
        assertEquals(false, presentation.durationEnabled)
    }

    @Test
    fun connectedStateShowsStrollerAndBattery() {
        val presentation = PriamUiState(
            safetyAccepted = true,
            connectionPhase = ConnectionPhase.READY,
            connectedDeviceName = "e-Priam",
            batteryPercent = 74,
            themePalette = ThemePalette.ROSE_GOLD,
            themeMode = ThemeMode.DARK,
        ).toWidgetPresentation()

        assertEquals("e-Priam", presentation.title)
        assertEquals("Ready to rock", presentation.detail)
        assertEquals(74, presentation.batteryPercent)
        assertEquals(ThemePalette.ROSE_GOLD, presentation.palette)
        assertEquals(ThemeMode.DARK, presentation.themeMode)
        assertEquals(true, presentation.primaryEnabled)
    }

    @Test
    fun disconnectedStateDoesNotShowStaleBattery() {
        val presentation = PriamUiState(
            safetyAccepted = true,
            connectionPhase = ConnectionPhase.IDLE,
            batteryPercent = 74,
        ).toWidgetPresentation()

        assertEquals(null, presentation.batteryPercent)
    }

    @Test
    fun activeRockingExposesStopWithoutStartingMotion() {
        val presentation = PriamUiState(
            connectionPhase = ConnectionPhase.READY,
            rockingState = RockingState.Active(
                intensity = RockingIntensity.MEDIUM,
                remainingSeconds = 12 * 60 + 1,
                configuredSeconds = 30 * 60,
                linkLossFlagSet = true,
            ),
        ).toWidgetPresentation()

        assertEquals("Rocking", presentation.title)
        assertEquals("13 min remaining", presentation.detail)
        assertEquals("Stop", presentation.actionLabel)
        assertEquals(WidgetAction.STOP_ROCKING, presentation.action)
        assertEquals(true, presentation.durationEnabled)
    }

    @Test
    fun lostConnectionKeepsStopAvailableWhenMotionMayContinue() {
        val presentation = PriamUiState(
            connectionPhase = ConnectionPhase.RECONNECTING,
            rockingState = RockingState.Active(
                intensity = RockingIntensity.LOW,
                remainingSeconds = 59,
                configuredSeconds = 30 * 60,
                linkLossFlagSet = true,
            ),
        ).toWidgetPresentation()

        assertEquals("Link lost", presentation.title)
        assertEquals("1 min remaining", presentation.detail)
        assertEquals(WidgetAction.STOP_ROCKING, presentation.action)
    }

    @Test
    fun unconfirmedMotionKeepsStopAvailable() {
        val presentation = PriamUiState(
            rockingState = RockingState.Unconfirmed("No confirmation"),
        ).toWidgetPresentation()

        assertEquals("Check stroller", presentation.title)
        assertEquals(WidgetAction.STOP_ROCKING, presentation.action)
        assertEquals(false, presentation.durationEnabled)
    }
}
