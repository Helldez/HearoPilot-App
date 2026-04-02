package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SearchMatchSource
import com.hearopilot.app.domain.model.SearchResult
import com.hearopilot.app.presentation.search.SearchViewModel
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen search overlay for finding past sessions by transcription text,
 * AI insight content, or session name.
 *
 * @param onNavigateBack     Navigate back to the calling screen.
 * @param onNavigateToSession Navigate to [SessionDetailsScreen] for the given session ID and optional item highlight ID.
 * @param viewModel          Injected [SearchViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSession: (sessionId: String, highlightId: String?, initialTab: Int) -> Unit = { _, _, _ -> },
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(modifier = Modifier.background(BrandPurpleDark)) {
                TopAppBar(
                    title = {
                        SearchBarInput(
                            query = uiState.query,
                            onQueryChange = viewModel::onQueryChange,
                            onClear = viewModel::clearQuery,
                            focusRequester = focusRequester
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        actionIconContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            when {
                uiState.query.length < 2 -> {
                    // Idle state — prompt the user
                    EmptySearchPrompt(
                        message = stringResource(R.string.search_empty_prompt),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.hasSearched && uiState.results.isEmpty() -> {
                    // Searched but nothing found
                    EmptySearchPrompt(
                        message = stringResource(R.string.search_no_results),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(uiState.results, key = { idx, result -> "${result.sessionId}-${result.matchSource}-$idx" }) { _, result ->
                            SearchResultCard(
                                result = result,
                                onClick = {
                                    val tab = if (result.matchSource == SearchMatchSource.INSIGHT) 1 else 0
                                    onNavigateToSession(result.sessionId, result.highlightId, tab)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Internal composables ───────────────────────────────────────────────────────

@Composable
private fun SearchBarInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                text = stringResource(R.string.search_hint),
                color = Color.White.copy(alpha = 0.6f)
            )
        },
        singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = result.mode.accentColor()
    val dateLabel = remember(result.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(result.createdAt))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left mode-color accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Min)
                    .background(accentColor)
                    .defaultMinSize(minHeight = 72.dp)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .weight(1f)
            ) {
                // Header row: session name + date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.sessionName ?: result.mode.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Snippet with highlighted match
                Text(
                    text = buildHighlightedSnippet(result.snippet, result.matchQuery),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Source chip
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = result.matchSource.label(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMatchSource.label(): String = when (this) {
    SearchMatchSource.TRANSCRIPTION -> stringResource(R.string.search_source_transcription)
    SearchMatchSource.INSIGHT -> stringResource(R.string.search_source_insight)
    SearchMatchSource.SESSION_NAME -> stringResource(R.string.search_source_session_name)
}

@Composable
private fun RecordingMode.label(): String = when (this) {
    RecordingMode.SIMPLE_LISTENING -> stringResource(R.string.mode_simple_listening)
    RecordingMode.SHORT_MEETING -> stringResource(R.string.mode_short_meeting)
    RecordingMode.LONG_MEETING -> stringResource(R.string.mode_long_meeting)
    RecordingMode.REAL_TIME_TRANSLATION -> stringResource(R.string.mode_translation_live)
}

private fun RecordingMode.accentColor(): Color = when (this) {
    RecordingMode.SIMPLE_LISTENING -> ModeSkyBlueTint
    RecordingMode.SHORT_MEETING -> BrandPrimary
    RecordingMode.LONG_MEETING -> ModeAmberTint
    RecordingMode.REAL_TIME_TRANSLATION -> ModeEmeraldTint
}

@Composable
private fun EmptySearchPrompt(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = AppIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

/**
 * Build an [androidx.compose.ui.text.AnnotatedString] that bolds every occurrence
 * of [query] (case-insensitive) inside [snippet].
 */
private fun buildHighlightedSnippet(snippet: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(snippet)
        return@buildAnnotatedString
    }
    var cursor = 0
    val lower = snippet.lowercase()
    val lowerQuery = query.lowercase()
    while (cursor < snippet.length) {
        val idx = lower.indexOf(lowerQuery, cursor)
        if (idx < 0) {
            append(snippet.substring(cursor))
            break
        }
        append(snippet.substring(cursor, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(snippet.substring(idx, idx + query.length))
        }
        cursor = idx + query.length
    }
}
