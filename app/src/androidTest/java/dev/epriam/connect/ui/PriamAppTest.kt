package dev.epriam.connect.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    fun onboardingShowsIndependentControllerAndSafetyChecks() {
        composeRule.setContent {
            EPriamConnectTheme { PriamAppContent(PriamUiState(), PriamActions()) }
        }

        composeRule.onNodeWithText("Before you connect").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This is an independent, experimental controller. It is not affiliated with Cybex.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("I understand — continue").assertIsDisplayed()
    }

    @Test
    fun demoDashboardExposesExtendedDurations() {
        composeRule.setContent {
            EPriamConnectTheme { PriamAppContent(demoState(), PriamActions()) }
        }

        composeRule.onNodeWithText("Demo connection").assertIsDisplayed()
        composeRule.onNodeWithText("1 hr").assertIsDisplayed()
        composeRule.onNodeWithText("3 hr").assertIsDisplayed()
    }

    @Test
    fun extendedDurationRequiresConfirmation() {
        composeRule.setContent {
            var state by remember { mutableStateOf(demoState()) }
            EPriamConnectTheme {
                PriamAppContent(
                    state,
                    PriamActions(setDuration = { state = state.copy(selectedDurationMinutes = it) }),
                )
            }
        }

        composeRule.onNodeWithText("1 hr").performClick()
        composeRule.onNodeWithText("Start rocking").performScrollTo().performClick()
        composeRule.onNodeWithText("Start an extended session?").assertIsDisplayed()
        composeRule.onNodeWithText("Start 60 minutes").assertIsDisplayed()
    }

    private fun demoState() = PriamUiState(
        safetyAccepted = true,
        connectionPhase = ConnectionPhase.DEMO,
        statusMessage = "Demo stroller connected",
        connectedDeviceName = "Demo e-Priam",
        batteryPercent = 74,
        isDemo = true,
        driveState = DriveState.Observed(DriveMode.TOUR),
    )
}
