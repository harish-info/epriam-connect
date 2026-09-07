package dev.epriam.connect.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.epriam.connect.BuildConfig
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.DeviceCandidate
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState

@Composable
internal fun ConnectionScreen(
    state: PriamUiState,
    actions: PriamActions,
    onSettings: () -> Unit,
) {
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

    (state.rockingState as? RockingState.Unconfirmed)?.let { unconfirmed ->
        WarningPanel(unconfirmed.message) {
            Button(
                onClick = actions.acknowledgeStopped,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("I verified it stopped")
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    CandidateList(state = state, onConnect = actions.connect)
    ScanButton(state = state, onScan = actions.scan)

    if (BuildConfig.DEBUG) {
        TextButton(
            onClick = actions.enterDemo,
            enabled = !state.connectionPhase.isConnecting,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Preview controls without stroller")
        }
    }
}

@Composable
private fun CandidateList(state: PriamUiState, onConnect: (DeviceCandidate) -> Unit) {
    if (state.candidates.isEmpty()) return

    Text("Available strollers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    state.candidates.forEachIndexed { index, candidate ->
        CandidateCard(
            candidate = candidate,
            connecting = state.connectionPhase.isConnecting && state.connectedDeviceName == candidate.name,
            connectionInProgress = state.connectionPhase.isConnecting,
            statusMessage = state.statusMessage,
            onConnect = { onConnect(candidate) },
        )
        if (index < state.candidates.lastIndex) Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun CandidateCard(
    candidate: DeviceCandidate,
    connecting: Boolean,
    connectionInProgress: Boolean,
    statusMessage: String,
    onConnect: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !connectionInProgress, onClick = onConnect),
        color = if (connecting) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (connecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    candidate.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (connecting) statusMessage else signalLabel(candidate.rssi),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp).semantics {
                        contentDescription = "Connecting to stroller"
                    },
                    strokeWidth = 2.5.dp,
                )
            } else {
                Text("CONNECT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ScanButton(state: PriamUiState, onScan: () -> Unit) {
    val scanning = state.connectionPhase == ConnectionPhase.SCANNING
    Button(
        onClick = onScan,
        enabled = !scanning && !state.connectionPhase.isConnecting,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (scanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).semantics {
                    contentDescription = "Scanning for stroller"
                },
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(if (scanning) "Scanning nearby…" else "Scan for e-Priam")
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

private val ConnectionPhase.isConnecting: Boolean
    get() = this == ConnectionPhase.CONNECTING || this == ConnectionPhase.DISCOVERING

internal fun signalLabel(rssi: Int): String = when {
    rssi >= -65 -> "Strong signal · ready to connect"
    rssi >= -80 -> "Stroller nearby"
    else -> "Move closer for a reliable connection"
}
