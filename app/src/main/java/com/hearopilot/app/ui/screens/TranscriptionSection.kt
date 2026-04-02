package com.hearopilot.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.domain.model.TranscriptionSegment

/**
 * Transcription display.
 * - Auto-scroll to latest segment when content is present
 * - Minimal empty state when no segments yet (no hero, just centered icon + text)
 */
@Composable
fun TranscriptionSection(
    segments: List<TranscriptionSegment>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) {
            listState.animateScrollToItem(segments.size - 1)
        }
    }

    Box(modifier = modifier) {
        if (segments.isEmpty()) {
            TranscriptionEmptyState(
                isRecording = isRecording,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(segments) { segment ->
                    TranscriptionItem(segment)
                }
            }
        }
    }
}

/**
 * Empty state: centered icon + text, no gradient hero.
 *
 * Idle:      static mic icon, "Press Record to start" hint.
 * Recording: mic icon pulses, text changes to "Listening…".
 */
@Composable
private fun TranscriptionEmptyState(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse animation — always created, only applied when recording.
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )
    val iconScale = if (isRecording) pulseScale else 1f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppIcons.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
            modifier = Modifier
                .size(56.dp)
                .scale(iconScale)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isRecording) stringResource(R.string.transcription_listening)
                   else stringResource(R.string.transcription_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        if (!isRecording) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.transcription_press_record),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

@Composable
private fun TranscriptionItem(segment: TranscriptionSegment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (segment.isComplete) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        },
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = segment.text,
                style = MaterialTheme.typography.transcription,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (segment.isComplete) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = AppIcons.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(14.dp))
                    Text(text = stringResource(R.string.transcription_complete), style = MaterialTheme.typography.labelSmall, color = Success, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
