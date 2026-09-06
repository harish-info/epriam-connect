package dev.epriam.connect.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.DeviceCandidate
import dev.epriam.connect.domain.PriamRepository
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.ThemeMode
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.protocol.RockingIntensity

@Composable
fun PriamApp(
    repository: PriamRepository,
    onScan: () -> Unit,
    onStartRocking: () -> Unit,
) {
    val state by repository.state.collectAsState()
    PriamAppContent(
        state = state,
        actions = PriamActions(
            acceptSafety = repository::acceptSafety,
            scan = onScan,
            connect = repository::connect,
            disconnect = repository::disconnect,
            enterDemo = repository::enterDemo,
            exitDemo = repository::exitDemo,
            setDriveMode = repository::setDriveMode,
            setIntensity = repository::setIntensity,
            setDuration = repository::setDuration,
            setThemeMode = repository::setThemeMode,
            startRocking = onStartRocking,
            stopRocking = repository::stopRocking,
            acknowledgeStopped = repository::acknowledgeStopped,
        ),
    )
}

internal data class PriamActions(
    val acceptSafety: () -> Unit = {},
    val scan: () -> Unit = {},
    val connect: (DeviceCandidate) -> Unit = {},
    val disconnect: () -> Unit = {},
    val enterDemo: () -> Unit = {},
    val exitDemo: () -> Unit = {},
    val setDriveMode: (DriveMode) -> Unit = {},
    val setIntensity: (RockingIntensity) -> Unit = {},
    val setDuration: (Int) -> Unit = {},
    val setThemeMode: (ThemeMode) -> Unit = {},
    val startRocking: () -> Unit = {},
    val stopRocking: () -> Unit = {},
    val acknowledgeStopped: () -> Unit = {},
)

@Composable
internal fun PriamAppContent(state: PriamUiState, actions: PriamActions) {
    var showSettings by remember { mutableStateOf(false) }
    AutoScanAfterAcceptance(state = state, onScan = actions.scan)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        when {
            !state.safetyAccepted -> DisclaimerScreen(
                themeMode = state.themeMode,
                onAccept = actions.acceptSafety,
                modifier = Modifier.padding(contentPadding),
            )
            showSettings -> SettingsScreen(
                state = state,
                actions = actions,
                onBack = { showSettings = false },
                modifier = Modifier.padding(contentPadding),
            )
            else -> MainScreen(
                state = state,
                actions = actions,
                onSettings = { showSettings = true },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun AutoScanAfterAcceptance(state: PriamUiState, onScan: () -> Unit) {
    LaunchedEffect(state.safetyAccepted) {
        if (
            state.safetyAccepted &&
            state.connectionPhase == ConnectionPhase.IDLE &&
            state.candidates.isEmpty()
        ) {
            onScan()
        }
    }
}

@Composable
private fun MainScreen(
    state: PriamUiState,
    actions: PriamActions,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (state.isReady) {
            ControlDashboard(state = state, actions = actions, onSettings = onSettings)
        } else {
            ConnectionScreen(state = state, actions = actions, onSettings = onSettings)
        }
    }
}
