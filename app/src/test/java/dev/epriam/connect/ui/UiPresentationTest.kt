package dev.epriam.connect.ui

import dev.epriam.connect.domain.DriveState
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.protocol.DriveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiPresentationTest {
    @Test
    fun `duration formatter keeps a two digit seconds field`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("29:05", formatDuration(29 * 60 + 5))
    }

    @Test
    fun `drive selection follows every known drive state`() {
        assertNull(PriamUiState().selectedDriveMode())
        assertEquals(
            DriveMode.ECO,
            PriamUiState(driveState = DriveState.Applying(DriveMode.ECO)).selectedDriveMode(),
        )
        assertEquals(
            DriveMode.TOUR,
            PriamUiState(driveState = DriveState.Commanded(DriveMode.TOUR)).selectedDriveMode(),
        )
        assertEquals(
            DriveMode.BOOST,
            PriamUiState(driveState = DriveState.Observed(DriveMode.BOOST)).selectedDriveMode(),
        )
    }

    @Test
    fun `signal labels cover connection quality boundaries`() {
        assertEquals("Strong signal · ready to connect", signalLabel(-65))
        assertEquals("Stroller nearby", signalLabel(-80))
        assertEquals("Move closer for a reliable connection", signalLabel(-81))
    }

    @Test
    fun `reconnect timer explains the bounded retry window`() {
        assertEquals("Automatic retries stop after 3 minutes.", reconnectTimeLabel(null))
        assertEquals("Automatic retries stop in 3:00.", reconnectTimeLabel(180))
        assertEquals("Automatic retries stop in 0:09.", reconnectTimeLabel(9))
    }
}
