package dev.epriam.connect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.epriam.connect.R
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.PriamUiState

@Composable
internal fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StrollerAppBadge(Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Text("PRIAM / CONNECT", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun StrollerAppBadge(modifier: Modifier = Modifier) {
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
internal fun BrandHeader(
    state: PriamUiState,
    onSettings: () -> Unit,
    onExitDemo: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrollerAppBadge(Modifier.size(42.dp))
        Spacer(Modifier.width(10.dp))
        ConnectionIdentity(state)
        Spacer(Modifier.weight(1f))
        if (state.isDemo) {
            TextButton(onClick = onExitDemo) { Text("Exit preview") }
        } else {
            BatteryStatus(state.batteryPercent)
        }
        SettingsButton(onSettings)
    }
}

@Composable
private fun ConnectionIdentity(state: PriamUiState) {
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
}

@Composable
private fun BatteryStatus(batteryPercent: Int?) {
    batteryPercent?.let { percent ->
        BatteryGlyph(Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text("$percent%", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(14.dp))
    }
}

@Composable
private fun SettingsButton(onSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onSettings)
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center,
    ) {
        SettingsGlyph(Modifier.size(25.dp), MaterialTheme.colorScheme.onSurface)
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
            style = Stroke(stroke),
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
            size = Size(size.width * 0.78f, size.height * 0.5f),
            cornerRadius = CornerRadius(stroke, stroke),
            style = Stroke(stroke),
        )
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.86f, size.height * 0.39f),
            size = Size(size.width * 0.09f, size.height * 0.22f),
        )
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.13f, size.height * 0.33f),
            size = Size(size.width * 0.58f, size.height * 0.34f),
        )
    }
}

@Composable
private fun statusColor(state: PriamUiState): Color = when (state.connectionPhase) {
    ConnectionPhase.READY, ConnectionPhase.DEMO -> MaterialTheme.colorScheme.primary
    ConnectionPhase.CONNECTING,
    ConnectionPhase.DISCOVERING,
    ConnectionPhase.SCANNING,
    -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
