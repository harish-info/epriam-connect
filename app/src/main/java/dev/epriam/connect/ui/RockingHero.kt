package dev.epriam.connect.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.epriam.connect.R
import dev.epriam.connect.domain.PriamUiState
import dev.epriam.connect.domain.RockingState
import dev.epriam.connect.domain.ThemeMode

@Composable
internal fun RockingHero(state: PriamUiState) {
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
        RockingArtwork(heroImage = heroImage, darkTheme = darkTheme, compact = active || busy)
        if (!active && !busy) Spacer(Modifier.height(8.dp))
        Text(
            when {
                active -> "Rocking in progress"
                busy -> "Preparing rocking"
                else -> "Ready to rock"
            },
            style = if (active || busy) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.headlineMedium,
            fontWeight = if (active || busy) FontWeight.Medium else FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        activeState?.let { RollingTimer(it.remainingSeconds) }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Square)
        }
    }
}

@Composable
private fun RockingArtwork(heroImage: Int, darkTheme: Boolean, compact: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(if (compact) 126.dp else 174.dp),
        contentAlignment = Alignment.Center,
    ) {
        RockingRings(
            active = compact,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 66.dp else 76.dp)
                .align(Alignment.BottomCenter),
        )
        Image(
            painter = painterResource(heroImage),
            contentDescription = null,
            modifier = Modifier.size(
                width = if (compact) 172.dp else 210.dp,
                height = if (compact) 128.dp else 158.dp,
            ),
            contentScale = ContentScale.Fit,
            colorFilter = if (darkTheme) {
                ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
            } else {
                null
            },
        )
    }
}

@Composable
private fun RollingTimer(seconds: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        formatDuration(seconds).forEachIndexed { index, character ->
            if (character == ':') {
                TimerCharacter(character)
            } else {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = {
                        slideInVertically(animationSpec = tween(340)) { height -> height } togetherWith
                            slideOutVertically(animationSpec = tween(340)) { height -> -height }
                    },
                    label = "timer digit $index",
                ) { digit ->
                    TimerCharacter(digit)
                }
            }
        }
    }
}

@Composable
private fun TimerCharacter(character: Char) {
    Text(
        character.toString(),
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
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
                size = Size(size.width * widthScale, ringHeight),
                style = Stroke(1.2.dp.toPx()),
            )
        }
    }
}

internal fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
