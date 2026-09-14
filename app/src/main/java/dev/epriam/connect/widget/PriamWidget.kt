package dev.epriam.connect.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import dev.epriam.connect.MainActivity
import dev.epriam.connect.PriamApplication
import dev.epriam.connect.R
import dev.epriam.connect.domain.ThemeMode
import dev.epriam.connect.domain.ThemePalette
import dev.epriam.connect.service.RockingSessionService

class PriamWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as PriamApplication).repository
        provideContent {
            val state by repository.state.collectAsState()
            PriamWidgetContent(state.toWidgetPresentation())
        }
    }
}

class PriamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PriamWidget()
}

@Composable
internal fun PriamWidgetContent(presentation: PriamWidgetPresentation) {
    val context = LocalContext.current
    val colors = presentation.widgetColors()
    val openApp = actionStartActivity<MainActivity>()
    val primaryAction = when (presentation.action) {
        WidgetAction.START_ROCKING -> actionRunCallback<StartRockingAction>()
        WidgetAction.STOP_ROCKING -> actionStartService(
            Intent(context, RockingSessionService::class.java)
                .setAction(RockingSessionService.ACTION_STOP),
            isForegroundService = true,
        )
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(colors.background)
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .clickable(openApp)
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_stroller),
                contentDescription = "ePriam stroller",
                modifier = GlanceModifier.size(28.dp),
                colorFilter = ColorFilter.tint(colors.primary),
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = presentation.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = presentation.detail,
                    maxLines = 1,
                    style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        DurationPresets(presentation, colors)
        Spacer(GlanceModifier.height(10.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(48.dp)
                .background(if (presentation.primaryEnabled) colors.primary else colors.control)
                .cornerRadius(24.dp)
                .clickable(primaryAction),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = presentation.actionLabel,
                style = TextStyle(
                    color = if (presentation.primaryEnabled) colors.onPrimary else colors.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun DurationPresets(
    presentation: PriamWidgetPresentation,
    colors: WidgetColors,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        DurationButton(
            minutes = 10,
            selected = presentation.selectedDurationMinutes == 10,
            enabled = presentation.durationEnabled,
            colors = colors,
            onClick = actionRunCallback<Select10MinuteAction>(),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        DurationButton(
            minutes = 30,
            selected = presentation.selectedDurationMinutes == 30,
            enabled = presentation.durationEnabled,
            colors = colors,
            onClick = actionRunCallback<Select30MinuteAction>(),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        DurationButton(
            minutes = 60,
            selected = presentation.selectedDurationMinutes == 60,
            enabled = presentation.durationEnabled,
            colors = colors,
            onClick = actionRunCallback<Select60MinuteAction>(),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun DurationButton(
    minutes: Int,
    selected: Boolean,
    enabled: Boolean,
    colors: WidgetColors,
    onClick: androidx.glance.action.Action,
    modifier: GlanceModifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                when {
                    selected -> colors.primary
                    enabled -> colors.control
                    else -> colors.disabledControl
                },
            )
            .cornerRadius(24.dp)
            .then(if (enabled) GlanceModifier.clickable(onClick) else GlanceModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$minutes min",
            style = TextStyle(
                color = if (selected) colors.onPrimary else if (enabled) colors.onBackground else colors.secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private data class WidgetColors(
    val background: GlanceColorProvider,
    val onBackground: GlanceColorProvider,
    val secondary: GlanceColorProvider,
    val primary: GlanceColorProvider,
    val onPrimary: GlanceColorProvider,
    val control: GlanceColorProvider,
    val disabledControl: GlanceColorProvider,
)

private fun PriamWidgetPresentation.widgetColors(): WidgetColors {
    val light = when (palette) {
        ThemePalette.MINT -> WidgetPalette(
            0xFFF4F6F1, 0xFF101412, 0xFF48534E, 0xFF006B52, 0xFFFFFFFF, 0xFFE4EAE5, 0xFFF0F2EE,
        )
        ThemePalette.ROSE_GOLD -> WidgetPalette(
            0xFFFBF8F5, 0xFF201A16, 0xFF51463F, 0xFF9A684A, 0xFFFFFFFF, 0xFFEEE2D9, 0xFFF7F1EC,
        )
        ThemePalette.OCEAN -> WidgetPalette(
            0xFFF6FAFD, 0xFF171C20, 0xFF41484D, 0xFF00658B, 0xFFFFFFFF, 0xFFDEE3E7, 0xFFEEF3F6,
        )
    }
    val dark = when (palette) {
        ThemePalette.MINT -> WidgetPalette(
            0xFF121816, 0xFFE4E9E5, 0xFFBBC5C0, 0xFF75F8C6, 0xFF003D2E, 0xFF1A2420, 0xFF151C19,
        )
        ThemePalette.ROSE_GOLD -> WidgetPalette(
            0xFF191512, 0xFFEEE2D8, 0xFFD2C2B6, 0xFFE7BB98, 0xFF442C1D, 0xFF29221C, 0xFF211B17,
        )
        ThemePalette.OCEAN -> WidgetPalette(
            0xFF0F181E, 0xFFDDE4E9, 0xFFBEC8CE, 0xFF78D1FF, 0xFF003548, 0xFF19252C, 0xFF131E24,
        )
    }
    return WidgetColors(
        background = themeColor(light.background, dark.background),
        onBackground = themeColor(light.onBackground, dark.onBackground),
        secondary = themeColor(light.secondary, dark.secondary),
        primary = themeColor(light.primary, dark.primary),
        onPrimary = themeColor(light.onPrimary, dark.onPrimary),
        control = themeColor(light.control, dark.control),
        disabledControl = themeColor(light.disabledControl, dark.disabledControl),
    )
}

private data class WidgetPalette(
    val background: Long,
    val onBackground: Long,
    val secondary: Long,
    val primary: Long,
    val onPrimary: Long,
    val control: Long,
    val disabledControl: Long,
)

private fun PriamWidgetPresentation.themeColor(light: Long, dark: Long): GlanceColorProvider = when (themeMode) {
    ThemeMode.SYSTEM -> ColorProvider(day = Color(light), night = Color(dark))
    ThemeMode.LIGHT -> androidx.glance.unit.ColorProvider(Color(light))
    ThemeMode.DARK -> androidx.glance.unit.ColorProvider(Color(dark))
}
