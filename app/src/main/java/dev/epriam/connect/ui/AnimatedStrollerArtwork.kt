package dev.epriam.connect.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.epriam.connect.protocol.RockingIntensity

@Composable
internal fun AnimatedStrollerArtwork(
    @DrawableRes heroImage: Int,
    darkTheme: Boolean,
    intensity: RockingIntensity,
    modifier: Modifier = Modifier,
) {
    val cycleDurationMillis = intensity.cycleDurationMillis
    val transition = rememberInfiniteTransition(label = "stroller rocking")
    val motion by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleDurationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stroller position",
    )
    val tint = if (darkTheme) {
        ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
    } else {
        null
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationX = ROCKING_TRAVEL_DP.dp.toPx() * motion
            rotationZ = ROCKING_TILT_DEGREES * motion
            transformOrigin = TransformOrigin(0.5f, 0.92f)
        },
    ) {
        StrollerLayer(
            heroImage = heroImage,
            tint = tint,
            modifier = Modifier.fillMaxSize().clipOutsideWheels(),
        )
        val wheelRotation = WHEEL_ROTATION_DEGREES * motion
        RotatingWheelLayer(heroImage, tint, REAR_WHEEL, rotation = wheelRotation)
        RotatingWheelLayer(heroImage, tint, FRONT_WHEEL, rotation = wheelRotation)
    }
}

@Composable
private fun RotatingWheelLayer(
    @DrawableRes heroImage: Int,
    tint: ColorFilter?,
    wheel: WheelRegion,
    rotation: Float,
) {
    StrollerLayer(
        heroImage = heroImage,
        tint = tint,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(
                    pivotFractionX = wheel.center.x / SOURCE_WIDTH,
                    pivotFractionY = wheel.center.y / SOURCE_HEIGHT,
                )
                rotationZ = rotation
            }
            .clipToWheel(wheel),
    )
}

@Composable
private fun StrollerLayer(
    @DrawableRes heroImage: Int,
    tint: ColorFilter?,
    modifier: Modifier,
) {
    Image(
        painter = painterResource(heroImage),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = tint,
    )
}

private fun Modifier.clipOutsideWheels() = drawWithContent {
    val wheelCutouts = Path().apply {
        addOval(REAR_WHEEL.boundsIn(size.width, size.height))
        addOval(FRONT_WHEEL.boundsIn(size.width, size.height))
    }
    clipPath(wheelCutouts, clipOp = ClipOp.Difference) {
        this@drawWithContent.drawContent()
    }
}

private fun Modifier.clipToWheel(wheel: WheelRegion) = drawWithContent {
    val wheelPath = Path().apply { addOval(wheel.boundsIn(size.width, size.height)) }
    clipPath(wheelPath) {
        this@drawWithContent.drawContent()
    }
}

private fun WheelRegion.boundsIn(width: Float, height: Float): Rect {
    val scale = minOf(width / SOURCE_WIDTH, height / SOURCE_HEIGHT)
    val imageLeft = (width - SOURCE_WIDTH * scale) / 2f
    val imageTop = (height - SOURCE_HEIGHT * scale) / 2f
    val scaledCenter = Offset(imageLeft + center.x * scale, imageTop + center.y * scale)
    return Rect(center = scaledCenter, radius = radius * scale)
}

private data class WheelRegion(val center: Offset, val radius: Float)

private val RockingIntensity.cycleDurationMillis: Int
    get() = when (this) {
        RockingIntensity.LOW -> 1_400
        RockingIntensity.MEDIUM -> 950
        RockingIntensity.HIGH -> 650
    }

private val REAR_WHEEL = WheelRegion(center = Offset(469f, 735f), radius = 108f)
private val FRONT_WHEEL = WheelRegion(center = Offset(812f, 766f), radius = 75f)
private const val ROCKING_TRAVEL_DP = 5f
private const val ROCKING_TILT_DEGREES = 1.2f
private const val WHEEL_ROTATION_DEGREES = 18f
private const val SOURCE_WIDTH = 1_200f
private const val SOURCE_HEIGHT = 900f
