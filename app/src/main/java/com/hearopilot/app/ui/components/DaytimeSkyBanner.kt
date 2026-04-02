package com.hearopilot.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/**
 * Flat-design sky banner that reflects the current local time of day.
 *
 * Drawing order (back to front):
 *   sky gradient → stars → back hills → celestial body → front hills → bottom scrim
 *
 * This puts the sun/moon between the two hill layers: it peeks above the distant
 * hills but is hidden behind the foreground hills at dawn and dusk.
 *
 * Updates once per minute via LaunchedEffect. On return from background the
 * composable is recomposed from scratch, so it always shows the correct time.
 *
 * Sky palette is harmonised with the brand purple family:
 *   night   → deep indigo  → near-black  (matches DownloadImmersiveNavy)
 *   dawn    → BrandPurpleDark → warm orange
 *   morning → soft blue    → pale sky
 *   afternoon → ModeSkyBlueDark → ModeSkyBlueLight
 *   dusk    → ModeAmberDark → BrandPurpleDark
 */
@Composable
fun DaytimeSkyBanner(modifier: Modifier = Modifier) {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val cal = Calendar.getInstance()
            hour = cal.get(Calendar.HOUR_OF_DAY)
            minute = cal.get(Calendar.MINUTE)
        }
    }

    val totalMinutes = hour * 60 + minute

    val (skyTop, skyBottom) = remember(hour, minute) {
        when {
            totalMinutes < 5 * 60 || totalMinutes >= 21 * 60 ->
                Color(0xFF1A0E4A) to Color(0xFF0A0A1E)   // night: deep indigo → near-black
            totalMinutes < 8 * 60 ->
                Color(0xFF5636C4) to Color(0xFFE07B39)   // dawn: BrandPurpleDark → warm orange
            totalMinutes < 12 * 60 ->
                Color(0xFF60A5FA) to Color(0xFFBAE6FD)   // morning: soft blue → pale sky
            totalMinutes < 17 * 60 ->
                Color(0xFF0284C7) to Color(0xFF38BDF8)   // afternoon: ModeSkyBlueDark → ModeSkyBlueLight
            else ->
                Color(0xFFD97706) to Color(0xFF5636C4)   // dusk: ModeAmberDark → BrandPurpleDark
        }
    }

    val isNight = totalMinutes < 6 * 60 || totalMinutes >= 20 * 60

    // Normalised arc position t ∈ [0, 1]: 0 = rising, 0.5 = zenith, 1 = setting
    val celestialT = remember(hour, minute) {
        if (!isNight) {
            // Sun visible 06:00 – 20:00 (14 h window)
            ((totalMinutes - 6 * 60).coerceIn(0, 14 * 60)).toFloat() / (14 * 60)
        } else {
            // Moon visible 20:00 – 06:00 (10 h window, wraps midnight)
            val adj = if (totalMinutes < 6 * 60) totalMinutes + 24 * 60 else totalMinutes
            ((adj - 20 * 60).coerceIn(0, 10 * 60)).toFloat() / (10 * 60)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Sky gradient
        drawRect(brush = Brush.verticalGradient(listOf(skyTop, skyBottom), 0f, h))

        // 2. Stars — night only, deterministic positions via fixed seed
        if (isNight) {
            val rng = java.util.Random(0xDEADBEEF)
            repeat(45) {
                val sx = rng.nextFloat() * w
                val sy = rng.nextFloat() * h * 0.70f
                val sr = rng.nextFloat() * 1.4f + 0.5f
                drawCircle(Color.White.copy(alpha = 0.65f), radius = sr, center = Offset(sx, sy))
            }
        }

        // 3. Celestial body on a parabolic arc.
        // Arc: starts/ends at horizonY (hill horizon line), peaks at zenithY.
        val cx = w * 0.08f + (w * 0.84f) * celestialT
        val horizonY = h * 0.72f  // matches left-edge of front hill path
        val zenithY  = h * 0.12f  // highest point of arc (noon / midnight)
        val cy = horizonY - (horizonY - zenithY) * sin(celestialT * PI).toFloat()

        if (!isNight) {
            // Sun: amber glow rings + disk
            drawCircle(Color(0xFFFBBF24).copy(alpha = 0.18f), 30.dp.toPx(), Offset(cx, cy))
            drawCircle(Color(0xFFFBBF24).copy(alpha = 0.10f), 40.dp.toPx(), Offset(cx, cy))
            drawCircle(Color(0xFFFCD34D), 13.dp.toPx(), Offset(cx, cy))
        } else {
            // Moon: subtle glow + white disk + small crater
            drawCircle(Color(0xFFE7E0EC).copy(alpha = 0.12f), 22.dp.toPx(), Offset(cx, cy))
            drawCircle(Color(0xFFE7E0EC), 11.dp.toPx(), Offset(cx, cy))
            drawCircle(Color(0xFFCAC4D0), 3.5.dp.toPx(), Offset(cx + 4.dp.toPx(), cy - 3.dp.toPx()))
        }

        // 4. Back hills — BrandPurpleDark, low alpha (distant layer)
        val backHill = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.60f)
            quadraticTo(w * 0.28f, h * 0.30f, w * 0.52f, h * 0.52f)
            quadraticTo(w * 0.76f, h * 0.70f, w, h * 0.44f)
            lineTo(w, h)
            close()
        }
        drawPath(backHill, Color(0xFF5636C4).copy(alpha = 0.30f))

        // 5. Front hills — BrandPurpleDark, fully opaque (foreground layer)
        //    Drawn after the celestial body so it completely hides the body near the horizon.
        val frontHill = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.76f)
            quadraticTo(w * 0.18f, h * 0.56f, w * 0.38f, h * 0.70f)
            quadraticTo(w * 0.58f, h * 0.84f, w * 0.74f, h * 0.65f)
            quadraticTo(w * 0.87f, h * 0.52f, w, h * 0.68f)
            lineTo(w, h)
            close()
        }
        drawPath(frontHill, Color(0xFF5636C4))

        // 6. Bottom scrim — ensures date/KPI text is readable on any sky colour
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                startY = h * 0.45f,
                endY = h
            )
        )
    }
}
