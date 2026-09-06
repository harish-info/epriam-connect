package dev.epriam.connect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.epriam.connect.BuildConfig
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.DeviceCandidate
import dev.epriam.connect.domain.DriveState
import dev.epriam.connect.domain.PriamRepository
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.protocol.DOCUMENTED_MAX_DURATION_SECONDS
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.theme.EPriamConnectTheme

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
            setDriveMode = repository::setDriveMode,
            setIntensity = repository::setIntensity,
            setDuration = repository::setDuration,
            setExpertMode = repository::setExpertMode,
            setProtocolLabEnabled = repository::setProtocolLabEnabled,
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
    val setDriveMode: (DriveMode) -> Unit = {},
    val setIntensity: (RockingIntensity) -> Unit = {},
    val setDuration: (Int) -> Unit = {},
    val setExpertMode: (Boolean) -> Unit = {},
    val setProtocolLabEnabled: (Boolean) -> Unit = {},
    val startRocking: () -> Unit = {},
    val stopRocking: () -> Unit = {},
    val acknowledgeStopped: () -> Unit = {},
)

@Composable
internal fun PriamAppContent(state: PriamUiState, actions: PriamActions) {
    Scaffold { contentPadding ->
        if (!state.safetyAccepted) {
            SafetyScreen(
                onAccept = actions.acceptSafety,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(state)
                if (!state.isReady) ConnectionCard(state, actions)
                else Dashboard(state, actions)
                Diagnostics(state)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SafetyScreen(onAccept: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Before you connect", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "This is an independent, experimental controller. It is not affiliated with Cybex.",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        SafetyLine("Engage the parking brake and lock both front swivel wheels.")
        SafetyLine("Use rocking only on firm, level ground with a clear area around the stroller.")
        SafetyLine("Stay beside the stroller and monitor the child for the entire session.")
        SafetyLine("Begin at Low intensity. Never exceed the stroller’s 22 kg load limit.")
        SafetyLine("Extended timers and Boost are undocumented and used at your own risk.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("I understand — continue")
        }
    }
}

@Composable
private fun SafetyLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 7.dp)) {
        Text("•", fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
}

@Composable
private fun Header(state: PriamUiState) {
    Column {
        Text("ePriam Connect", style = MaterialTheme.typography.headlineMedium)
        Text(
            state.statusMessage,
            color = if (state.connectionPhase == ConnectionPhase.ERROR) {
                MaterialTheme.colorScheme.error
            } else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionCard(state: PriamUiState, actions: PriamActions) {
    SectionCard("Connect") {
        if (state.rockingState is RockingState.Unconfirmed) {
            Text(state.rockingState.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = actions.acknowledgeStopped, modifier = Modifier.fillMaxWidth()) {
                Text("I verified the stroller stopped")
            }
            HorizontalDivider()
        }
        Button(
            onClick = actions.scan,
            enabled = state.connectionPhase !in setOf(ConnectionPhase.SCANNING, ConnectionPhase.CONNECTING),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.connectionPhase == ConnectionPhase.SCANNING) "Scanning…" else "Find my stroller")
        }
        state.candidates.forEach { candidate ->
            OutlinedButton(
                onClick = { actions.connect(candidate) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(candidate.name, fontWeight = FontWeight.SemiBold)
                    Text("Signal ${candidate.rssi} dBm · …${candidate.addressHint}")
                }
            }
        }
        if (BuildConfig.DEBUG) {
            TextButton(
                onClick = actions.enterDemo,
                enabled = state.connectionPhase !in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Open demo without a stroller")
            }
        }
    }
}

@Composable
private fun Dashboard(state: PriamUiState, actions: PriamActions) {
    ConnectionSummary(state, actions.disconnect)
    DriveModeCard(state, actions)
    RockingCard(state, actions)
    ExpertCard(state, actions)
}

@Composable
private fun ConnectionSummary(state: PriamUiState, disconnect: () -> Unit) {
    SectionCard(if (state.isDemo) "Demo connection" else "Stroller") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.connectedDeviceName ?: "e-Priam", fontWeight = FontWeight.SemiBold)
                Text("Connected", color = MaterialTheme.colorScheme.primary)
            }
            state.batteryPercent?.let {
                Text("$it%", style = MaterialTheme.typography.titleLarge)
            }
        }
        TextButton(
            onClick = disconnect,
            enabled = !state.motionMayBeActive,
            modifier = Modifier.align(Alignment.End),
        ) { Text(if (state.motionMayBeActive) "Stop rocking first" else "Disconnect") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveModeCard(state: PriamUiState, actions: PriamActions) {
    val selected = when (val drive = state.driveState) {
        is DriveState.Commanded -> drive.mode
        is DriveState.Observed -> drive.mode
        DriveState.Unknown -> null
    }
    SectionCard("Drive assistance") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriveMode.entries.filter { !it.experimental || state.expertMode }.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { actions.setDriveMode(mode) },
                    label = { Text(if (mode.experimental) "Boost · experimental" else mode.displayName) },
                )
            }
        }
        if (selected == null) Text("Waiting for the stroller’s current mode")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RockingCard(state: PriamUiState, actions: PriamActions) {
    var confirmExtended by remember { mutableStateOf(false) }
    SectionCard("Rocking") {
        when (val rocking = state.rockingState) {
            is RockingState.Active -> {
                Text(formatDuration(rocking.remainingSeconds), style = MaterialTheme.typography.displayMedium)
                Text("${rocking.intensity.displayName} · ${formatDuration(rocking.configuredSeconds)} session")
                Button(onClick = actions.stopRocking, modifier = Modifier.fillMaxWidth()) { Text("Stop rocking") }
            }
            is RockingState.Starting -> Text("Sending rocking command…")
            is RockingState.Stopping -> Text("Sending stop command…")
            is RockingState.Rejected -> Text(rocking.message, color = MaterialTheme.colorScheme.error)
            is RockingState.Unconfirmed -> {
                Text(rocking.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = actions.stopRocking, modifier = Modifier.fillMaxWidth()) {
                    Text("Send stop again")
                }
            }
            RockingState.Off -> {
                Text("Intensity", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RockingIntensity.entries.reversed().forEach { intensity ->
                        FilterChip(
                            selected = state.selectedIntensity == intensity,
                            onClick = { actions.setIntensity(intensity) },
                            label = { Text(intensity.displayName) },
                        )
                    }
                }
                Text("Duration", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60, 120, 180).forEach { minutes ->
                        FilterChip(
                            selected = state.selectedDurationMinutes == minutes,
                            onClick = { actions.setDuration(minutes) },
                            label = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} hr") },
                        )
                    }
                }
                if (state.selectedDurationMinutes * 60 > DOCUMENTED_MAX_DURATION_SECONDS) {
                    Text(
                        "Cybex documents a 30-minute maximum. Extended sessions are experimental and require active supervision.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        if (state.selectedDurationMinutes * 60 > DOCUMENTED_MAX_DURATION_SECONDS) {
                            confirmExtended = true
                        } else actions.startRocking()
                    },
                    enabled = state.isDemo || state.protocolLabEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start rocking") }
                if (!state.isDemo && !state.protocolLabEnabled) {
                    Text("Enable Protocol Lab below before sending unverified motor commands.")
                }
            }
        }
    }
    if (confirmExtended) {
        AlertDialog(
            onDismissRequest = { confirmExtended = false },
            title = { Text("Start an extended session?") },
            text = { Text("Stay beside the stroller. You can stop at any time from the app or notification.") },
            confirmButton = {
                Button(onClick = {
                    confirmExtended = false
                    actions.startRocking()
                }) { Text("Start ${state.selectedDurationMinutes} minutes") }
            },
            dismissButton = { TextButton(onClick = { confirmExtended = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExpertCard(state: PriamUiState, actions: PriamActions) {
    SectionCard("Expert controls") {
        SettingRow(
            title = "Expert mode",
            description = "Shows undocumented Boost drive assistance.",
            checked = state.expertMode,
            onCheckedChange = actions.setExpertMode,
        )
        if (BuildConfig.ENABLE_PROTOCOL_LAB && !state.isDemo) {
            HorizontalDivider()
            SettingRow(
                title = "Protocol Lab",
                description = "Allows direct motor writes. Packet meanings still require validation on real hardware.",
                checked = state.protocolLabEnabled,
                onCheckedChange = actions.setProtocolLabEnabled,
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Diagnostics(state: PriamUiState) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard("Diagnostics") {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide protocol log" else "Show protocol log (${state.diagnostics.size})")
        }
        if (expanded) {
            if (state.diagnostics.isEmpty()) Text("No events yet")
            state.diagnostics.take(20).forEach { event ->
                Text(event.message, style = MaterialTheme.typography.bodySmall)
            }
            state.batteryRawValue?.let {
                Text("Battery estimate source: raw $it", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun formatDuration(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

@Preview(showBackground = true)
@Composable
private fun SafetyPreview() {
    EPriamConnectTheme { PriamAppContent(PriamUiState(), PriamActions()) }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    EPriamConnectTheme {
        PriamAppContent(
            PriamUiState(
                safetyAccepted = true,
                connectionPhase = ConnectionPhase.DEMO,
                statusMessage = "Demo stroller connected",
                connectedDeviceName = "Demo e-Priam",
                batteryPercent = 74,
                isDemo = true,
                driveState = DriveState.Observed(DriveMode.TOUR),
            ),
            PriamActions(),
        )
    }
}
