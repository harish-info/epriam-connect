package dev.epriam.connect.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.epriam.connect.R
import dev.epriam.connect.domain.ThemeMode

@Composable
internal fun DisclaimerScreen(
    themeMode: ThemeMode,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var responsibilityAccepted by remember { mutableStateOf(false) }
    val darkTheme = themeMode.isDarkTheme()
    val welcomeAccent = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 184.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(175.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(160.dp)) {
                    drawCircle(welcomeAccent.copy(alpha = 0.06f), radius = size.minDimension * 0.48f)
                    drawCircle(
                        welcomeAccent.copy(alpha = 0.28f),
                        radius = size.minDimension * 0.46f,
                        style = Stroke(1.dp.toPx()),
                    )
                }
                Image(
                    painter = painterResource(
                        if (darkTheme) R.drawable.stroller_hero_dark else R.drawable.stroller_hero_light,
                    ),
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
                DisclaimerWarning()
            }
        }
        DisclaimerAcceptance(
            accepted = responsibilityAccepted,
            onAcceptedChange = { responsibilityAccepted = it },
            onAccept = onAccept,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DisclaimerWarning() {
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

@Composable
private fun DisclaimerAcceptance(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
                    .clickable { onAcceptedChange(!accepted) }
                    .semantics { contentDescription = "Accept responsibility" }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
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
            enabled = accepted,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Get started")
        }
    }
}

@Composable
private fun ThemeMode.isDarkTheme(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}
