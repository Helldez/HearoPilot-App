package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.material3.RichText
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.ui.components.GradientButton
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.ui.components.ShimmerInsightCard

/**
 * Minimalist insights display
 * - Flat design with subtle accents
 * - Clear visual hierarchy
 * - Brand primary accent color
 * - Formatted text with markdown-like rendering
 */
@Composable
fun InsightsSection(
    insights: List<LlmInsight>,
    segments: List<TranscriptionSegment>,
    llmStatus: String,
    isLlmEnabled: Boolean = true,
    isLlmAvailable: Boolean = true,
    isFinalizingSession: Boolean = false,
    isRecording: Boolean = false,
    insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    batchProgress: BatchInsightProgress = BatchInsightProgress.Idle,
    onDownloadLlm: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (!isLlmEnabled) {
            // LLM disabled by the user in Settings — show informational message, not download prompt
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.insights_disabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.insights_disabled_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (!isLlmAvailable) {
            // LLM enabled but model not yet downloaded — prompt the user to download
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top spacer to push content toward center when space allows
                Spacer(modifier = Modifier.weight(1f, fill = false))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,

                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.insights_download_prompt),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        GradientButton(
                            text = stringResource(R.string.insights_download_button),
                            onClick = onDownloadLlm,
                            modifier = Modifier.fillMaxWidth(),
                            // Downloading the LLM while STT is active would load ~1 GB into RAM
                            // on top of the already-loaded STT model, causing OOM / freeze.
                            enabled = !isRecording
                        )
                    }
                }

                // Bottom spacer to push content toward center when space allows
                Spacer(modifier = Modifier.weight(1f, fill = false))
            }
        } else if (insightStrategy == InsightStrategy.END_OF_SESSION && isRecording && insights.isEmpty()) {
            // END_OF_SESSION mode: recording in progress — show info card.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Summarize,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.batch_insights_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Light,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (isFinalizingSession && batchProgress !is BatchInsightProgress.Idle && batchProgress !is BatchInsightProgress.Complete) {
            // Batch pipeline running — show progress UI.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (val progress = batchProgress) {
                        is BatchInsightProgress.Mapping -> {
                            Text(
                                text = stringResource(
                                    R.string.batch_processing_chunk,
                                    progress.currentChunk,
                                    progress.totalChunks
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress.currentChunk.toFloat() / progress.totalChunks },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is BatchInsightProgress.Reducing -> {
                            Text(
                                text = stringResource(R.string.batch_merging_insights),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is BatchInsightProgress.Error -> {
                            Icon(
                                imageVector = AppIcons.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = progress.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        else -> {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        } else if (insights.isEmpty() && isFinalizingSession) {
            // Session just ended: no prior insights but the final one is being generated.
            // Show a single shimmer card so the user sees immediate feedback.
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ShimmerInsightCard() }
            }
        } else if (insights.isEmpty()) {
            // Empty state (LLM available but no insights yet)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.insights_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Light,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.insights_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Insights list. A shimmer card at the bottom signals the final insight is in flight.
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(insights) { insight ->
                    InsightItem(insight, segments)
                }
                if (isFinalizingSession) {
                    item { ShimmerInsightCard(modifier = Modifier.padding(horizontal = 0.dp)) }
                }
            }
        }
    }
}

@Composable
private fun InsightItem(insight: LlmInsight, segments: List<TranscriptionSegment>) {
    // Create a map for fast lookup of segments by ID
    val segmentMap = remember(segments) {
        segments.associateBy { it.id }
    }

    // Get the source segments for this insight
    val sourceSegments = remember(insight.sourceSegmentIds, segmentMap) {
        insight.sourceSegmentIds.mapNotNull { id -> segmentMap[id] }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Insight indicator with title (if present)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = insight.title ?: stringResource(R.string.insight_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Formatted insight content (always visible)
            FormattedInsightText(insight.content)

            // Tasks (if present)
            insight.tasks?.let { tasksJson ->
                Spacer(modifier = Modifier.height(12.dp))
                TasksList(tasksJson)
            }

            // Source transcription — collapsed by default; tap the header to expand.
            if (insight.sourceSegmentIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                var sourceExpanded by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Clickable header row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sourceExpanded = !sourceExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.source_transcription),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (sourceExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                                contentDescription = if (sourceExpanded)
                                    stringResource(R.string.collapse)
                                else
                                    stringResource(R.string.expand),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Collapsible content
                        if (sourceExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (sourceSegments.isNotEmpty()) {
                                Text(
                                    text = sourceSegments.joinToString(" ") { it.text },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.segments_not_found),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksList(tasksJson: String) {
    // Parse JSON outside composable to avoid try-catch around composables
    val tasks = remember(tasksJson) {
        try {
            val tasksArray = org.json.JSONArray(tasksJson)
            List(tasksArray.length()) { i ->
                tasksArray.optString(i)
            }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    if (tasks.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_items),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }

                tasks.forEach { task ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\u2022",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = task,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders insight text with full Markdown support using compose-richtext.
 * Supports headers, lists, bold, italic, code blocks, and more.
 */
@Composable
internal fun FormattedInsightText(content: String) {
    RichText(
        modifier = Modifier.fillMaxWidth()
    ) {
        Markdown(content)
    }
}
