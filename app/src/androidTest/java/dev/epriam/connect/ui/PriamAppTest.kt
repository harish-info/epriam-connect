package dev.epriam.connect.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.DeviceCandidate
import dev.epriam.connect.domain.DriveState
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.protocol.RockingIntensity
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
    fun acceptedLaunchStartsScanningAutomatically() {
        var scanRequested = false
        composeRule.setContent {
            EPriamConnectTheme {
                PriamAppContent(
                    PriamUiState(safetyAccepted = true),
                    PriamActions(scan = { scanRequested = true }),
                )
            }
        }

        composeRule.runOnIdle { assert(scanRequested) }
    }

    @Test
    fun launchDoesNotScanBeforeTermsAreAccepted() {
        var scanRequested = false
        composeRule.setContent {
            EPriamConnectTheme {
                PriamAppContent(
                    PriamUiState(safetyAccepted = false),
                    PriamActions(scan = { scanRequested = true }),
                )
            }
        }

        composeRule.runOnIdle { assert(!scanRequested) }
    }

    @Test
    fun selectedStrollerShowsConnectionProgress() {
        val candidate = DeviceCandidate("id", "e-Priam", -55, "Nearby device")
        composeRule.setContent {
            EPriamConnectTheme {
                PriamAppContent(
                    PriamUiState(
                        safetyAccepted = true,
                        connectionPhase = ConnectionPhase.CONNECTING,
                        statusMessage = "Connecting to e-Priam…",
                        candidates = listOf(candidate),
                        connectedDeviceName = candidate.name,
                    ),
                    PriamActions(),
                )
            }
        }

        composeRule.onNodeWithText("Available strollers").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Connecting to stroller").assertIsDisplayed()
    }

    @Test
    fun boostIsVisibleWithoutExpertMode() {
        composeRule.setContent {
            var state by remember { mutableStateOf(demoState()) }
            EPriamConnectTheme {
                PriamAppContent(
                    state,
                    PriamActions(
                        setDriveMode = { mode -> state = state.copy(driveState = DriveState.Observed(mode)) },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Drive assistance").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Boost").assertIsDisplayed()
        composeRule.onNodeWithText("Eco").assertIsDisplayed()
        composeRule.onNodeWithText("Tour").assertIsDisplayed()
        composeRule.onNodeWithText("Experimental mode").assertIsNotDisplayed()
        composeRule.onNodeWithText("Boost").performClick()
        composeRule.onNodeWithText("Experimental mode").assertIsDisplayed()
        composeRule.onNodeWithText("not exposed by the official Cybex app", substring = true).assertIsDisplayed()
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
    fun demoModeCanReturnToStrollerSelection() {
        composeRule.setContent {
            var state by remember { mutableStateOf(demoState()) }
            EPriamConnectTheme {
                PriamAppContent(
                    state,
                    PriamActions(
                        exitDemo = { state = PriamUiState(safetyAccepted = true) },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Exit preview").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Scan for e-Priam").assertIsDisplayed()
    }

    @Test
    fun activeStopActionIsVisibleWithoutScrollingOnCompactScreen() {
        composeRule.setContent {
            EPriamConnectTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PriamAppContent(
                        demoState().copy(
                            rockingState = RockingState.Active(
                                intensity = RockingIntensity.MEDIUM,
                                remainingSeconds = 29 * 60 + 59,
                                configuredSeconds = 30 * 60,
                                linkLossFlagSet = true,
                            ),
                        ),
                        PriamActions(),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Stop Rocking").assertIsDisplayed()
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
        composeRule.onNodeWithText("No events yet").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("Expand protocol log").performClick()
        composeRule.onNodeWithText("No events yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Collapse protocol log").assertIsDisplayed()
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
