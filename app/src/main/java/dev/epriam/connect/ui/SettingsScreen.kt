package dev.epriam.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.epriam.connect.BuildConfig
import dev.epriam.connect.R
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.ThemeMode
import dev.epriam.connect.domain.ThemePalette
import dev.epriam.connect.theme.previewColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: PriamUiState,
    actions: PriamActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var protocolLogExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Appearance", "Choose a theme or follow your phone.")
            Spacer(Modifier.height(14.dp))
            ThemeSelector(selected = state.themeMode, onSelected = actions.setThemeMode)
            Spacer(Modifier.height(20.dp))
            Text("Color palette", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            PaletteSelector(selected = state.themePalette, onSelected = actions.setThemePalette)

            HorizontalDivider(modifier = Modifier.padding(vertical = 30.dp))
            SectionTitle(
                "Rocking connection",
                "Choose what the stroller should do if this phone disconnects.",
            )
            Spacer(Modifier.height(14.dp))
            ContinueRockingSetting(
                enabled = state.continueRockingWhenDisconnected,
                pendingValue = state.pendingContinueRockingWhenDisconnected,
                onEnabledChange = actions.setContinueRockingWhenDisconnected,
            )

            if (state.isReady) {
                Spacer(Modifier.height(24.dp))
                DisconnectButton(state = state, onDisconnect = actions.disconnect)
            }

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

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ContinueRockingSetting(
    enabled: Boolean,
    pendingValue: Boolean?,
    onEnabledChange: (Boolean) -> Unit,
) {
    val updateInProgress = pendingValue != null
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .toggleable(
                    value = enabled,
                    enabled = !updateInProgress,
                    onValueChange = onEnabledChange,
                )
                .semantics { contentDescription = "Continue rocking when disconnected" }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Continue if phone disconnects", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            updateInProgress -> "Updating the active rocking session…"
                            enabled -> "Requests rocking to continue for the configured timer."
                            else -> "Requests stop on Bluetooth link loss."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = !updateInProgress,
                    onCheckedChange = null,
                )
            }
            if (enabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "The stroller may keep moving when the app cannot send Stop. Keep it supervised.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
private fun PaletteSelector(selected: ThemePalette, onSelected: (ThemePalette) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ThemePalette.entries.forEachIndexed { index, palette ->
            SegmentedButton(
                selected = selected == palette,
                onClick = { onSelected(palette) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemePalette.entries.size),
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(
                            Modifier
                                .size(10.dp)
                                .background(palette.previewColor(), CircleShape),
                        )
                        Text(palette.displayName)
                    }
                },
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
        Icon(
            painter = painterResource(R.drawable.ic_bluetooth_disconnected),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (state.motionMayBeActive) "Stop rocking before disconnecting" else "Disconnect stroller")
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}
