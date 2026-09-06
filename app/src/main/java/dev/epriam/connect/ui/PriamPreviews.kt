package dev.epriam.connect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.DriveState
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.theme.EPriamConnectTheme

@Preview(showBackground = true)
@Composable
private fun DisclaimerPreview() {
    EPriamConnectTheme(darkTheme = true) {
        PriamAppContent(PriamUiState(), PriamActions())
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    EPriamConnectTheme(darkTheme = true) {
        PriamAppContent(
            PriamUiState(
                safetyAccepted = true,
                connectionPhase = ConnectionPhase.DEMO,
                statusMessage = "Connected · controls ready",
                connectedDeviceName = "Demo e-Priam",
                batteryPercent = 74,
                isDemo = true,
                driveState = DriveState.Observed(DriveMode.TOUR),
            ),
            PriamActions(),
        )
    }
}
