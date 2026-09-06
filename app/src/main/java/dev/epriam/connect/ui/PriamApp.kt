package dev.epriam.connect.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.epriam.connect.BuildConfig
import dev.epriam.connect.R
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
                themeMode = state.themeMode,
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
private fun DisclaimerScreen(themeMode: ThemeMode, onAccept: () -> Unit, modifier: Modifier = Modifier) {
    var responsibilityAccepted by remember { mutableStateOf(false) }
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val welcomeAccent = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 184.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(175.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(160.dp)) {
                    drawCircle(welcomeAccent.copy(alpha = 0.06f), radius = size.minDimension * 0.48f)
                    drawCircle(
                        welcomeAccent.copy(alpha = 0.28f),
                        radius = size.minDimension * 0.46f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
                Image(
                    painter = painterResource(if (darkTheme) R.drawable.stroller_hero_dark else R.drawable.stroller_hero_light),
                    contentDescription = null,
                    modifier = Modifier.size(width = 180.dp, height = 145.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (darkTheme) {
                        ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                    } else {
                        null
                    },
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "e-Priam Companion",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Direct Bluetooth controls for rocking and drive assistance.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        "This independent app controls stroller hardware. Extended rocking and Boost go beyond official controls. Apply the brake, stay beside the stroller, and accept responsibility for injury or damage.",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { responsibilityAccepted = !responsibilityAccepted }
                        .semantics { contentDescription = "Accept responsibility" }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = responsibilityAccepted, onCheckedChange = { responsibilityAccepted = it })
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "I understand and take responsibility",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onAccept,
                enabled = responsibilityAccepted,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Get started")
            }
        }
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
    var protocolLogExpanded by remember { mutableStateOf(false) }
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
                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { protocolLogExpanded = !protocolLogExpanded }
                            .semantics {
                                contentDescription = if (protocolLogExpanded) {
                                    "Collapse protocol log"
                                } else {
                                    "Expand protocol log"
                                }
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
                            if (protocolLogExpanded) "Hide" else "Show",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    AnimatedVisibility(visible = protocolLogExpanded) {
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
                            state.batteryRawValue?.let { raw ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("Battery raw value: $raw", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if (state.isReady) {
            Spacer(Modifier.height(30.dp))
            OutlinedButton(
                onClick = actions.disconnect,
                enabled = !state.motionMayBeActive,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (state.motionMayBeActive) "Stop rocking before disconnecting" else "Disconnect stroller")
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StrollerAppBadge(Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Text("PRIAM / CONNECT", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StrollerAppBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFDDF6EC), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.stroller_hero_light),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 6.dp),
            contentScale = ContentScale.Fit,
        )
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
    BrandHeader(state, onSettings)
    Spacer(Modifier.height(6.dp))
    RockingHero(state)
    Spacer(Modifier.height(12.dp))
    if (state.rockingState !is RockingState.Unconfirmed) {
        RockingSetup(state, actions)
    } else {
        WarningPanel(state.rockingState.message) {
            OutlinedButton(onClick = actions.stopRocking, shape = MaterialTheme.shapes.small) {
                Text("Send stop again")
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    DriveModeLauncher(state = state, onClick = { showDriveModes = true })
    Spacer(Modifier.height(24.dp))
    if (showDriveModes) {
        DriveModeSheet(
            state = state,
            onDismiss = { showDriveModes = false },
            onMode = {
                actions.setDriveMode(it)
                if (it != DriveMode.BOOST) showDriveModes = false
            },
        )
    }
}

@Composable
private fun BrandHeader(
    state: PriamUiState,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrollerAppBadge(Modifier.size(46.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("e-Priam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(statusColor(state), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (state.isReady) "Connected" else state.statusMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        state.batteryPercent?.let { battery ->
            BatteryGlyph(Modifier.size(25.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("$battery%", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(14.dp))
        }
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onSettings)
                .semantics { contentDescription = "Settings" },
            contentAlignment = Alignment.Center,
        ) {
            SettingsGlyph(Modifier.size(28.dp), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SettingsGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.27f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawCircle(color = color, radius = size.minDimension * 0.07f, center = center)
        val directions = listOf(
            Offset(0f, -1f), Offset(0.71f, -0.71f), Offset(1f, 0f), Offset(0.71f, 0.71f),
            Offset(0f, 1f), Offset(-0.71f, 0.71f), Offset(-1f, 0f), Offset(-0.71f, -0.71f),
        )
        directions.forEach { direction ->
            drawLine(
                color = color,
                start = center + direction * size.minDimension * 0.31f,
                end = center + direction * size.minDimension * 0.45f,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun BatteryGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.05f, size.height * 0.25f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.78f, size.height * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.86f, size.height * 0.39f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.09f, size.height * 0.22f),
        )
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.13f, size.height * 0.33f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.34f),
        )
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
        StrollerAppBadge(Modifier.size(58.dp))
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
private fun RockingHero(state: PriamUiState) {
    val activeState = state.rockingState as? RockingState.Active
    val active = activeState != null
    val busy = state.rockingState is RockingState.Starting || state.rockingState is RockingState.Stopping
    val darkTheme = when (state.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val heroImage = if (darkTheme) R.drawable.stroller_hero_dark else R.drawable.stroller_hero_light
    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(206.dp), contentAlignment = Alignment.Center) {
            RockingRings(active = active || busy, modifier = Modifier.fillMaxWidth().height(88.dp).align(Alignment.BottomCenter))
            Image(
                painter = painterResource(heroImage),
                contentDescription = null,
                modifier = Modifier.size(width = 235.dp, height = 185.dp),
                contentScale = ContentScale.Fit,
                colorFilter = if (darkTheme) {
                    ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                } else {
                    null
                },
            )
        }
        Text(
            if (active) "Rocking in progress" else if (busy) "Preparing rocking" else "Ready to rock",
            style = if (active || busy) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
            fontWeight = if (active || busy) FontWeight.Medium else FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (active) {
            RollingTimer(activeState.remainingSeconds)
        }
        if (busy) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Square)
        }
    }
}

@Composable
private fun RollingTimer(seconds: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        formatDuration(seconds).forEachIndexed { index, character ->
            if (character == ':') {
                Text(
                    character.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = {
                        slideInVertically(animationSpec = tween(340)) { height -> height } togetherWith
                            slideOutVertically(animationSpec = tween(340)) { height -> -height }
                    },
                    label = "timer digit $index",
                ) { digit ->
                    Text(
                        digit.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun RockingRings(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rocking pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (active) 1600 else 1), repeatMode = RepeatMode.Restart),
        label = "pulse radius",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val groundY = size.height * 0.72f
        repeat(5) { index ->
            val widthScale = (0.34f + index * 0.13f) * pulse
            val ringHeight = size.height * (0.08f + index * 0.035f)
            drawOval(
                color = color.copy(alpha = if (active) 0.52f else 0.10f),
                topLeft = Offset(size.width * (1f - widthScale) / 2, groundY - ringHeight / 2),
                size = androidx.compose.ui.geometry.Size(size.width * widthScale, ringHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()),
            )
        }
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
        color = Color.Transparent,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_drive_assistance),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Drive assistance", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    selected?.let { "${it.displayName} mode" } ?: "Choose motor support",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (state.driveState is DriveState.Applying) "APPLYING" else "›",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveModeSheet(state: PriamUiState, onDismiss: () -> Unit, onMode: (DriveMode) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val selectedMode = selectedDriveMode(state)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Drive assistance", style = MaterialTheme.typography.headlineMedium)
            Text("Choose how much support you want.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            DriveMode.entries.forEach { mode ->
                DriveModeCard(
                    mode = mode,
                    selected = selectedMode == mode,
                    enabled = state.driveState !is DriveState.Applying,
                    onClick = { onMode(mode) },
                )
            }
            AnimatedVisibility(visible = selectedMode == DriveMode.BOOST) {
                BoostRiskNotice()
            }
        }
    }
}

@Composable
private fun DriveModeCard(mode: DriveMode, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val (summary, detail) = when (mode) {
        DriveMode.ECO -> "Maximum range" to "Gentle support for everyday strolls."
        DriveMode.TOUR -> "Balanced" to "A smooth mix of support and range."
        DriveMode.BOOST -> "Maximum support" to "Extra power for hills and heavier loads."
    }
    val selectionColor = if (mode.experimental) Color(0xFFE69A16) else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) selectionColor.copy(alpha = 0.10f) else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (selected) selectionColor else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(driveModeIcon(mode)),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mode.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (mode.experimental) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "EXPERIMENTAL",
                            color = selectionColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(summary, color = MaterialTheme.colorScheme.onSurface)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Box(
                modifier = Modifier.size(24.dp).background(
                    if (selected) selectionColor else Color.Transparent,
                    CircleShape,
                ).then(
                    if (selected) Modifier else Modifier.background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(if (selected) 8.dp else 20.dp).background(
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
                        CircleShape,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BoostRiskNotice() {
    val warningColor = Color(0xFFE69A16)
    Surface(
        color = warningColor.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, warningColor.copy(alpha = 0.70f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Experimental mode",
                color = warningColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Boost is a hidden mode not exposed by the official Cybex app. Its behavior is undocumented and may vary by stroller firmware. Use it with extra care; switch back to Eco or Tour if anything feels unexpected.",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun driveModeIcon(mode: DriveMode): Int = when (mode) {
    DriveMode.ECO -> R.drawable.ic_drive_eco
    DriveMode.TOUR -> R.drawable.ic_drive_tour
    DriveMode.BOOST -> R.drawable.ic_drive_boost
}

@Composable
private fun RockingSetup(state: PriamUiState, actions: PriamActions) {
    val active = state.rockingState is RockingState.Active
    val busy = state.rockingState is RockingState.Starting || state.rockingState is RockingState.Stopping
    val controlsEnabled = !active && !busy
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RockingIntensity.entries.reversed().forEach { intensity ->
            IntensityTile(
                intensity = intensity,
                selected = state.selectedIntensity == intensity,
                enabled = controlsEnabled,
                onClick = { actions.setIntensity(intensity) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Duration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("${state.selectedDurationMinutes} min", style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(10.dp))
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DurationStepButton("−", enabled = controlsEnabled && state.selectedDurationMinutes > 5) {
                actions.setDuration((state.selectedDurationMinutes - 5).coerceAtLeast(5))
            }
            Text("${state.selectedDurationMinutes}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            DurationStepButton("+", enabled = controlsEnabled && state.selectedDurationMinutes < 180) {
                actions.setDuration((state.selectedDurationMinutes + 5).coerceAtMost(180))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(30, 60, 90, 120, 180).forEach { minutes ->
            Surface(
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .clickable(enabled = controlsEnabled) { actions.setDuration(minutes) },
                color = if (state.selectedDurationMinutes == minutes) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(
                    1.dp,
                    if (state.selectedDurationMinutes == minutes) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    minutes.toString(),
                    modifier = Modifier.padding(vertical = 15.dp),
                    textAlign = TextAlign.Center,
                    color = if (state.selectedDurationMinutes == minutes) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
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
        onClick = if (active) actions.stopRocking else actions.startRocking,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = MaterialTheme.shapes.medium,
        colors = if (active) {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE5252A),
                contentColor = Color.White,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Image(
            painter = painterResource(if (active) R.drawable.ic_rocking_stop else R.drawable.ic_rocking_play),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(if (active) Color.White else MaterialTheme.colorScheme.onPrimary),
        )
        Spacer(Modifier.width(10.dp))
        Text(if (active) "Stop Rocking" else if (busy) "Please wait…" else "Start Rocking")
    }
}

@Composable
private fun IntensityTile(
    intensity: RockingIntensity,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IntensityWave(intensity, selected, Modifier.fillMaxWidth().height(20.dp))
            Spacer(Modifier.height(5.dp))
            Text(intensity.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                when (intensity) {
                    RockingIntensity.LOW -> "Gentle"
                    RockingIntensity.MEDIUM -> "Balanced"
                    RockingIntensity.HIGH -> "Strong"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        modifier = Modifier.width(72.dp).height(64.dp).clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
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
