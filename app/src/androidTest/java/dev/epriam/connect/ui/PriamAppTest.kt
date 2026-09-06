package dev.epriam.connect.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.DriveState
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.theme.EPriamConnectTheme
import org.junit.Rule
import org.junit.Test

class PriamAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disclaimerMakesResponsibilityExplicit() {
        composeRule.setContent {
            EPriamConnectTheme { PriamAppContent(PriamUiState(), PriamActions()) }
        }

        composeRule.onNodeWithText("e-Priam Companion").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Accept responsibility").performClick()
        composeRule.onNodeWithText("Get started").assertIsEnabled()
    }

    @Test
    fun connectionGuideWarnsAboutOfficialAppCompetition() {
        composeRule.setContent {
            EPriamConnectTheme { PriamAppContent(PriamUiState(safetyAccepted = true), PriamActions()) }
        }

        composeRule.onNodeWithText(
            "In the official Cybex app, disconnect the stroller — or close the app completely",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Scan for e-Priam").assertIsDisplayed()
    }

    @Test
    fun boostIsVisibleWithoutExpertMode() {
        composeRule.setContent {
            EPriamConnectTheme { PriamAppContent(demoState(), PriamActions()) }
        }

        composeRule.onNodeWithText("Drive assistance").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Boost").assertIsDisplayed()
        composeRule.onNodeWithText("Eco").assertIsDisplayed()
        composeRule.onNodeWithText("Tour").assertIsDisplayed()
    }

    @Test
    fun extendedDurationShowsInlineWarningWithoutConfirmation() {
        var started = false
        composeRule.setContent {
            var state by remember { mutableStateOf(demoState()) }
            EPriamConnectTheme {
                PriamAppContent(
                    state,
                    PriamActions(
                        setDuration = { state = state.copy(selectedDurationMinutes = it) },
                        startRocking = { started = true },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("60").performScrollTo().performClick()
        composeRule.onNodeWithText("Beyond Cybex’s 30-minute limit · stay nearby")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Start Rocking").performScrollTo().performClick()
        composeRule.runOnIdle { assert(started) }
        composeRule.onNodeWithText("Start an extended session?").assertIsNotDisplayed()
    }

    @Test
    fun settingsKeepsDeveloperDetailsAwayFromMainControls() {
        composeRule.setContent {
            EPriamConnectTheme { PriamAppContent(demoState(), PriamActions()) }
        }

        composeRule.onNodeWithText("PROTOCOL LOG", substring = true).assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("PROTOCOL LOG", substring = true).assertIsDisplayed()
    }

    private fun demoState() = PriamUiState(
        safetyAccepted = true,
        connectionPhase = ConnectionPhase.DEMO,
        statusMessage = "Connected · controls ready",
        connectedDeviceName = "Demo e-Priam",
        batteryPercent = 74,
        isDemo = true,
        driveState = DriveState.Observed(DriveMode.TOUR),
    )
}
