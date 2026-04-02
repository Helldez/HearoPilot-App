package com.hearopilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearopilot.app.domain.model.LicenseEntry
import com.hearopilot.app.presentation.licenses.LicensesViewModel
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.BrandPrimary

/**
 * Screen listing all open-source licenses and AI model attributions.
 *
 * Gemma and Parakeet cards are expanded by default because their licenses
 * impose the strongest attribution obligations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.licenses_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            uiState.licenses.forEach { entry ->
                LicenseCard(
                    entry = entry,
                    isExpanded = entry.id in uiState.expandedIds,
                    onToggleExpand = { viewModel.toggleExpanded(entry.id) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LicenseCard(
    entry: LicenseEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header row ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.componentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.licenseType,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandPrimary
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.licenses_hide_full_text
                            else R.string.licenses_show_full_text
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.shortNotice.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.shortNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── URL chips ─────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LicenseUrlChip(
                    label = stringResource(R.string.licenses_open_url),
                    url = entry.primaryUrl,
                    onOpen = { uriHandler.openUri(entry.primaryUrl) }
                )
                entry.secondaryUrl?.let { url ->
                    LicenseUrlChip(
                        label = "Model Card",
                        url = url,
                        onOpen = { uriHandler.openUri(url) }
                    )
                }
                entry.githubUrl?.let { url ->
                    LicenseUrlChip(
                        label = "GitHub",
                        url = url,
                        onOpen = { uriHandler.openUri(url) }
                    )
                }
            }

            // ── Full text (collapsible) ───────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = entry.fullText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseUrlChip(
    label: String,
    url: String,
    onOpen: () -> Unit
) {
    SuggestionChip(
        onClick = onOpen,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        icon = {
            Icon(
                imageVector = AppIcons.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
    )
}
