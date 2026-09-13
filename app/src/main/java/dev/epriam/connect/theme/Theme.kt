package dev.epriam.connect.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.epriam.connect.domain.ThemePalette

private val MintDarkColors = darkColorScheme(
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

private val MintLightColors = lightColorScheme(
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

private val RoseGoldDarkColors = darkColorScheme(
    primary = RoseLight,
    onPrimary = DeepRose,
    primaryContainer = Color(0xFF60442F),
    onPrimaryContainer = Color(0xFFFFE0C8),
    secondary = Color(0xFFD8C2B5),
    secondaryContainer = Color(0xFF53433A),
    onSecondaryContainer = Color(0xFFF5DED1),
    tertiary = Color(0xFFE2C179),
    background = RoseNight,
    onBackground = Color(0xFFEEE2D8),
    surface = RoseNightSurface,
    surfaceVariant = RoseNightRaised,
    onSurface = Color(0xFFEEE2D8),
    onSurfaceVariant = Color(0xFFD2C2B6),
    error = Color(0xFFFFB4AB),
)

private val RoseGoldLightColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3D7C2),
    onPrimaryContainer = Color(0xFF32190B),
    secondary = Color(0xFF725C50),
    secondaryContainer = Color(0xFFF2DFD4),
    onSecondaryContainer = Color(0xFF281811),
    tertiary = Color(0xFF755F28),
    background = RosePaper,
    onBackground = Color(0xFF201A16),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEE2D9),
    onSurface = Color(0xFF201A16),
    onSurfaceVariant = Color(0xFF51463F),
    error = Danger,
)

private val OceanDarkColors = darkColorScheme(
    primary = OceanLight,
    onPrimary = DeepOcean,
    primaryContainer = Color(0xFF004D69),
    onPrimaryContainer = Color(0xFFC5E7FF),
    secondary = Color(0xFFB5CAD6),
    secondaryContainer = Color(0xFF354A54),
    onSecondaryContainer = Color(0xFFD0E6F2),
    tertiary = Color(0xFFCBC1EA),
    background = OceanNight,
    onBackground = Color(0xFFDDE4E9),
    surface = OceanNightSurface,
    surfaceVariant = OceanNightRaised,
    onSurface = Color(0xFFDDE4E9),
    onSurfaceVariant = Color(0xFFBEC8CE),
    error = Color(0xFFFFB4AB),
)

private val OceanLightColors = lightColorScheme(
    primary = Ocean,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5E7FF),
    onPrimaryContainer = Color(0xFF001E2D),
    secondary = Color(0xFF4D616C),
    secondaryContainer = Color(0xFFD0E6F2),
    onSecondaryContainer = Color(0xFF091E27),
    tertiary = Color(0xFF625A7C),
    background = OceanPaper,
    onBackground = Color(0xFF171C20),
    surface = Color.White,
    surfaceVariant = Color(0xFFDEE3E7),
    onSurface = Color(0xFF171C20),
    onSurfaceVariant = Color(0xFF41484D),
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
    palette: ThemePalette = ThemePalette.MINT,
    content: @Composable () -> Unit,
) {
    val colors = when (palette) {
        ThemePalette.MINT -> if (darkTheme) MintDarkColors else MintLightColors
        ThemePalette.ROSE_GOLD -> if (darkTheme) RoseGoldDarkColors else RoseGoldLightColors
        ThemePalette.OCEAN -> if (darkTheme) OceanDarkColors else OceanLightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}

fun ThemePalette.previewColor(): Color = when (this) {
    ThemePalette.MINT -> SignalGreen
    ThemePalette.ROSE_GOLD -> Rose
    ThemePalette.OCEAN -> Ocean
}

fun ThemePalette.notificationAccentArgb(): Int = previewColor().toArgb()
