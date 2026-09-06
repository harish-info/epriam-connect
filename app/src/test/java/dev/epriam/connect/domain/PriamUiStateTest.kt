package dev.epriam.connect.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriamUiStateTest {
    @Test
    fun unconfirmedMotionRemainsSafetyRelevant() {
        val state = PriamUiState(
            rockingState = RockingState.Unconfirmed("Stop was not confirmed"),
        )

        assertTrue(state.motionMayBeActive)
        assertFalse(state.isRocking)
    }

    @Test
    fun rejectedCommandDoesNotBlockDisconnect() {
        val state = PriamUiState(
            rockingState = RockingState.Rejected(
                error = dev.epriam.connect.protocol.RockingProtocolError.BrakeNotEngaged,
                message = "Brake not engaged",
            ),
        )

        assertFalse(state.motionMayBeActive)
    }
}
