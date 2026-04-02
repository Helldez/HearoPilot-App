package com.hearopilot.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.ui.components.DownloadOnboardingCarousel
import com.hearopilot.app.ui.components.GradientButton
import com.hearopilot.app.domain.model.DownloadState

/** CC BY 4.0 license URL for the Parakeet TDT model. */
private const val STT_LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"

/** Vertical overlap between the hero and the content sheet, in dp. */
private val CONTENT_SHEET_OVERLAP = 28.dp

/** Corner radius for the rounded-top content sheet. */
private val CONTENT_SHEET_CORNER = 32.dp

/**
 * Bottom clearance for the immersive layout progress bar.
 * Must clear the onboarding page-dot indicator (dot ~10dp + 24dp padding above nav bar = ~34dp),
 * plus a small visual margin.
 */
private val PROGRESS_BAR_BOTTOM_CLEARANCE = 58.dp

/**
 * Screen for STT (Speech-to-Text) model download.
 *
 * Three layouts:
 * - [DownloadState.Downloading]: full-screen immersive gradient with large carousel,
 *   percentage counter, and a thin progress bar at the bottom.
 * - [DownloadState.Completed] / [DownloadState.Error]: full-screen result layout —
 *   gradient background, centered icon + text + action button, no white sheet.
 * - [DownloadState.Idle]: hero at top + content sheet with model info and download button.
 */
@Composable
fun SttDownloadScreen(
    downloadState: DownloadState,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    val rawProgress = if (downloadState is DownloadState.Downloading)
        downloadState.progress.percentage / 100f else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 300),
        label = "stt_progress"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (downloadState) {
            is DownloadState.Downloading -> SttImmersiveLayout(downloadState, animatedProgress)
            is DownloadState.Completed   -> SttResultLayout(downloadState, onContinue = onContinue, onRetry = onRetry)
            is DownloadState.Error       -> SttResultLayout(downloadState, onContinue = onContinue, onRetry = onRetry)
            else                         -> SttIdleLayout(onStartDownload)
        }
    }
}

// ── Immersive layout (active download) ──────────────────────────────────────

/**
 * Full-screen gradient layout shown during active download.
 * Displays: state title, large percentage, animated carousel, thin progress bar.
 */
@Composable
private fun SttImmersiveLayout(
    downloadState: DownloadState.Downloading,
    animatedProgress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DownloadImmersiveGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.onboarding_downloading),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.70f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage — primary visual anchor
            Text(
                text = "${downloadState.progress.percentage}%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            // Rotating messages — full-screen, no card background
            DownloadOnboardingCarousel(immersive = true)

            Spacer(modifier = Modifier.weight(1f))

            // Inline stats: MB · speed · ETA
            SttImmersiveStats(downloadState)

            Spacer(modifier = Modifier.height(12.dp))

            // Thin progress bar — no card, blends with gradient background
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(PROGRESS_BAR_BOTTOM_CLEARANCE))
        }
    }
}

@Composable
private fun SttImmersiveStats(downloadState: DownloadState.Downloading) {
    val parts = buildList {
        if (downloadState.progress.totalBytes > 0) {
            add(
                stringResource(
                    R.string.onboarding_download_progress,
                    downloadState.progress.bytesDownloaded / 1_000_000,
                    downloadState.progress.totalBytes / 1_000_000
                )
            )
        }
        if (downloadState.speedMbps > 0) {
            add(stringResource(R.string.onboarding_download_speed_value, downloadState.speedMbps))
        }
        if (downloadState.etaSeconds > 0) {
            add(sttFormatSeconds(downloadState.etaSeconds))
        }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center
        )
    }
}

// ── Result layout (Completed / Error) ───────────────────────────────────────

/**
 * Full-screen gradient layout for completed or failed downloads.
 * No white sheet — icon, title, description and action button are centered on the gradient.
 */
@Composable
private fun SttResultLayout(
    downloadState: DownloadState,
    onContinue: () -> Unit,
    onRetry: () -> Unit
) {
    val isSuccess = downloadState is DownloadState.Completed
    val gradient = if (isSuccess)
        androidx.compose.ui.graphics.Brush.linearGradient(listOf(BrandPurpleDark, AccentSuccess.copy(alpha = 0.60f)))
    else
        androidx.compose.ui.graphics.Brush.linearGradient(listOf(BrandPurpleDark, AccentError.copy(alpha = 0.60f)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                imageVector = if (isSuccess) AppIcons.CheckCircle else AppIcons.Error,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = if (isSuccess) stringResource(R.string.onboarding_stt_ready)
                       else stringResource(R.string.onboarding_download_failed),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (isSuccess) {
                Text(
                    text = stringResource(R.string.onboarding_stt_ready_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            GradientButton(
                text = if (isSuccess) stringResource(R.string.onboarding_continue)
                       else stringResource(R.string.onboarding_retry_download),
                onClick = if (isSuccess) onContinue else onRetry,
                modifier = Modifier.fillMaxWidth(),
                gradient = androidx.compose.ui.graphics.SolidColor(Color.White),
                textColor = BrandPurpleDark
            )
        }
    }
}

// ── Idle layout ──────────────────────────────────────────────────────────────

/**
 * Hero (no bottom rounding) + overlapping content sheet with model info and download button.
 */
@Composable
private fun SttIdleLayout(onStartDownload: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ── Hero ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.SolidColor(BrandPurpleDark)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 24.dp, bottom = 36.dp + CONTENT_SHEET_OVERLAP),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = stringResource(R.string.onboarding_stt_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        // ── Content sheet — overlaps hero with rounded top corners ───────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = -CONTENT_SHEET_OVERLAP)
                .clip(RoundedCornerShape(topStart = CONTENT_SHEET_CORNER, topEnd = CONTENT_SHEET_CORNER))
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.onboarding_stt_model_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.onboarding_stt_model_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SttModelInfoRow(
                            stringResource(R.string.onboarding_model_label),
                            stringResource(R.string.onboarding_stt_model_name)
                        )
                        SttModelInfoRow(
                            stringResource(R.string.onboarding_runs_label),
                            stringResource(R.string.onboarding_on_device)
                        )
                        SttModelInfoRow(
                            stringResource(R.string.onboarding_source_label),
                            stringResource(R.string.onboarding_huggingface)
                        )
                        SttLicenseLinkRow()
                    }
                    GradientButton(
                        text = stringResource(R.string.onboarding_stt_download_button),
                        onClick = onStartDownload,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        AppIcons.Info, null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.onboarding_stt_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Tappable license row shown in the STT model info card.
 * Opens the Creative Commons CC BY 4.0 license in the system browser.
 */
@Composable
private fun SttLicenseLinkRow() {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.onboarding_license_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { uriHandler.openUri(STT_LICENSE_URL) },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "CC BY 4.0 · NVIDIA",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = BrandPrimary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = AppIcons.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = BrandPrimary
            )
        }
    }
}

@Composable
private fun SttModelInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun sttFormatSeconds(seconds: Int): String {
    return when {
        seconds < 60   -> stringResource(R.string.onboarding_time_seconds, seconds)
        seconds < 3600 -> stringResource(R.string.onboarding_time_minutes, seconds / 60, seconds % 60)
        else           -> stringResource(R.string.onboarding_time_hours, seconds / 3600, (seconds % 3600) / 60)
    }
}
