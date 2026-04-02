package com.hearopilot.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hearopilot.app.ui.ui.theme.BrandPurpleDark

/**
 * Hero icon for download screens.
 *
 * Renders a Material icon centered inside an 88 dp gradient circle, matching the
 * same visual language as the gradient FABs used elsewhere in the app.
 *
 * @param icon         The icon to display.
 * @param containerBrush Gradient brush applied to the circle background.
 * @param modifier     Optional modifier.
 * @param animated     When true, the circle pulses with a slow breathe animation —
 *                     use this for the active Downloading state only.
 */
@Composable
fun DownloadIconHero(
    icon: ImageVector,
    containerBrush: Brush,
    modifier: Modifier = Modifier,
    animated: Boolean = false
) {
    // Always create the transition; only apply the animated value when requested.
    // This avoids a conditional composable call, which would violate Compose rules.
    val infiniteTransition = rememberInfiniteTransition(label = "hero_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_scale"
    )
    val scale = if (animated) pulseScale else 1f

    Box(
        modifier = modifier
            .size(88.dp)
            .scale(scale)
            .background(brush = containerBrush, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

/**
 * Animated HearoPilot logo icon for model download screens.
 *
 * Renders the full logo (ears, top arc, purple circle) with the white smile
 * rotating 360° continuously inside the circle. Intended for the Downloading
 * state where it replaces the generic CloudDownload icon.
 *
 * Coordinate system: SVG viewBox 895×721, transform translate(-1823,-867) already applied.
 * Purple circle center: (447.5, 386). Smile pivot: same point.
 */
@Composable
fun HearoPilotDownloadIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "smile_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "spin"
    )

    Canvas(modifier = modifier) {
        val s = size.width / 895f

        // Left ear
        drawPath(
            path = Path().apply {
                moveTo(0f, 410.5f * s)
                cubicTo(0f, 352.79f * s, 39.17f * s, 306f * s, 87.5f * s, 306f * s)
                cubicTo(135.82f * s, 306f * s, 175f * s, 352.79f * s, 175f * s, 410.5f * s)
                cubicTo(175f * s, 468.21f * s, 135.82f * s, 515f * s, 87.5f * s, 515f * s)
                cubicTo(39.17f * s, 515f * s, 0f, 468.21f * s, 0f, 410.5f * s)
                close()
            },
            color = Color.White
        )

        // Right ear
        drawPath(
            path = Path().apply {
                moveTo(720f * s, 410.5f * s)
                cubicTo(720f * s, 352.79f * s, 759.18f * s, 306f * s, 807.5f * s, 306f * s)
                cubicTo(855.82f * s, 306f * s, 895f * s, 352.79f * s, 895f * s, 410.5f * s)
                cubicTo(895f * s, 468.21f * s, 855.82f * s, 515f * s, 807.5f * s, 515f * s)
                cubicTo(759.18f * s, 515f * s, 720f * s, 468.21f * s, 720f * s, 410.5f * s)
                close()
            },
            color = Color.White
        )

        // Top arc
        drawPath(
            path = Path().apply {
                moveTo(73f * s, 374f * s)
                cubicTo(73f * s, 167.45f * s, 240.45f * s, 0f, 447f * s, 0f)
                cubicTo(653.06f * s, 0f, 820.3f * s, 166.68f * s, 821f * s, 372.74f * s)
                lineTo(447f * s, 374f * s)
                close()
            },
            color = Color.White
        )

        // Purple circle
        drawPath(
            path = Path().apply {
                moveTo(112f * s, 386f * s)
                cubicTo(112f * s, 200.98f * s, 262.21f * s, 51f * s, 447.5f * s, 51f * s)
                cubicTo(632.79f * s, 51f * s, 783f * s, 200.98f * s, 783f * s, 386f * s)
                cubicTo(783f * s, 571.02f * s, 632.79f * s, 721f * s, 447.5f * s, 721f * s)
                cubicTo(262.21f * s, 721f * s, 112f * s, 571.02f * s, 112f * s, 386f * s)
                close()
            },
            color = BrandPurpleDark
        )

        // Rotating white smile
        rotate(degrees = rotation, pivot = Offset(447.5f * s, 386f * s)) {
            drawPath(
                path = Path().apply {
                    moveTo(606f * s, 507.5f * s)
                    cubicTo(606f * s, 558.03f * s, 535.04f * s, 599f * s, 447.5f * s, 599f * s)
                    cubicTo(360.92f * s, 599f * s, 290.36f * s, 558.89f * s, 289.02f * s, 508.92f * s)
                    lineTo(447.5f * s, 507.5f * s)
                    close()
                },
                color = Color.White
            )
        }
    }
}

/**
 * Overload accepting a solid color container — used for success and error states
 * where a semantic color (green / red) conveys meaning more clearly than a gradient.
 */
@Composable
fun DownloadIconHero(
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(88.dp)
            .background(color = containerColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}
