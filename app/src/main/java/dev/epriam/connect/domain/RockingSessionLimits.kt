package dev.epriam.connect.domain

import dev.epriam.connect.protocol.APP_MAX_DURATION_SECONDS

internal object RockingSessionLimits {
    const val MIN_DURATION_MINUTES = 5
    const val MAX_DURATION_MINUTES = APP_MAX_DURATION_SECONDS / 60
    const val DURATION_STEP_MINUTES = 5
    val durationPresetsMinutes = listOf(30, 60, 90, 120, 180)
}
