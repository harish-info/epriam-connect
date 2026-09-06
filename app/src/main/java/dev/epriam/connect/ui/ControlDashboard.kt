package dev.epriam.connect.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.protocol.DriveMode

@Composable
internal fun ControlDashboard(
    state: PriamUiState,
    actions: PriamActions,
    onSettings: () -> Unit,
) {
    var showDriveModes by remember { mutableStateOf(false) }

    BrandHeader(state = state, onSettings = onSettings, onExitDemo = actions.exitDemo)
    Spacer(Modifier.height(6.dp))
    RockingHero(state)
    Spacer(Modifier.height(24.dp))
    RockingContent(state = state, actions = actions)
    Spacer(Modifier.height(32.dp))
    DriveModeLauncher(state = state, onClick = { showDriveModes = true })
    Spacer(Modifier.height(16.dp))

    if (showDriveModes) {
        DriveModeSheet(
            state = state,
            onDismiss = { showDriveModes = false },
            onMode = { mode ->
                actions.setDriveMode(mode)
                if (mode != DriveMode.BOOST) showDriveModes = false
            },
        )
    }
}

@Composable
private fun RockingContent(state: PriamUiState, actions: PriamActions) {
    val unconfirmed = state.rockingState as? RockingState.Unconfirmed
    if (unconfirmed == null) {
        RockingSetup(state, actions)
    } else {
        WarningPanel(unconfirmed.message) {
            OutlinedButton(onClick = actions.stopRocking, shape = MaterialTheme.shapes.small) {
                Text("Send stop again")
            }
        }
    }
}
