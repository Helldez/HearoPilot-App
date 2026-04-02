package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearopilot.app.ui.R
import androidx.compose.ui.graphics.Brush
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.TranscriptionSession
import com.hearopilot.app.presentation.sessions.SessionsViewModel
import com.hearopilot.app.ui.components.DaytimeSkyBanner
import com.hearopilot.app.ui.components.ShimmerSessionList
import com.hearopilot.app.ui.icons.AppIcons
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sessions list screen - Home screen showing all transcription sessions.
 *
 * @param onNavigateToSettings Navigate to settings screen
 * @param onNavigateToSessionDetails Navigate to session details with session ID
 * @param onNavigateToRecording Navigate to recording screen with session ID
 * @param viewModel SessionsViewModel for managing session list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSessionDetails: (String) -> Unit = {},
    onNavigateToRecording: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: SessionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(modifier = Modifier.background(BrandPurpleDark)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 20.sp,
                                fontFamily = SpaceGroteskFont,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.sessions_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(AppIcons.Search, contentDescription = stringResource(R.string.search_hint))
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(AppIcons.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        floatingActionButton = {
            // navigationBarsPadding wrapper: contentWindowInsets=WindowInsets(0) removes the
            // Scaffold's automatic FAB offset, so we expand the FAB bounding box by the nav bar
            // height so the Scaffold positions the circle above the nav bar.
            Box(modifier = Modifier.navigationBarsPadding()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = PrimaryGradientVertical,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { viewModel.showNewSessionDialog() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = stringResource(R.string.new_transcription),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    ShimmerSessionList(
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                uiState.error != null -> {
                    ErrorMessage(
                        message = stringResource(R.string.error_delete_session, uiState.error!!),
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.sessions.isEmpty() -> {
                    EmptyState()
                }

                else -> {
                    SessionsList(
                        sessions = uiState.sessions,
                        totalDataSizeBytes = uiState.totalDataSizeBytes,
                        onSessionClick = onNavigateToSessionDetails
                    )
                }
            }
        }

        // New Session Dialog
        if (uiState.showNewSessionDialog) {
            NewSessionDialog(
                onDismiss = { viewModel.hideNewSessionDialog() },
                onConfirm = { name, mode, outputLang, strategy, topic ->
                    viewModel.createSession(name, mode, outputLang, strategy, topic) { sessionId ->
                        onNavigateToRecording(sessionId)
                    }
                },
                settings = uiState.settings
            )
        }
    }
}

/**
 * List of transcription sessions with a date/stats header.
 */
@Composable
private fun SessionsList(
    sessions: List<TranscriptionSession>,
    totalDataSizeBytes: Long,
    onSessionClick: (String) -> Unit
) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp + navBarPadding), // space for FAB + nav bar
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Date + stats header (no purple background)
        item {
            SessionsHeader(sessions = sessions, totalDataSizeBytes = totalDataSizeBytes)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                onClick = { onSessionClick(session.id) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Header block showing today's date and a stats row beneath it.
 * The daytime sky banner spans from the top to approximately half the KPI row height.
 */
@Composable
private fun SessionsHeader(sessions: List<TranscriptionSession>, totalDataSizeBytes: Long) {
    val today = remember {
        java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
    val dateStr = remember {
        java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
    val totalMillis = remember(sessions) {
        sessions.sumOf { it.durationMs }
    }

    // Sky height covers the full header: top padding + date + gap + KPI row + bottom padding
    val skyHeight = 138.dp

    Box(modifier = Modifier.fillMaxWidth()) {
        DaytimeSkyBanner(
            modifier = Modifier
                .fillMaxWidth()
                .height(skyHeight)
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: day name + full date — white text on sky
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = today,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.80f)
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Row 2: stat blocks — glass pill, transparent background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.13f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SessionStatBlock(
                    value = "${sessions.size}",
                    label = stringResource(R.string.header_stat_sessions),
                    modifier = Modifier.weight(1f),
                    valueColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.72f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(Color.White.copy(alpha = 0.30f))
                )
                SessionStatBlock(
                    value = formatHeaderDuration(totalMillis),
                    label = stringResource(R.string.header_stat_duration),
                    modifier = Modifier.weight(1f),
                    valueColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.72f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(Color.White.copy(alpha = 0.30f))
                )
                SessionStatBlock(
                    value = formatDataSize(totalDataSizeBytes),
                    label = stringResource(R.string.header_stat_data),
                    modifier = Modifier.weight(1f),
                    valueColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.72f)
                )
            }
        }
    }
}

/**
 * Card representing a single session in the list.
 *
 * Design: borderless surface card with a 4 dp left accent bar tinted
 * with the mode colour. Delete is accessible only inside the session detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(
    session: TranscriptionSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left mode-colour accent bar — requires IntrinsicSize.Min on the Row
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        brush = recordingModeGradient(session.mode),
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = session.name ?: stringResource(R.string.session_unnamed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (session.name != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Date row
                Text(
                    text = formatTimestampComposable(session.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Mode + duration row — always fits on one line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(recordingModeLabel(session.mode)),
                        style = MaterialTheme.typography.labelSmall,
                        color = recordingModeTint(session.mode),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (session.durationMs > 0L) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatCardDuration(session.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Vertical overlap between the hero and the steps sheet, in dp. */
/**
 * Empty state — full-screen immersive gradient.
 *
 * Single surface (no hero/sheet split): title + subtitle at top,
 * divider, then all four feature rows (Privacy last for visual balance)
 * with white icons and text on the dark gradient background.
 */
@Composable
private fun EmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DownloadImmersiveGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(top = 32.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Title + subtitle ─────────────────────────────────────────
            Text(
                text = stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.empty_state_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.70f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

            Spacer(modifier = Modifier.height(32.dp))

            // ── Step rows — white on gradient ────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                ImmersiveStepRow(
                    icon = AppIcons.Add,
                    title = stringResource(R.string.empty_state_step1_title),
                    description = stringResource(R.string.empty_state_step1_desc)
                )
                ImmersiveStepRow(
                    icon = AppIcons.Mic,
                    title = stringResource(R.string.empty_state_step2_title),
                    description = stringResource(R.string.empty_state_step2_desc)
                )
                ImmersiveStepRow(
                    icon = AppIcons.AutoAwesome,
                    title = stringResource(R.string.empty_state_step3_title),
                    description = stringResource(R.string.empty_state_step3_desc)
                )
                ImmersiveStepRow(
                    icon = AppIcons.Lock,
                    title = stringResource(R.string.empty_state_privacy_title),
                    description = stringResource(R.string.empty_state_privacy)
                )
            }
        }
    }
}

/**
 * Step row styled for a dark/gradient background:
 * white semi-transparent icon circle + white title and description.
 */
@Composable
private fun ImmersiveStepRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.70f)
            )
        }
    }
}

/**
 * Stat block for the sessions header: value + label stacked, centered in its slot.
 */
@Composable
private fun SessionStatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

/**
 * Format a session duration in milliseconds for the session card (e.g. "2m 34s", "1h 23m").
 */
private fun formatCardDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Format a total duration in milliseconds to a short human-readable string.
 */
private fun formatHeaderDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

/**
 * Format a byte count to a short human-readable size string (KB or MB).
 * Values below 1 KB are shown as "< 1 KB".
 */
private fun formatDataSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000L -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
        bytes >= 1_000L     -> "${bytes / 1_000} KB"
        else                -> "< 1 KB"
    }
}

/**
 * Error message display.
 */
@Composable
private fun ErrorMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onDismiss) {
            Text(stringResource(R.string.dismiss))
        }
    }
}

/**
 * Maps a [RecordingMode] to its short display string resource ID for use in session cards.
 */
private fun recordingModeLabel(mode: RecordingMode): Int = when (mode) {
    RecordingMode.SIMPLE_LISTENING      -> R.string.mode_simple_listening
    RecordingMode.SHORT_MEETING         -> R.string.mode_short_meeting
    RecordingMode.LONG_MEETING          -> R.string.mode_long_meeting
    RecordingMode.REAL_TIME_TRANSLATION -> R.string.mode_translation_live
}

/**
 * Maps a [RecordingMode] to the gradient brush used for its icon container background.
 */
private fun recordingModeGradient(mode: RecordingMode): Brush = when (mode) {
    RecordingMode.SIMPLE_LISTENING      -> ModeSimpleListeningGradient
    RecordingMode.SHORT_MEETING         -> ModeShortMeetingGradient
    RecordingMode.LONG_MEETING          -> ModeLongMeetingGradient
    RecordingMode.REAL_TIME_TRANSLATION -> ModeTranslationGradient
}

/**
 * Maps a [RecordingMode] to the flat tint color used for its label text.
 */
private fun recordingModeTint(mode: RecordingMode): Color = when (mode) {
    RecordingMode.SIMPLE_LISTENING      -> ModeSkyBlueTint
    RecordingMode.SHORT_MEETING         -> ModeShortMeetingTint
    RecordingMode.LONG_MEETING          -> ModeAmberTint
    RecordingMode.REAL_TIME_TRANSLATION -> ModeEmeraldTint
}

/**
 * Format timestamp to readable date/time.
 */
@Composable
private fun formatTimestampComposable(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> stringResource(R.string.time_just_now)
        diff < 3_600_000 -> {
            val minutes = diff / 60_000
            stringResource(R.string.time_minutes_ago, minutes)
        }
        diff < 86_400_000 -> {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeString = dateFormat.format(Date(timestamp))
            stringResource(R.string.time_today, timeString)
        }
        diff < 172_800_000 -> {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeString = dateFormat.format(Date(timestamp))
            stringResource(R.string.time_yesterday, timeString)
        }
        else -> {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}
