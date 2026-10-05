package dev.epriam.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.epriam.connect.domain.ConnectionPhase
import dev.epriam.connect.domain.PriamUiState

@Composable
internal fun ConnectionRecoveryPanel(
    state: PriamUiState,
    onRetry: () -> Unit,
    onStopEverything: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (state.connectionPhase == ConnectionPhase.RECONNECTING) {
                        "Connection lost"
                    } else {
                        "Couldn’t connect"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                reconnectTimeLabel(state.reconnectSecondsRemaining),
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Try again now")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onStopEverything,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Stop everything")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Stop everything closes Bluetooth, cancels scanning and reconnect attempts, " +
                    "and removes the background notification.",
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.motionMayBeActive) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "It cannot send a motor command while disconnected. Verify the stroller stopped.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun ConnectionStoppedPanel(message: String, onScanAgain: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "Not connected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Scan again")
            }
        }
    }
}

internal fun reconnectTimeLabel(secondsRemaining: Int?): String {
    if (secondsRemaining == null) return "Automatic retries stop after 3 minutes."
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    return "Automatic retries stop in %d:%02d.".format(minutes, seconds)
}
