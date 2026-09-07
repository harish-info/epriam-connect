package dev.epriam.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.epriam.connect.R
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.domain.RockingSessionLimits
import dev.epriam.connect.protocol.DOCUMENTED_MAX_DURATION_SECONDS
import dev.epriam.connect.protocol.RockingIntensity
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun RockingSetup(state: PriamUiState, actions: PriamActions) {
    val active = state.rockingState is RockingState.Active
    val busy = state.rockingState is RockingState.Starting || state.rockingState is RockingState.Stopping
    val controlsEnabled = !busy

    IntensitySelector(
        selected = state.selectedIntensity,
        enabled = controlsEnabled,
        onSelected = actions.setIntensity,
    )
    Spacer(Modifier.height(16.dp))
    DurationSelector(
        selectedMinutes = state.selectedDurationMinutes,
        enabled = controlsEnabled,
        onSelected = actions.setDuration,
    )
    ExtendedDurationNotice(visible = state.selectedDurationMinutes * 60 > DOCUMENTED_MAX_DURATION_SECONDS)

    (state.rockingState as? RockingState.Rejected)?.let { rejected ->
        Text(
            rejected.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    Spacer(Modifier.height(20.dp))
    RockingActionButton(
        active = active,
        busy = busy,
        onClick = if (active) actions.stopRocking else actions.startRocking,
    )
}

@Composable
private fun IntensitySelector(
    selected: RockingIntensity,
    enabled: Boolean,
    onSelected: (RockingIntensity) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RockingIntensity.entries.reversed().forEach { intensity ->
            IntensityTile(
                intensity = intensity,
                selected = selected == intensity,
                enabled = enabled,
                onClick = { onSelected(intensity) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DurationSelector(
    selectedMinutes: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("$selectedMinutes min", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))
    DurationStepper(selectedMinutes = selectedMinutes, enabled = enabled, onSelected = onSelected)
    Spacer(Modifier.height(12.dp))
    DurationPresets(selectedMinutes = selectedMinutes, enabled = enabled, onSelected = onSelected)
}

@Composable
private fun DurationStepper(
    selectedMinutes: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DurationStepButton(
                "−",
                enabled = enabled && selectedMinutes > RockingSessionLimits.MIN_DURATION_MINUTES,
            ) {
                onSelected(
                    (selectedMinutes - RockingSessionLimits.DURATION_STEP_MINUTES)
                        .coerceAtLeast(RockingSessionLimits.MIN_DURATION_MINUTES),
                )
            }
            Text("$selectedMinutes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            DurationStepButton(
                "+",
                enabled = enabled && selectedMinutes < RockingSessionLimits.MAX_DURATION_MINUTES,
            ) {
                onSelected(
                    (selectedMinutes + RockingSessionLimits.DURATION_STEP_MINUTES)
                        .coerceAtMost(RockingSessionLimits.MAX_DURATION_MINUTES),
                )
            }
        }
    }
}

@Composable
private fun DurationPresets(
    selectedMinutes: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RockingSessionLimits.durationPresetsMinutes.forEach { minutes ->
            val selected = selectedMinutes == minutes
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled) { onSelected(minutes) },
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    minutes.toString(),
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ExtendedDurationNotice(visible: Boolean) {
    AnimatedVisibility(visible) {
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
}

@Composable
private fun RockingActionButton(active: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = if (active) {
            ButtonDefaults.buttonColors(containerColor = Color(0xFFE5252A), contentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Image(
            painter = painterResource(if (active) R.drawable.ic_rocking_stop else R.drawable.ic_rocking_play),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(if (active) Color.White else MaterialTheme.colorScheme.onPrimary),
        )
        Spacer(Modifier.width(8.dp))
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IntensityWave(intensity, selected, Modifier.fillMaxWidth().height(17.dp))
            Spacer(Modifier.height(4.dp))
            Text(intensity.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                intensity.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IntensityWave(
    intensity: RockingIntensity,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val (amplitude, cycles) = intensity.waveShape
    Canvas(modifier) {
        repeat(WAVE_POINT_COUNT - 1) { index ->
            val x1 = size.width * index / (WAVE_POINT_COUNT - 1)
            val x2 = size.width * (index + 1) / (WAVE_POINT_COUNT - 1)
            val y1 = waveY(index, WAVE_POINT_COUNT, amplitude, cycles, size.height)
            val y2 = waveY(index + 1, WAVE_POINT_COUNT, amplitude, cycles, size.height)
            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

private fun waveY(index: Int, pointCount: Int, amplitude: Float, cycles: Float, height: Float): Float =
    height * (0.5f + amplitude * sin(index * PI * 2 * cycles / (pointCount - 1)).toFloat())

@Composable
private fun DurationStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(64.dp)
            .height(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
            )
        }
    }
}

@Composable
internal fun WarningPanel(message: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            content()
        }
    }
}

private val RockingIntensity.description: String
    get() = when (this) {
        RockingIntensity.LOW -> "Gentle"
        RockingIntensity.MEDIUM -> "Balanced"
        RockingIntensity.HIGH -> "Strong"
    }

private val RockingIntensity.waveShape: Pair<Float, Float>
    get() = when (this) {
        RockingIntensity.LOW -> 0.16f to 1.25f
        RockingIntensity.MEDIUM -> 0.30f to 3f
        RockingIntensity.HIGH -> 0.40f to 5.5f
    }

private const val WAVE_POINT_COUNT = 72
