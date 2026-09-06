package dev.epriam.connect.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.epriam.connect.BuildConfig
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.DeviceCandidate
import dev.epriam.connect.domain.DriveState
import dev.epriam.connect.domain.PriamRepository
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.domain.ThemeMode
import dev.epriam.connect.protocol.DOCUMENTED_MAX_DURATION_SECONDS
import dev.epriam.connect.protocol.DriveMode
import dev.epriam.connect.protocol.RockingIntensity
import dev.epriam.connect.theme.EPriamConnectTheme
import kotlin.math.PI
import kotlin.math.sin

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
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        if (!state.safetyAccepted) {
            DisclaimerScreen(
                onAccept = actions.acceptSafety,
                modifier = Modifier.padding(contentPadding),
            )
        } else if (showSettings) {
            SettingsScreen(
                state = state,
                actions = actions,
                onBack = { showSettings = false },
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (state.isReady) ControlDashboard(state, actions) { showSettings = true }
                else ConnectionScreen(state, actions) { showSettings = true }
            }
        }
    }
}

@Composable
private fun DisclaimerScreen(onAccept: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            BrandMark()
            Spacer(Modifier.height(52.dp))
            Text("You are in control.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "ePriam Connect sends commands directly to your stroller. It is independent software and is not affiliated with Cybex.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            DisclaimerPoint("Boost and sessions over 30 minutes are outside the official app’s controls.")
            DisclaimerPoint("Use the parking brake, lock the front wheels and stay beside the stroller while rocking.")
            DisclaimerPoint("You accept responsibility for injury, damage, data loss or unexpected stroller behaviour.")
            Spacer(Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "The developer provides this app as-is and is not responsible for damage to the stroller or harm caused by its use.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Accept responsibility")
        }
    }
}

@Composable
private fun DisclaimerPoint(text: String) {
    Row(modifier = Modifier.padding(vertical = 9.dp)) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(7.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: PriamUiState,
    actions: PriamActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← BACK") }
            Spacer(Modifier.weight(1f))
            BrandMark()
        }
        Spacer(Modifier.height(38.dp))
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(30.dp))
        SectionTitle("Appearance", "Choose a theme or follow your phone.")
        Spacer(Modifier.height(14.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.themeMode == mode,
                    onClick = { actions.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    label = { Text(mode.name) },
                )
            }
        }
        if (BuildConfig.DEBUG) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 30.dp))
            SectionTitle("Developer", "Raw Bluetooth information for troubleshooting.")
            Spacer(Modifier.height(14.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("PROTOCOL LOG · ${state.diagnostics.size}", style = MaterialTheme.typography.labelLarge)
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
                    state.batteryRawValue?.let { raw ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Battery raw value: $raw", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            StrollerGlyph(Modifier.size(23.dp), MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Text("PRIAM / CONNECT", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StrollerGlyph(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.18f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.32f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.31f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.35f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.35f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.67f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(color, radius = size.minDimension * 0.12f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.79f))
        drawCircle(color, radius = size.minDimension * 0.12f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.79f))
    }
}

@Composable
private fun ConnectionScreen(state: PriamUiState, actions: PriamActions, onSettings: () -> Unit) {
    BrandHeader(state = state, onSettings = onSettings)
    Spacer(Modifier.height(36.dp))
    Text("Connect your stroller", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(10.dp))
    Text(
        "Keep the e-Priam powered on and nearby.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    ConnectionGuide(scanning = state.connectionPhase == ConnectionPhase.SCANNING)
    Spacer(Modifier.height(24.dp))
    if (state.rockingState is RockingState.Unconfirmed) {
        WarningPanel(state.rockingState.message) {
            Button(
                onClick = actions.acknowledgeStopped,
                shape = MaterialTheme.shapes.small,
            ) { Text("I verified it stopped") }
        }
        Spacer(Modifier.height(20.dp))
    }
    Button(
        onClick = actions.scan,
        enabled = state.connectionPhase !in setOf(ConnectionPhase.SCANNING, ConnectionPhase.CONNECTING),
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(if (state.connectionPhase == ConnectionPhase.SCANNING) "Scanning nearby…" else "Scan for e-Priam")
    }
    state.candidates.forEach { candidate ->
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { actions.connect(candidate) },
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.name, style = MaterialTheme.typography.titleLarge)
                    Text(signalLabel(candidate.rssi), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("CONNECT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (BuildConfig.DEBUG) {
        TextButton(
            onClick = actions.enterDemo,
            enabled = state.connectionPhase !in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text("Preview controls without stroller") }
    }
}

@Composable
private fun ControlDashboard(state: PriamUiState, actions: PriamActions, onSettings: () -> Unit) {
    var showDriveModes by remember { mutableStateOf(false) }
    BrandHeader(state, actions.disconnect, onSettings)
    Spacer(Modifier.height(26.dp))
    RockingHero(state, actions)
    Spacer(Modifier.height(30.dp))
    if (state.rockingState is RockingState.Off || state.rockingState is RockingState.Rejected) {
        RockingSetup(state, actions)
    } else if (state.rockingState is RockingState.Unconfirmed) {
        WarningPanel(state.rockingState.message) {
            OutlinedButton(onClick = actions.stopRocking, shape = MaterialTheme.shapes.small) {
                Text("Send stop again")
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 28.dp), color = MaterialTheme.colorScheme.outlineVariant)
    DriveModeLauncher(state = state, onClick = { showDriveModes = true })
    Spacer(Modifier.height(24.dp))
    if (showDriveModes) {
        DriveModeSheet(
            state = state,
            onDismiss = { showDriveModes = false },
            onMode = {
                actions.setDriveMode(it)
                showDriveModes = false
            },
        )
    }
}

@Composable
private fun BrandHeader(
    state: PriamUiState,
    disconnect: (() -> Unit)? = null,
    onSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSettings) { Text("SETTINGS") }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(statusColor(state), CircleShape))
        Spacer(Modifier.width(9.dp))
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(targetState = state.statusMessage, label = "connection status") { message ->
                Text(
                    message,
                    color = if (state.connectionPhase == ConnectionPhase.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        state.batteryPercent?.let { battery ->
            Text("$battery%", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
        }
        if (disconnect != null) {
            TextButton(onClick = disconnect, enabled = !state.motionMayBeActive) {
                Text(if (state.motionMayBeActive) "ACTIVE" else "Disconnect")
            }
        }
    }
}

@Composable
private fun ConnectionGuide(scanning: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(20.dp)) {
            StrollerRadar(scanning, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            GuideStep("1", "Power on the e-Priam")
            GuideStep("2", "In the official Cybex app, disconnect the stroller — or close the app completely")
            GuideStep("3", "Keep this phone close to the handle")
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(13.dp))
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StrollerRadar(scanning: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan radar")
    val pulse by transition.animateFloat(
        initialValue = if (scanning) 0.45f else 0.72f,
        targetValue = if (scanning) 1f else 0.72f,
        animationSpec = infiniteRepeatable(tween(if (scanning) 1300 else 1), RepeatMode.Restart),
        label = "scan pulse",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color.copy(alpha = (1f - pulse) * 0.22f), radius = size.minDimension * pulse / 2)
            drawCircle(color.copy(alpha = 0.12f), radius = size.minDimension * 0.34f)
        }
        Box(
            modifier = Modifier.size(52.dp).background(color, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            StrollerGlyph(Modifier.size(34.dp), MaterialTheme.colorScheme.onPrimary)
        }
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -65 -> "Strong signal · ready to connect"
    rssi >= -80 -> "Stroller nearby"
    else -> "Move closer for a reliable connection"
}

@Composable
private fun statusColor(state: PriamUiState): Color = when (state.connectionPhase) {
    ConnectionPhase.READY, ConnectionPhase.DEMO -> MaterialTheme.colorScheme.primary
    ConnectionPhase.CONNECTING, ConnectionPhase.DISCOVERING, ConnectionPhase.SCANNING -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun RockingHero(state: PriamUiState, actions: PriamActions) {
    val active = state.rockingState is RockingState.Active
    val surfaceColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        color = surfaceColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp)) {
            if (active) RockingPulse(Modifier.align(Alignment.CenterEnd))
            Column {
                Text(
                    when (state.rockingState) {
                        is RockingState.Active -> "ROCKING"
                        is RockingState.Starting -> "STARTING"
                        is RockingState.Stopping -> "STOPPING"
                        else -> "READY"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                val remaining = (state.rockingState as? RockingState.Active)?.remainingSeconds
                AnimatedContent(targetState = remaining, label = "rocking timer") { seconds ->
                    Text(
                        seconds?.let(::formatDuration) ?: "Rock gently.",
                        style = if (seconds == null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (state.rockingState is RockingState.Active) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${state.rockingState.intensity.displayName} intensity · ${formatDuration(state.rockingState.configuredSeconds)} set",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = actions.stopRocking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("STOP ROCKING") }
                }
                if (state.rockingState is RockingState.Starting || state.rockingState is RockingState.Stopping) {
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Square)
                }
            }
        }
    }
}

@Composable
private fun RockingPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rocking pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), repeatMode = RepeatMode.Restart),
        label = "pulse radius",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(150.dp)) {
        drawCircle(color.copy(alpha = (1f - pulse) * 0.22f), radius = size.minDimension * pulse / 2)
        drawCircle(color.copy(alpha = 0.14f), radius = size.minDimension * 0.30f)
        drawCircle(color.copy(alpha = 0.24f), radius = size.minDimension * 0.17f)
    }
}

private fun selectedDriveMode(state: PriamUiState): DriveMode? = when (val drive = state.driveState) {
    is DriveState.Applying -> drive.mode
    is DriveState.Commanded -> drive.mode
    is DriveState.Observed -> drive.mode
    DriveState.Unknown -> null
}

@Composable
private fun DriveModeLauncher(state: PriamUiState, onClick: () -> Unit) {
    val selected = selectedDriveMode(state)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("↗", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Drive assistance", style = MaterialTheme.typography.titleLarge)
                Text(
                    selected?.let { "${it.displayName} mode" } ?: "Choose motor support",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (state.driveState is DriveState.Applying) "APPLYING" else "CHANGE  ›",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveModeSheet(state: PriamUiState, onDismiss: () -> Unit, onMode: (DriveMode) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Drive assistance", style = MaterialTheme.typography.headlineLarge)
            Text("Choose how strongly the motor supports your push.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            DriveMode.entries.forEach { mode ->
                DriveModeCard(
                    mode = mode,
                    selected = selectedDriveMode(state) == mode,
                    enabled = state.driveState !is DriveState.Applying,
                    onClick = { onMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun DriveModeCard(mode: DriveMode, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = when (mode) {
        DriveMode.ECO -> "Gentle support · maximum range"
        DriveMode.TOUR -> "Balanced everyday support"
        DriveMode.BOOST -> "Maximum support when you need it"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.displayName.uppercase(), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(3.dp))
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Text("CURRENT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RockingSetup(state: PriamUiState, actions: PriamActions) {
    SectionTitle("Rocking", "Set the motion and duration, then start when you are ready.")
    Spacer(Modifier.height(22.dp))
    ControlLabel("INTENSITY")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RockingIntensity.entries.reversed().forEach { intensity ->
            IntensityTile(
                intensity = intensity,
                selected = state.selectedIntensity == intensity,
                onClick = { actions.setIntensity(intensity) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(28.dp))
    ControlLabel("DURATION")
    Spacer(Modifier.height(10.dp))
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DurationStepButton("−", enabled = state.selectedDurationMinutes > 5) {
                actions.setDuration((state.selectedDurationMinutes - 5).coerceAtLeast(5))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.selectedDurationMinutes}", style = MaterialTheme.typography.displayLarge)
                Text(
                    "MINUTES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DurationStepButton("+", enabled = state.selectedDurationMinutes < 180) {
                actions.setDuration((state.selectedDurationMinutes + 5).coerceAtMost(180))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 60, 120, 180).forEach { minutes ->
            Surface(
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .clickable { actions.setDuration(minutes) },
                color = if (state.selectedDurationMinutes == minutes) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    minutes.toString(),
                    modifier = Modifier.padding(vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    color = if (state.selectedDurationMinutes == minutes) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    AnimatedVisibility(state.selectedDurationMinutes * 60 > DOCUMENTED_MAX_DURATION_SECONDS) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "Beyond Cybex’s 30-minute limit · stay nearby",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (state.rockingState is RockingState.Rejected) {
        Text(
            state.rockingState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    Spacer(Modifier.height(22.dp))
    Button(
        onClick = actions.startRocking,
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text("START · ${state.selectedDurationMinutes} MIN · ${state.selectedIntensity.displayName.uppercase()}")
    }
}

@Composable
private fun IntensityTile(
    intensity: RockingIntensity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IntensityWave(intensity, selected, Modifier.fillMaxWidth().height(26.dp))
            Spacer(Modifier.height(9.dp))
            Text(intensity.displayName.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun IntensityWave(intensity: RockingIntensity, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val amplitude = when (intensity) {
        RockingIntensity.LOW -> 0.18f
        RockingIntensity.MEDIUM -> 0.30f
        RockingIntensity.HIGH -> 0.43f
    }
    Canvas(modifier) {
        val pointCount = 32
        repeat(pointCount - 1) { index ->
            val x1 = size.width * index / (pointCount - 1)
            val x2 = size.width * (index + 1) / (pointCount - 1)
            val y1 = size.height * (0.5f + amplitude * sin(index * PI * 3 / (pointCount - 1)).toFloat())
            val y2 = size.height * (0.5f + amplitude * sin((index + 1) * PI * 3 / (pointCount - 1)).toFloat())
            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun DurationStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(58.dp).clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (enabled) 1f else 0.35f),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ControlLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WarningPanel(message: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            content()
        }
    }
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Preview(showBackground = true)
@Composable
private fun DisclaimerPreview() {
    EPriamConnectTheme(darkTheme = true) { PriamAppContent(PriamUiState(), PriamActions()) }
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
