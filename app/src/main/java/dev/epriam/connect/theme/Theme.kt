package dev.epriam.connect.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = SignalGreenLight,
    onPrimary = DeepGreen,
    primaryContainer = DeepGreen,
    onPrimaryContainer = SignalGreenLight,
    secondary = Color(0xFFB5CCC2),
    secondaryContainer = Color(0xFF245A47),
    onSecondaryContainer = Color(0xFFB9F3DA),
    tertiary = Amber,
    background = Night,
    onBackground = Color(0xFFE4E9E5),
    surface = NightSurface,
    surfaceVariant = NightRaised,
    onSurface = Color(0xFFE4E9E5),
    onSurfaceVariant = Color(0xFFBBC5C0),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = SignalGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F1D8),
    onPrimaryContainer = DeepGreen,
    secondary = Color(0xFF4B6359),
    secondaryContainer = Color(0xFFB5F1D8),
    onSecondaryContainer = DeepGreen,
    tertiary = Color(0xFF8A5100),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    surfaceVariant = Color(0xFFE4EAE5),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF48534E),
    error = Danger,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun EPriamConnectTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
