package dev.epriam.connect.domain

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import dev.epriam.connect.protocol.RockingIntensity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PriamSettingsPersistenceTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("priam", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun selectedControlsAndThemeSurviveRepositoryRecreation() {
        PriamRepository(context).apply {
            acceptSafety()
            setDuration(120)
            setIntensity(RockingIntensity.HIGH)
            setThemeMode(ThemeMode.DARK)
        }

        val restored = PriamRepository(context).state.value
        assertEquals(true, restored.safetyAccepted)
        assertEquals(120, restored.selectedDurationMinutes)
        assertEquals(RockingIntensity.HIGH, restored.selectedIntensity)
        assertEquals(ThemeMode.DARK, restored.themeMode)
    }
}
