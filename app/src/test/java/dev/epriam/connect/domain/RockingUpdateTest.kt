package dev.epriam.connect.domain

import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.protocol.RockingRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class RockingUpdateTest {
    private val active = RockingState.Active(
        intensity = RockingIntensity.MEDIUM,
        remainingSeconds = 777,
        configuredSeconds = 1_800,
        linkLossFlagSet = true,
    )

    @Test
    fun `intensity adjustment preserves remaining time and disconnect policy`() {
        assertEquals(
            RockingRequest(RockingIntensity.HIGH, 777, linkLossFlagSet = true),
            active.toUpdateRequest(intensity = RockingIntensity.HIGH),
        )
    }

    @Test
    fun `duration adjustment replaces timer and preserves disconnect policy`() {
        assertEquals(
            RockingRequest(RockingIntensity.MEDIUM, 3_600, linkLossFlagSet = true),
            active.toUpdateRequest(durationSeconds = 3_600),
        )
    }
}
