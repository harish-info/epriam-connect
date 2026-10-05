package dev.epriam.connect.service

import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.PriamUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RockingSessionServiceTest {
    @Test
    fun `notification stops all Bluetooth work only while reconnecting`() {
        assertTrue(
            shouldStopEverything(
                PriamUiState(
                    connectionPhase = ConnectionPhase.RECONNECTING,
                    reconnectSecondsRemaining = 120,
                ),
            ),
        )
        assertTrue(
            shouldStopEverything(
                PriamUiState(
                    connectionPhase = ConnectionPhase.DISCOVERING,
                    reconnectSecondsRemaining = 90,
                ),
            ),
        )
        assertFalse(
            shouldStopEverything(
                PriamUiState(connectionPhase = ConnectionPhase.READY),
            ),
        )
    }
}
