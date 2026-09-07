package dev.epriam.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.epriam.connect.BuildConfig
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.ThemeMode

@Composable
internal fun SettingsScreen(
    state: PriamUiState,
    actions: PriamActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var protocolLogExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        SettingsHeader(onBack)
        Spacer(Modifier.height(38.dp))
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(30.dp))
        SectionTitle("Appearance", "Choose a theme or follow your phone.")
        Spacer(Modifier.height(14.dp))
        ThemeSelector(selected = state.themeMode, onSelected = actions.setThemeMode)

        if (BuildConfig.DEBUG) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 30.dp))
            SectionTitle("Developer", "Raw Bluetooth information for troubleshooting.")
            Spacer(Modifier.height(14.dp))
            ProtocolLog(
                state = state,
                expanded = protocolLogExpanded,
                onExpandedChange = { protocolLogExpanded = it },
            )
        }

        if (state.isReady) {
            Spacer(Modifier.height(30.dp))
            DisconnectButton(state = state, onDisconnect = actions.disconnect)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← BACK") }
        Spacer(Modifier.weight(1f))
        BrandMark()
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                label = { Text(mode.name) },
            )
        }
    }
}

@Composable
private fun ProtocolLog(
    state: PriamUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onExpandedChange(!expanded) }
                    .semantics {
                        contentDescription = if (expanded) "Collapse protocol log" else "Expand protocol log"
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "PROTOCOL LOG · ${state.diagnostics.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    if (expanded) "Hide" else "Show",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            AnimatedVisibility(visible = expanded) {
                ProtocolLogEntries(state)
            }
        }
    }
}

@Composable
private fun ProtocolLogEntries(state: PriamUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        if (state.diagnostics.isEmpty()) {
            Text("No events yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.diagnostics.take(30).forEach { event ->
            Text(
                event.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.batteryRawValue?.let { rawValue ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Battery raw value: $rawValue", style = MaterialTheme.typography.bodySmall)
        }
        state.batteryLeds?.let { ledCount ->
            Text("Battery LEDs: $ledCount", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DisconnectButton(state: PriamUiState, onDisconnect: () -> Unit) {
    OutlinedButton(
        onClick = onDisconnect,
        enabled = !state.motionMayBeActive,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(if (state.motionMayBeActive) "Stop rocking before disconnecting" else "Disconnect stroller")
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}
