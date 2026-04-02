package com.hearopilot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val DOT_SIZE_ACTIVE = 10.dp
private val DOT_SIZE_INACTIVE = 8.dp
private val DOT_SPACING = 8.dp
private const val COLOR_ANIM_DURATION_MS = 250

/**
 * Classic onboarding page-dot indicator.
 *
 * The current page dot is filled with [MaterialTheme.colorScheme.primary];
 * other dots are outlined with [MaterialTheme.colorScheme.onSurfaceVariant].
 *
 * @param totalPages Total number of onboarding pages.
 * @param currentPage Zero-based index of the active page.
 * @param modifier Optional modifier.
 */
@Composable
fun OnboardingPageIndicator(
    totalPages: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isActive = index == currentPage

            val dotColor by animateColorAsState(
                targetValue = if (isActive) primary else inactive,
                animationSpec = tween(COLOR_ANIM_DURATION_MS),
                label = "dot_color_$index"
            )
            val dotSize by animateDpAsState(
                targetValue = if (isActive) DOT_SIZE_ACTIVE else DOT_SIZE_INACTIVE,
                animationSpec = tween(COLOR_ANIM_DURATION_MS),
                label = "dot_size_$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .then(
                        if (isActive) {
                            Modifier.background(dotColor)
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.5.dp, dotColor, CircleShape)
                        }
                    )
            )
        }
    }
}
