package dev.epriam.connect.domain

import android.content.Context
import dev.epriam.connect.protocol.RockingIntensity

internal class PriamPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadInitialState(): PriamUiState = PriamUiState(
        safetyAccepted = preferences.getInt(KEY_DISCLAIMER_VERSION, 0) >= CURRENT_DISCLAIMER_VERSION,
        selectedIntensity = RockingIntensity.fromWire(
            preferences.getInt(KEY_INTENSITY, RockingIntensity.MEDIUM.wireValue),
        ) ?: RockingIntensity.LOW,
        selectedDurationMinutes = preferences
            .getInt(KEY_DURATION_MINUTES, DEFAULT_DURATION_MINUTES)
            .coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES),
        themeMode = preferences.getString(KEY_THEME_MODE, null)
            ?.let(::parseThemeMode)
            ?: ThemeMode.SYSTEM,
    )

    fun acceptSafetyDisclaimer() {
        preferences.edit().putInt(KEY_DISCLAIMER_VERSION, CURRENT_DISCLAIMER_VERSION).apply()
    }

    fun saveIntensity(intensity: RockingIntensity) {
        preferences.edit().putInt(KEY_INTENSITY, intensity.wireValue).apply()
    }

    fun saveDurationMinutes(minutes: Int) {
        preferences.edit().putInt(KEY_DURATION_MINUTES, minutes).apply()
    }

    fun saveThemeMode(themeMode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
    }

    private fun parseThemeMode(value: String): ThemeMode? =
        runCatching { ThemeMode.valueOf(value) }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "priam"
        const val KEY_DISCLAIMER_VERSION = "disclaimer_version"
        const val KEY_INTENSITY = "rocking_intensity"
        const val KEY_DURATION_MINUTES = "rocking_duration_minutes"
        const val KEY_THEME_MODE = "theme_mode"
        const val CURRENT_DISCLAIMER_VERSION = 2
        const val DEFAULT_DURATION_MINUTES = 30
        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 180
    }
}
