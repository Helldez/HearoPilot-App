package com.hearopilot.app.ui.screens

import androidx.compose.foundation.layout.*
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.domain.model.DownloadState

/**
 * Setup screen for first-time model download.
 *
 * Displays during initial app launch when LLM model needs to be downloaded.
 * Features enhanced UX with:
 * - Progress bar with percentage
 * - Download speed and ETA
 * - Model information
 * - Retry and cancel options
 *
 * @param downloadState Current download state
 * @param onRetry Callback to retry download
 * @param onContinueOffline Callback to continue without LLM (optional)
 */
@Composable
fun SetupScreen(
    downloadState: DownloadState,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App logo/title
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 30.sp,
                fontFamily = SpaceGroteskFont,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Download content
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    when (downloadState) {
                        is DownloadState.Idle -> IdleContent(onRetry, onContinueOffline)
                        is DownloadState.Downloading -> DownloadingContent(downloadState)
                        is DownloadState.Completed -> CompletedContent()
                        is DownloadState.Error -> ErrorContent(downloadState.message, onRetry, onContinueOffline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Model info
            ModelInfoCard()
        }
    }
}

/**
 * Idle state - ready to start download.
 */
@Composable
private fun IdleContent(
    onStart: () -> Unit,
    onContinueOffline: () -> Unit
) {
    Icon(
        imageVector = AppIcons.AutoAwesome,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Text(
        text = stringResource(R.string.setup_ai_insights_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    // WHY section - explains the benefits
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_ai_why_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        FeatureBullet(
            icon = AppIcons.Lightbulb,
            text = stringResource(R.string.setup_ai_benefit_realtime)
        )

        FeatureBullet(
            icon = AppIcons.AutoAwesome,
            text = stringResource(R.string.setup_ai_benefit_analysis)
        )

        FeatureBullet(
            icon = AppIcons.OfflineBolt,
            text = stringResource(R.string.setup_ai_benefit_offline)
        )

        FeatureBullet(
            icon = AppIcons.Speed,
            text = stringResource(R.string.setup_ai_benefit_fast)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
    ) {
        Icon(AppIcons.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.insights_download_button))
    }

    TextButton(
        onClick = onContinueOffline,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.onboarding_llm_skip_short),
            color = Gray600,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Feature bullet point with icon.
 */
@Composable
private fun FeatureBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Downloading state - shows progress.
 */
@Composable
private fun DownloadingContent(state: DownloadState.Downloading) {
    Icon(
        imageVector = AppIcons.CloudDownload,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Text(
        text = stringResource(R.string.setup_downloading_model),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    // Progress percentage
    Text(
        text = "${state.progress.percentage}%",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = BrandPrimary
    )

    // Progress bar
    LinearProgressIndicator(
        progress = { state.progress.percentage / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        color = BrandPrimary,
        trackColor = Gray200
    )

    // Download stats
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.onboarding_llm_downloaded_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${state.progress.bytesDownloaded / 1_000_000} MB / ${state.progress.totalBytes / 1_000_000} MB",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (state.speedMbps > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.onboarding_download_speed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%.2f MB/s".format(state.speedMbps),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (state.etaSeconds > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.onboarding_download_eta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSeconds(state.etaSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

}

/**
 * Completed state - download successful.
 */
@Composable
private fun CompletedContent() {
    Icon(
        imageVector = AppIcons.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = AccentSuccess
    )

    Text(
        text = stringResource(R.string.onboarding_llm_complete),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Text(
        text = stringResource(R.string.onboarding_llm_initializing),
        style = MaterialTheme.typography.bodyMedium,
        color = Gray600,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    CircularProgressIndicator(
        modifier = Modifier.size(40.dp),
        color = BrandPrimary
    )
}

/**
 * Error state - download failed.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit
) {
    Icon(
        imageVector = AppIcons.Error,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = AccentError
    )

    Text(
        text = stringResource(R.string.onboarding_download_failed),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Surface(
        color = AccentError.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = AccentError
        )
    }

    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
    ) {
        Icon(AppIcons.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.onboarding_retry_download))
    }

    TextButton(
        onClick = onContinueOffline,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.setup_continue_no_ai),
            color = Gray600,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Model information card.
 */
@Composable
private fun ModelInfoCard() {
    Surface(
        color = White.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.setup_model_information),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ModelInfoRow(stringResource(R.string.onboarding_model_label), stringResource(R.string.onboarding_llm_model_name))
            ModelInfoRow(stringResource(R.string.setup_model_capability_label), stringResource(R.string.setup_model_capability_value))
            ModelInfoRow(stringResource(R.string.onboarding_runs_label), stringResource(R.string.onboarding_on_device))
        }
    }
}

@Composable
private fun ModelInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Gray600
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Format seconds to human-readable time.
 */
private fun formatSeconds(seconds: Int): String {
    return when {
        seconds < 60 -> "$seconds sec"
        seconds < 3600 -> "${seconds / 60} min ${seconds % 60} sec"
        else -> "${seconds / 3600} hr ${(seconds % 3600) / 60} min"
    }
}
