package dev.epriam.connect.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RockingSessionLimitsTest {
    @Test
    fun appDurationPresetsMatchSupportedQuickChoices() {
        assertEquals(listOf(10, 30, 60, 90, 120), RockingSessionLimits.durationPresetsMinutes)
    }
}
