package com.hearopilot.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.presentation.main.MainViewModel
import com.hearopilot.app.ui.components.ShimmerInsightCard
import java.util.concurrent.TimeUnit

/**
 * Formats milliseconds to mm:ss or h:mm:ss format for recording timer display.
 * Shows hours only when recording exceeds 60 minutes.
 */
private fun formatRecordingTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Libellula - Mobile-First Design
 * - Tabs instead of side-by-side columns
 * - Floating Action Button for Record
 * - Full-width content area
 *
 * @param onNavigateToSettings Callback to navigate to Settings screen
 * @param onNavigateBack Callback to navigate back to sessions list
 * @param onNavigateToLlmDownload Callback to navigate to LLM download screen
 * @param viewModel MainViewModel for managing app state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToLlmDownload: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val llmDownloadState by viewModel.llmDownloadState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Intercept hardware back button while recording is active.
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    // Block back navigation while recording or while the session is being finalized.
    BackHandler(enabled = uiState.isRecording || uiState.isFinalizingSession) {
        if (!uiState.isFinalizingSession) {
            showStopConfirmDialog = true
        }
        // When finalizing: silently consume the back press — user must wait.
    }

    // Request RECORD_AUDIO (critical) + POST_NOTIFICATIONS (Android 13+, best-effort).
    // POST_NOTIFICATIONS is required for the foreground service notification to appear.
    // Without it on API 33+, the notification is silently suppressed.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // If RECORD_AUDIO is absent from results it was not requested (already granted).
        // Fall back to a runtime check rather than treating absence as denial.
        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED)
        if (micGranted) {
            viewModel.initialize()
        } else {
            viewModel.onMicPermissionDenied()
        }
        // POST_NOTIFICATIONS denial is non-blocking: recording works, notification won't show.
    }

    LaunchedEffect(Unit) {
        val micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val permissionsToRequest = buildList {
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            viewModel.initialize()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Confirmation dialog shown when user presses back (hardware or nav icon) while recording.
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text(stringResource(R.string.stop_recording_confirm_title)) },
            text = { Text(stringResource(R.string.stop_recording_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmDialog = false
                    viewModel.stopStreaming()
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.stop_and_go_back))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show dialog and navigate back when microphone permission is denied
    if (uiState.isMicPermissionDenied) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            title = { Text(stringResource(R.string.permission_mic_required)) },
            text = { Text(stringResource(R.string.permission_mic_denied)) },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    Scaffold(
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (uiState.isRecording) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color.Red, CircleShape)
                                    )
                                }
                                Text(
                                    text = if (uiState.isRecording) {
                                        formatRecordingTime(uiState.recordingDurationMillis)
                                    } else {
                                        stringResource(R.string.recording_session)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = if (uiState.isRecording) 1f else 0.75f),
                                    fontWeight = if (uiState.isRecording) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when {
                                    uiState.isFinalizingSession -> { /* blocked: session is finalizing */ }
                                    uiState.isRecording -> showStopConfirmDialog = true
                                    else -> onNavigateBack()
                                }
                            },
                            enabled = !uiState.isFinalizingSession
                        ) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.back_to_sessions)
                            )
                        }
                    },
                    actions = {
                        // Download progress indicator shown in top bar during model download
                        if (uiState.isDownloadingModel) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${uiState.downloadProgress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                // Clear button
                IconButton(
                    onClick = { viewModel.clearHistory() },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            AppIcons.Delete,
                            stringResource(R.string.clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.clear),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Record/Stop FAB (centered) with pulse animation.
                // Disabled while STT is initializing (models still loading).
                // Shows frozen Mic icon + spinner overlay while session is finalizing.
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val haptic = LocalHapticFeedback.current
                    val canRecord = !uiState.isInitializing && !uiState.isMicPermissionDenied
                    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (uiState.isRecording) 1.08f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "fab_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(if (uiState.isFinalizingSession) 1f else scale)
                            .alpha(
                                when {
                                    uiState.isFinalizingSession -> 0.5f
                                    canRecord || uiState.isRecording -> 1f
                                    else -> 0.4f
                                }
                            )
                            .background(
                                brush = if (!uiState.isRecording) PrimaryGradient else Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.error,
                                        MaterialTheme.colorScheme.error
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (!uiState.isFinalizingSession) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (uiState.isRecording) viewModel.stopStreaming()
                                    else viewModel.startStreaming()
                                }
                            },
                            enabled = (canRecord || uiState.isRecording) && !uiState.isFinalizingSession,
                            modifier = Modifier.size(64.dp)
                        ) {
                            // While finalizing: show Mic (frozen), otherwise show Stop/Mic normally.
                            Icon(
                                imageVector = if (uiState.isRecording) AppIcons.Stop else AppIcons.Mic,
                                contentDescription = if (uiState.isRecording) stringResource(R.string.stop) else stringResource(R.string.record),
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Spinner overlay shown while finalizing the session.
                    if (uiState.isFinalizingSession) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(72.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // AI status — icon tint shows readiness, no dot
                val aiReady = uiState.llmStatus.contains("Ready")
                IconButton(
                    onClick = { /* no-op */ },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            AppIcons.AutoAwesome,
                            contentDescription = null,
                            tint = if (aiReady) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.llm),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (aiReady) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error banner
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                AppIcons.Info,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(AppIcons.Close, stringResource(R.string.dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BrandPrimary,
                contentColor = Color.White,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        val tab = tabPositions[selectedTab]
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart, unbounded = true)
                                .offset(x = tab.left)
                                .width(tab.width)
                                .height(3.dp)
                                .background(Color.White)
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.tab_transcription),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(AppIcons.Mic, null)
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.tab_insights),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(AppIcons.AutoAwesome, null)
                    }
                )
            }

            // Content Area - Full Width
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.isInitializing) {
                    // Loading state with shimmer skeleton
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(3) {
                            ShimmerInsightCard()
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> TranscriptionSection(
                            segments = uiState.allSegments,
                            isRecording = uiState.isRecording,
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> {
                            InsightsSection(
                                insights = uiState.sortedInsights,
                                segments = uiState.completedSegments,
                                llmStatus = uiState.llmStatus,
                                isLlmEnabled = uiState.settings.llmEnabled,
                                isLlmAvailable = uiState.isLlmModelAvailable,
                                isFinalizingSession = uiState.isFinalizingSession,
                                isRecording = uiState.isRecording,
                                insightStrategy = uiState.insightStrategy,
                                batchProgress = uiState.batchProgress,
                                onDownloadLlm = {
                                    onNavigateToLlmDownload()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Processing banner: shown after stop while the final LLM insight is being generated.
            // Placed below the content so it does not displace the transcription/insights view.
            if (uiState.isFinalizingSession) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.processing_session),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

