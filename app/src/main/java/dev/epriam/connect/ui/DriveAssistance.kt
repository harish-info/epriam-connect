package dev.epriam.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.epriam.connect.R
import dev.epriam.connect.domain.DriveState
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.protocol.DriveMode

internal fun PriamUiState.selectedDriveMode(): DriveMode? = when (val drive = driveState) {
    is DriveState.Applying -> drive.mode
    is DriveState.Commanded -> drive.mode
    is DriveState.Observed -> drive.mode
    DriveState.Unknown -> null
}

@Composable
internal fun DriveModeLauncher(state: PriamUiState, onClick: () -> Unit) {
    val selected = state.selectedDriveMode()
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_drive_assistance),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
            Spacer(Modifier.width(12.dp))
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
internal fun DriveModeSheet(
    state: PriamUiState,
    onDismiss: () -> Unit,
    onMode: (DriveMode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val selectedMode = state.selectedDriveMode()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Text("Drive assistance", style = MaterialTheme.typography.headlineMedium)
            Text("Choose how much support you want.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
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
private fun DriveModeCard(
    mode: DriveMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (summary, detail) = mode.description
    val selectionColor = if (mode.experimental) BoostColor else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
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
                painter = painterResource(mode.iconResource),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
            Spacer(Modifier.width(18.dp))
            DriveModeDescription(mode = mode, summary = summary, detail = detail, modifier = Modifier.weight(1f))
            SelectionIndicator(selected = selected, color = selectionColor)
        }
    }
}

@Composable
private fun DriveModeDescription(
    mode: DriveMode,
    summary: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mode.displayName,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (mode.experimental) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "EXPERIMENTAL",
                    color = BoostColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.size(3.dp))
        Text(summary, color = MaterialTheme.colorScheme.onSurface)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(if (selected) color else MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 8.dp else 20.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun BoostRiskNotice() {
    Surface(
        color = BoostColor.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, BoostColor.copy(alpha = 0.70f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Experimental mode",
                color = BoostColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Boost is a hidden mode not exposed by the official Cybex app. Its behavior is undocumented and may vary by stroller firmware. Use it with extra care; switch back to Eco or Tour if anything feels unexpected.",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val DriveMode.description: Pair<String, String>
    get() = when (this) {
        DriveMode.ECO -> "Maximum range" to "Gentle support for everyday strolls."
        DriveMode.TOUR -> "Balanced" to "A smooth mix of support and range."
        DriveMode.BOOST -> "Maximum support" to "Extra power for hills and heavier loads."
    }

private val DriveMode.iconResource: Int
    get() = when (this) {
        DriveMode.ECO -> R.drawable.ic_drive_eco
        DriveMode.TOUR -> R.drawable.ic_drive_tour
        DriveMode.BOOST -> R.drawable.ic_drive_boost
    }

private val BoostColor = Color(0xFFE69A16)
