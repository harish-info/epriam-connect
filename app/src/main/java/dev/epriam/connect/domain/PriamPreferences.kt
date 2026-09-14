package dev.epriam.connect.domain

import android.content.Context
import androidx.core.content.edit
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
            .coerceIn(
                RockingSessionLimits.MIN_DURATION_MINUTES,
                RockingSessionLimits.MAX_DURATION_MINUTES,
            ),
        continueRockingWhenDisconnected = preferences.getBoolean(
            KEY_CONTINUE_ROCKING_WHEN_DISCONNECTED,
            false,
        ),
        themeMode = preferences.getString(KEY_THEME_MODE, null)
            ?.let(::parseThemeMode)
            ?: ThemeMode.SYSTEM,
        themePalette = preferences.getString(KEY_THEME_PALETTE, null)
            ?.let(::parseThemePalette)
            ?: ThemePalette.MINT,
    )

    fun acceptSafetyDisclaimer() {
        preferences.edit { putInt(KEY_DISCLAIMER_VERSION, CURRENT_DISCLAIMER_VERSION) }
    }

    fun saveIntensity(intensity: RockingIntensity) {
        preferences.edit { putInt(KEY_INTENSITY, intensity.wireValue) }
    }

    fun saveDurationMinutes(minutes: Int) {
        preferences.edit { putInt(KEY_DURATION_MINUTES, minutes) }
    }

    fun saveContinueRockingWhenDisconnected(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_CONTINUE_ROCKING_WHEN_DISCONNECTED, enabled) }
    }

    fun saveThemeMode(themeMode: ThemeMode) {
        preferences.edit { putString(KEY_THEME_MODE, themeMode.name) }
    }

    fun saveThemePalette(themePalette: ThemePalette) {
        preferences.edit { putString(KEY_THEME_PALETTE, themePalette.name) }
    }

    private fun parseThemeMode(value: String): ThemeMode? =
        runCatching { ThemeMode.valueOf(value) }.getOrNull()

    private fun parseThemePalette(value: String): ThemePalette? =
        runCatching { ThemePalette.valueOf(value) }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "priam"
        const val KEY_DISCLAIMER_VERSION = "disclaimer_version"
        const val KEY_INTENSITY = "rocking_intensity"
        const val KEY_DURATION_MINUTES = "rocking_duration_minutes"
        const val KEY_CONTINUE_ROCKING_WHEN_DISCONNECTED = "continue_rocking_when_disconnected"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_PALETTE = "theme_palette"
        const val CURRENT_DISCLAIMER_VERSION = 2
        const val DEFAULT_DURATION_MINUTES = 30
    }
}
