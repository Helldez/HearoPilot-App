package com.hearopilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmModelVariant
import com.hearopilot.app.domain.model.LlmSamplerConfig
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.ThemeMode
import com.hearopilot.app.presentation.settings.SettingsViewModel
import kotlin.math.roundToInt

/** URL for the app's Privacy Policy document. */
private const val PRIVACY_POLICY_URL =
    "https://helldez.github.io/hearopilot/privacy-policy.html"

/**
 * Settings screen for configuring app behavior.
 *
 * Features:
 * - LLM inference interval configuration (5-60 seconds)
 * - Future: Model selection, audio settings, etc.
 *
 * @param onNavigateBack Callback when back button is pressed
 * @param viewModel SettingsViewModel for managing settings state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLlmDownload: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLlmDownloaded by viewModel.isLlmDownloaded.collectAsStateWithLifecycle()
    val llmDownloadState by viewModel.llmDownloadState.collectAsStateWithLifecycle()
    val activeDownloadVariant by viewModel.activeDownloadVariant.collectAsStateWithLifecycle()
    val recommendedVariant = viewModel.recommendedVariant

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // AI Model Section
            SettingsSection(title = stringResource(R.string.settings_section_llm)) {
                LlmModelVariantSetting(
                    currentVariant = settings.llmModelVariant,
                    recommendedVariant = recommendedVariant,
                    isQ8Downloaded = viewModel.isVariantDownloaded(LlmModelVariant.Q8_0),
                    isIq4Downloaded = viewModel.isVariantDownloaded(LlmModelVariant.IQ4_NL),
                    isQwen35Downloaded = viewModel.isVariantDownloaded(LlmModelVariant.QWEN3_5_Q8_0),
                    activeDownloadVariant = activeDownloadVariant,
                    onVariantChange = { viewModel.updateLlmModelVariant(it) },
                    onDownloadVariant = { viewModel.downloadVariant(it) }
                )
                LlmModelDownloadSetting(
                    isDownloaded = isLlmDownloaded,
                    downloadState = llmDownloadState,
                    onStartDownload = { viewModel.startLlmDownload() },
                    onRetryDownload = { viewModel.retryLlmDownload() }
                )
                LlmEnabledSetting(
                    enabled = settings.llmEnabled,
                    onEnabledChange = { viewModel.updateLlmEnabled(it) }
                )
            }

            // Appearance Section
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                ThemeModeSetting(
                    currentMode = settings.themeMode,
                    onModeChange = { viewModel.updateThemeMode(it) }
                )
            }

            // Recording Modes accordion
            SettingsSection(title = stringResource(R.string.settings_recording_modes)) {
                RecordingModesAccordion(settings = settings, viewModel = viewModel)
            }

            // VAD Settings Section
            SettingsSection(title = stringResource(R.string.settings_section_vad)) {
                VadParametersSetting(
                    minSilenceDuration = settings.vadMinSilenceDuration,
                    maxSpeechDuration = settings.vadMaxSpeechDuration,
                    threshold = settings.vadThreshold,
                    onMinSilenceChange = { viewModel.updateVadMinSilenceDuration(it) },
                    onMaxSpeechChange = { viewModel.updateVadMaxSpeechDuration(it) },
                    onThresholdChange = { viewModel.updateVadThreshold(it) },
                    onReset = { viewModel.resetVadParameters() }
                )
            }

            // LLM Sampler Settings Section
            SettingsSection(title = stringResource(R.string.settings_section_llm_sampler)) {
                LlmSamplerSetting(
                    samplerConfig = settings.llmSamplerConfig,
                    onTemperatureChange = { viewModel.updateLlmTemperature(it) },
                    onTopKChange = { viewModel.updateLlmTopK(it) },
                    onTopPChange = { viewModel.updateLlmTopP(it) },
                    onMinPChange = { viewModel.updateLlmMinP(it) },
                    onRepeatPenaltyChange = { viewModel.updateLlmRepeatPenalty(it) },
                    onReset = { viewModel.resetLlmSamplerConfig() }
                )
            }

            // About & Legal Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                ExternalLinkSetting(
                    title = stringResource(R.string.settings_privacy_policy),
                    url = PRIVACY_POLICY_URL
                )
                LicensesNavigationSetting(onClick = onNavigateToLicenses)
            }
        }
    }
}

/**
 * Accordion containing one collapsible card per recording mode.
 * All modes start collapsed; tapping a header toggles its content.
 */
@Composable
private fun RecordingModesAccordion(
    settings: AppSettings,
    viewModel: SettingsViewModel
) {
    // Set of currently expanded modes
    var expandedModes by remember { mutableStateOf(emptySet<RecordingMode>()) }

    fun toggle(mode: RecordingMode) {
        expandedModes = if (mode in expandedModes) expandedModes - mode else expandedModes + mode
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Simple Listening
        ModeAccordionCard(
            title = stringResource(R.string.settings_section_simple_listening),
            icon = AppIcons.ModeSimpleListening,
            isExpanded = RecordingMode.SIMPLE_LISTENING in expandedModes,
            onToggle = { toggle(RecordingMode.SIMPLE_LISTENING) }
        ) {
            LlmIntervalSetting(
                currentInterval = settings.simpleListeningIntervalSeconds,
                onIntervalChange = { viewModel.updateModeInterval(RecordingMode.SIMPLE_LISTENING, it) },
                titleOverride = stringResource(R.string.settings_interval_analysis),
                minIntervalSeconds = SettingsViewModel.MIN_INTERVAL_SECONDS_ANALYSIS
            )
            LlmSystemPromptSetting(
                currentPrompt = settings.simpleListeningSystemPrompt,
                onPromptChange = { viewModel.updateModeSystemPrompt(RecordingMode.SIMPLE_LISTENING, it) },
                onReset = { viewModel.resetModeSystemPrompt(RecordingMode.SIMPLE_LISTENING) }
            )
            DefaultStrategySetting(
                currentStrategy = settings.simpleListeningDefaultStrategy,
                onStrategyChange = { viewModel.updateModeDefaultStrategy(RecordingMode.SIMPLE_LISTENING, it) }
            )
        }

        // Short Meeting
        ModeAccordionCard(
            title = stringResource(R.string.settings_section_short_meeting),
            icon = AppIcons.ModeShortMeeting,
            isExpanded = RecordingMode.SHORT_MEETING in expandedModes,
            onToggle = { toggle(RecordingMode.SHORT_MEETING) }
        ) {
            LlmIntervalSetting(
                currentInterval = settings.shortMeetingIntervalSeconds,
                onIntervalChange = { viewModel.updateModeInterval(RecordingMode.SHORT_MEETING, it) },
                titleOverride = stringResource(R.string.settings_interval_analysis),
                minIntervalSeconds = SettingsViewModel.MIN_INTERVAL_SECONDS_ANALYSIS
            )
            LlmSystemPromptSetting(
                currentPrompt = settings.shortMeetingSystemPrompt,
                onPromptChange = { viewModel.updateModeSystemPrompt(RecordingMode.SHORT_MEETING, it) },
                onReset = { viewModel.resetModeSystemPrompt(RecordingMode.SHORT_MEETING) }
            )
            DefaultStrategySetting(
                currentStrategy = settings.shortMeetingDefaultStrategy,
                onStrategyChange = { viewModel.updateModeDefaultStrategy(RecordingMode.SHORT_MEETING, it) }
            )
        }

        // Long Meeting
        ModeAccordionCard(
            title = stringResource(R.string.settings_section_long_meeting),
            icon = AppIcons.ModeLongMeeting,
            isExpanded = RecordingMode.LONG_MEETING in expandedModes,
            onToggle = { toggle(RecordingMode.LONG_MEETING) }
        ) {
            LongRecordingIntervalSetting(
                currentInterval = settings.longMeetingIntervalMinutes,
                onIntervalChange = { viewModel.updateModeInterval(RecordingMode.LONG_MEETING, it) }
            )
            LlmSystemPromptSetting(
                currentPrompt = settings.longMeetingSystemPrompt,
                onPromptChange = { viewModel.updateModeSystemPrompt(RecordingMode.LONG_MEETING, it) },
                onReset = { viewModel.resetModeSystemPrompt(RecordingMode.LONG_MEETING) }
            )
            DefaultStrategySetting(
                currentStrategy = settings.longMeetingDefaultStrategy,
                onStrategyChange = { viewModel.updateModeDefaultStrategy(RecordingMode.LONG_MEETING, it) }
            )
        }

        // Real-time Translation
        ModeAccordionCard(
            title = stringResource(R.string.settings_section_translation),
            icon = AppIcons.ModeTranslation,
            isExpanded = RecordingMode.REAL_TIME_TRANSLATION in expandedModes,
            onToggle = { toggle(RecordingMode.REAL_TIME_TRANSLATION) }
        ) {
            LlmIntervalSetting(
                currentInterval = settings.translationIntervalSeconds,
                onIntervalChange = { viewModel.updateModeInterval(RecordingMode.REAL_TIME_TRANSLATION, it) },
                titleOverride = stringResource(R.string.settings_interval_translation)
                // Translation keeps the 10s minimum (MIN_INTERVAL_SECONDS default)
            )
            TranslationLanguageSetting(
                currentLanguage = settings.translationTargetLanguage,
                onLanguageChange = { viewModel.updateTranslationTargetLanguage(it) }
            )
            LlmSystemPromptSetting(
                currentPrompt = settings.translationSystemPrompt,
                onPromptChange = { viewModel.updateModeSystemPrompt(RecordingMode.REAL_TIME_TRANSLATION, it) },
                onReset = { viewModel.resetModeSystemPrompt(RecordingMode.REAL_TIME_TRANSLATION) }
            )
            DefaultStrategySetting(
                currentStrategy = settings.translationDefaultStrategy,
                onStrategyChange = { viewModel.updateModeDefaultStrategy(RecordingMode.REAL_TIME_TRANSLATION, it) }
            )
        }
    }
}

/**
 * Single accordion card for a recording mode.
 * Header (icon + title + chevron) is always visible; [content] animates in/out.
 */
@Composable
private fun ModeAccordionCard(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Tappable header ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Expandable content ───────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Card showing two selectable model variant options (Q8_0 and IQ4_NL) with:
 *  - Name and description for each option.
 *  - A "Recommended for your device" badge on the detected optimal variant.
 *  - A "Downloaded" chip when the variant's model file is present on disk.
 *
 * @param currentVariant The currently persisted variant selection.
 * @param recommendedVariant The variant recommended by [DeviceTierDetector] for this device.
 * @param isQ8Downloaded True if the Q8_0 model file exists on disk.
 * @param isIq4Downloaded True if the IQ4_NL model file exists on disk.
 * @param onVariantChange Callback when the user selects a different variant.
 */
@Composable
private fun LlmModelVariantSetting(
    currentVariant: LlmModelVariant,
    recommendedVariant: LlmModelVariant,
    isQ8Downloaded: Boolean,
    isIq4Downloaded: Boolean,
    isQwen35Downloaded: Boolean,
    activeDownloadVariant: LlmModelVariant?,
    onVariantChange: (LlmModelVariant) -> Unit,
    onDownloadVariant: (LlmModelVariant) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_llm_model_variant_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_llm_model_variant_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Q8_0 option
        ModelVariantOption(
            isSelected = currentVariant == LlmModelVariant.Q8_0,
            isRecommended = recommendedVariant == LlmModelVariant.Q8_0,
            isDownloaded = isQ8Downloaded,
            isDownloading = activeDownloadVariant == LlmModelVariant.Q8_0,
            isBeta = false,
            name = stringResource(R.string.settings_llm_model_variant_q8_name),
            description = stringResource(R.string.settings_llm_model_variant_q8_desc),
            onClick = { onVariantChange(LlmModelVariant.Q8_0) },
            onDownload = { onDownloadVariant(LlmModelVariant.Q8_0) }
        )

        // IQ4_NL option
        ModelVariantOption(
            isSelected = currentVariant == LlmModelVariant.IQ4_NL,
            isRecommended = recommendedVariant == LlmModelVariant.IQ4_NL,
            isDownloaded = isIq4Downloaded,
            isDownloading = activeDownloadVariant == LlmModelVariant.IQ4_NL,
            isBeta = false,
            name = stringResource(R.string.settings_llm_model_variant_iq4_name),
            description = stringResource(R.string.settings_llm_model_variant_iq4_desc),
            onClick = { onVariantChange(LlmModelVariant.IQ4_NL) },
            onDownload = { onDownloadVariant(LlmModelVariant.IQ4_NL) }
        )

        // Advanced models section — experimental, manual-download only
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Column {
            Text(
                text = stringResource(R.string.settings_llm_advanced_section_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_llm_advanced_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Qwen 3.5 0.8B Q8_0 — beta, advanced users only
        ModelVariantOption(
            isSelected = currentVariant == LlmModelVariant.QWEN3_5_Q8_0,
            isRecommended = false,
            isDownloaded = isQwen35Downloaded,
            isDownloading = activeDownloadVariant == LlmModelVariant.QWEN3_5_Q8_0,
            isBeta = true,
            name = stringResource(R.string.settings_llm_model_variant_qwen35_name),
            description = stringResource(R.string.settings_llm_model_variant_qwen35_desc),
            onClick = { onVariantChange(LlmModelVariant.QWEN3_5_Q8_0) },
            onDownload = { onDownloadVariant(LlmModelVariant.QWEN3_5_Q8_0) }
        )
    }
}

/**
 * A single selectable card for one [LlmModelVariant].
 *
 * When the model is not yet downloaded, shows a Download icon button on the trailing edge.
 * While any download is in progress, the button is replaced by a small progress spinner.
 */
@Composable
private fun ModelVariantOption(
    isSelected: Boolean,
    isRecommended: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isBeta: Boolean,
    name: String,
    description: String,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDownloaded) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = stringResource(R.string.settings_llm_model_variant_downloaded),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isBeta) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = stringResource(R.string.settings_llm_model_variant_beta),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isRecommended) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = stringResource(R.string.settings_llm_model_variant_recommended),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            // Download button / progress spinner on the trailing edge when not yet downloaded
            if (!isDownloaded) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(2.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = AppIcons.Download,
                            contentDescription = stringResource(R.string.settings_llm_model_download),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tappable row that opens an external URL in the system browser.
 */
@Composable
private fun ExternalLinkSetting(title: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = { uriHandler.openUri(url) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = AppIcons.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Collapsible settings section. The header row (title + chevron) is always visible;
 * content animates in/out when the user taps the header.
 *
 * @param initiallyExpanded Set to true for sections the user is most likely to need
 *                          immediately (none by default — all start collapsed).
 */
@Composable
private fun SettingsSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Tappable header ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Expandable content ───────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Tappable row that navigates to the Open Source Licenses screen.
 */
@Composable
private fun LicensesNavigationSetting(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_open_source_licenses),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_open_source_licenses_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * LLM inference interval setting with slider.
 *
 * @param currentInterval Current interval in seconds
 * @param onIntervalChange Callback when interval changes
 * @param minIntervalSeconds Minimum allowed interval (defaults to translation minimum 10s;
 *                           pass [SettingsViewModel.MIN_INTERVAL_SECONDS_ANALYSIS] for
 *                           Simple Listening and Short Meeting modes)
 * @param onReset Callback to reset to default value (optional)
 */
@Composable
private fun LlmIntervalSetting(
    currentInterval: Int,
    onIntervalChange: (Int) -> Unit,
    titleOverride: String? = null,
    minIntervalSeconds: Int = SettingsViewModel.MIN_INTERVAL_SECONDS,
    onReset: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = titleOverride ?: stringResource(R.string.settings_short_interval_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.settings_short_interval_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Slider + current value
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Current value displayed prominently above the min/max row
            Text(
                text = stringResource(R.string.settings_interval_current, currentInterval),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Slider(
                value = currentInterval.toFloat(),
                onValueChange = {
                    // Snap to 10-second increments
                    val snapped = (it.roundToInt() / 10) * 10
                    onIntervalChange(snapped.coerceIn(minIntervalSeconds, SettingsViewModel.MAX_INTERVAL_SECONDS))
                },
                valueRange = minIntervalSeconds.toFloat()..SettingsViewModel.MAX_INTERVAL_SECONDS.toFloat(),
                steps = (SettingsViewModel.MAX_INTERVAL_SECONDS - minIntervalSeconds) / 10 - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Min/Max labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${minIntervalSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${SettingsViewModel.MAX_INTERVAL_SECONDS}s (3min)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Reset button
        if (onReset != null) {
            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.settings_reset_default, SettingsViewModel.DEFAULT_INTERVAL_SECONDS),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Help text
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_interval_tip_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.settings_interval_tip_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Long recording insight interval setting with discrete buttons.
 *
 * Allows user to configure insight generation interval for LONG recording sessions.
 * Options: 10, 20, or 30 minutes.
 *
 * @param currentInterval Current interval in minutes
 * @param onIntervalChange Callback when interval changes
 */
@Composable
private fun LongRecordingIntervalSetting(
    currentInterval: Int,
    onIntervalChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_long_interval_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.settings_long_interval_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Discrete buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(3, 5, 10).forEach { minutes ->
                OutlinedButton(
                    onClick = { onIntervalChange(minutes) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (currentInterval == minutes) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                        contentColor = if (currentInterval == minutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(
                        width = if (currentInterval == minutes) 2.dp else 1.dp,
                        color = if (currentInterval == minutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = "${minutes}m",
                        fontWeight = if (currentInterval == minutes) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * LLM system prompt setting with TextField.
 *
 * Allows user to customize the AI assistant's behavior and instructions.
 *
 * @param currentPrompt Current system prompt text
 * @param onPromptChange Callback when prompt changes
 * @param onReset Callback to reset to default value
 */
@Composable
private fun LlmSystemPromptSetting(
    currentPrompt: String,
    onPromptChange: (String) -> Unit,
    onReset: (() -> Unit)? = null
) {
    var promptText by remember(currentPrompt) { mutableStateOf(currentPrompt) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // Header
            Column {
                Text(
                    text = stringResource(R.string.settings_system_prompt_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_system_prompt_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // TextField
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_system_prompt_hint)) },
                minLines = 4,
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onReset != null) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_system_prompt_reset))
                    }
                }

                Button(
                    onClick = { onPromptChange(promptText) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = promptText != currentPrompt
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }

            // Help text
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_system_prompt_note_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.settings_system_prompt_note_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
}

/**
 * Toggle selector for the default insight strategy of a recording mode.
 */
@Composable
private fun DefaultStrategySetting(
    currentStrategy: InsightStrategy,
    onStrategyChange: (InsightStrategy) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_default_strategy),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                InsightStrategy.REAL_TIME to stringResource(R.string.insight_strategy_realtime),
                InsightStrategy.END_OF_SESSION to stringResource(R.string.insight_strategy_batch)
            ).forEach { (strategy, label) ->
                val isSelected = currentStrategy == strategy
                FilterChip(
                    selected = isSelected,
                    onClick = { onStrategyChange(strategy) },
                    label = { Text(label) },
                    leadingIcon = if (isSelected) {
                        { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * VAD parameters setting with sliders.
 *
 * Allows user to fine-tune voice activity detection for better transcription accuracy.
 * IMPORTANT: Changes require app restart to take effect.
 *
 * @param minSilenceDuration Current minimum silence duration in seconds
 * @param maxSpeechDuration Current maximum speech duration in seconds
 * @param threshold Current VAD threshold
 * @param onMinSilenceChange Callback when min silence duration changes
 * @param onMaxSpeechChange Callback when max speech duration changes
 * @param onThresholdChange Callback when threshold changes
 * @param onReset Callback to reset all parameters to defaults
 */
@Composable
private fun VadParametersSetting(
    minSilenceDuration: Float,
    maxSpeechDuration: Float,
    threshold: Float,
    onMinSilenceChange: (Float) -> Unit,
    onMaxSpeechChange: (Float) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_vad_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_vad_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VadParameterSlider(
            label = stringResource(R.string.settings_vad_min_silence),
            description = stringResource(R.string.settings_vad_min_silence_desc),
            value = minSilenceDuration,
            onValueChange = onMinSilenceChange,
            valueRange = SettingsViewModel.MIN_SILENCE_DURATION_MIN..SettingsViewModel.MIN_SILENCE_DURATION_MAX,
            steps = 18,
            formatValue = { "${String.format("%.1f", it)}s" },
            minLabel = "${SettingsViewModel.MIN_SILENCE_DURATION_MIN}s",
            maxLabel = "${SettingsViewModel.MIN_SILENCE_DURATION_MAX}s"
        )

        VadParameterSlider(
            label = stringResource(R.string.settings_vad_max_speech),
            description = stringResource(R.string.settings_vad_max_speech_desc),
            value = maxSpeechDuration,
            onValueChange = onMaxSpeechChange,
            valueRange = SettingsViewModel.MAX_SPEECH_DURATION_MIN..SettingsViewModel.MAX_SPEECH_DURATION_MAX,
            steps = 26,
            formatValue = { "${it.roundToInt()}s" },
            minLabel = "${SettingsViewModel.MAX_SPEECH_DURATION_MIN.roundToInt()}s",
            maxLabel = "${SettingsViewModel.MAX_SPEECH_DURATION_MAX.roundToInt()}s"
        )

        VadParameterSlider(
            label = stringResource(R.string.settings_vad_threshold),
            description = stringResource(R.string.settings_vad_threshold_desc),
            value = threshold,
            onValueChange = onThresholdChange,
            valueRange = SettingsViewModel.VAD_THRESHOLD_MIN..SettingsViewModel.VAD_THRESHOLD_MAX,
            steps = 13,
            formatValue = { String.format("%.2f", it) },
            minLabel = stringResource(R.string.settings_vad_sensitivity_more),
            maxLabel = stringResource(R.string.settings_vad_sensitivity_less)
        )

        TextButton(
            onClick = onReset,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = stringResource(R.string.settings_vad_reset_all),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(text = "⚠️", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_vad_restart_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_vad_restart_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_vad_troubleshooting_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.settings_vad_troubleshooting_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Reusable slider component for VAD parameters.
 */
@Composable
private fun VadParameterSlider(
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    formatValue: (Float) -> String,
    minLabel: String,
    maxLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Label and current value
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = formatValue(value),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Min/Max labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LlmSamplerSetting(
    samplerConfig: LlmSamplerConfig,
    onTemperatureChange: (Float) -> Unit,
    onTopKChange: (Int) -> Unit,
    onTopPChange: (Float) -> Unit,
    onMinPChange: (Float) -> Unit,
    onRepeatPenaltyChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_llm_sampler_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_llm_sampler_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VadParameterSlider(
            label = stringResource(R.string.settings_llm_sampler_temperature),
            description = stringResource(R.string.settings_llm_sampler_temperature_desc),
            value = samplerConfig.temperature,
            onValueChange = onTemperatureChange,
            valueRange = SettingsViewModel.LLM_TEMPERATURE_MIN..SettingsViewModel.LLM_TEMPERATURE_MAX,
            steps = 39,
            formatValue = { String.format("%.2f", it) },
            minLabel = "0.00",
            maxLabel = "2.00"
        )

        // top_k is Int but the slider works with Float; we round on read/write
        VadParameterSlider(
            label = stringResource(R.string.settings_llm_sampler_top_k),
            description = stringResource(R.string.settings_llm_sampler_top_k_desc),
            value = samplerConfig.topK.toFloat(),
            onValueChange = { onTopKChange(it.roundToInt()) },
            valueRange = SettingsViewModel.LLM_TOP_K_MIN.toFloat()..SettingsViewModel.LLM_TOP_K_MAX.toFloat(),
            steps = 99,
            formatValue = { it.roundToInt().toString() },
            minLabel = "0",
            maxLabel = "100"
        )

        VadParameterSlider(
            label = stringResource(R.string.settings_llm_sampler_top_p),
            description = stringResource(R.string.settings_llm_sampler_top_p_desc),
            value = samplerConfig.topP,
            onValueChange = onTopPChange,
            valueRange = SettingsViewModel.LLM_TOP_P_MIN..SettingsViewModel.LLM_TOP_P_MAX,
            steps = 19,
            formatValue = { String.format("%.2f", it) },
            minLabel = "0.00",
            maxLabel = "1.00"
        )

        VadParameterSlider(
            label = stringResource(R.string.settings_llm_sampler_min_p),
            description = stringResource(R.string.settings_llm_sampler_min_p_desc),
            value = samplerConfig.minP,
            onValueChange = onMinPChange,
            valueRange = SettingsViewModel.LLM_MIN_P_MIN..SettingsViewModel.LLM_MIN_P_MAX,
            steps = 19,
            formatValue = { String.format("%.2f", it) },
            minLabel = "0.00",
            maxLabel = "1.00"
        )

        VadParameterSlider(
            label = stringResource(R.string.settings_llm_sampler_repeat_penalty),
            description = stringResource(R.string.settings_llm_sampler_repeat_penalty_desc),
            value = samplerConfig.repeatPenalty,
            onValueChange = onRepeatPenaltyChange,
            valueRange = SettingsViewModel.LLM_REPEAT_PENALTY_MIN..SettingsViewModel.LLM_REPEAT_PENALTY_MAX,
            steps = 19,
            formatValue = { String.format("%.2f", it) },
            minLabel = "1.00",
            maxLabel = "2.00"
        )

        TextButton(
            onClick = onReset,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = stringResource(R.string.settings_llm_sampler_reset_all),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Theme mode setting with discrete buttons.
 *
 * Allows user to choose between SYSTEM (follow device), LIGHT, or DARK theme.
 *
 * @param currentMode Current theme mode
 * @param onModeChange Callback when mode changes
 */
@Composable
private fun ThemeModeSetting(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system_short),
                ThemeMode.LIGHT to stringResource(R.string.theme_light_short),
                ThemeMode.DARK to stringResource(R.string.theme_dark_short)
            ).forEach { (mode, label) ->
                OutlinedButton(
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (currentMode == mode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                        contentColor = if (currentMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(
                        width = if (currentMode == mode) 2.dp else 1.dp,
                        color = if (currentMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = label,
                        fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * LLM model file status card.
 *
 * Shows whether the currently selected model variant has been downloaded.
 * When not downloaded, displays a "Download Now" button that starts the download inline.
 */
@Composable
private fun LlmModelDownloadSetting(
    isDownloaded: Boolean,
    downloadState: DownloadState,
    onStartDownload: () -> Unit,
    onRetryDownload: () -> Unit
) {
    val isDownloading = downloadState is DownloadState.Downloading
    val isError = downloadState is DownloadState.Error
    val progress = (downloadState as? DownloadState.Downloading)?.progress?.percentage ?: 0
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "llm_settings_progress"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row: title + status badge
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_llm_model_file_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_llm_model_file_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Show badge only when the state adds information not conveyed by the button
                if (isDownloaded || isDownloading) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = if (isDownloaded) AccentSuccess.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (isDownloaded) stringResource(R.string.settings_llm_model_ready)
                            else stringResource(R.string.settings_llm_model_downloading, progress),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDownloaded) AccentSuccess
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Progress bar visible while a download is in progress
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline
                )
            }

            // Start download inline when the selected variant is not yet available
            if (!isDownloaded && !isDownloading) {
                Button(
                    onClick = if (isError) onRetryDownload else onStartDownload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = AppIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isError) stringResource(R.string.settings_llm_model_retry)
                        else stringResource(R.string.settings_llm_model_download)
                    )
                }
            }
        }
}

/**
 * LLM enable/disable toggle.
 *
 * When disabled, the LLM is never loaded during recording sessions,
 * saving memory and battery on low-end devices.
 *
 * @param enabled Current LLM enabled state
 * @param onEnabledChange Callback when the toggle changes
 */
@Composable
private fun LlmEnabledSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_llm_enabled_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_llm_enabled_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

/**
 * Translation target language setting with dropdown selector.
 *
 * @param currentLanguage Current target language code (e.g., "en", "it")
 * @param onLanguageChange Callback when language changes
 */
@Composable
private fun TranslationLanguageSetting(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    // Source of truth: SupportedLanguages.ALL (domain layer), already sorted alphabetically.
    // UI shows nativeName; stored value and LLM substitution use the BCP-47 code.
    val languages = com.hearopilot.app.domain.model.SupportedLanguages.ALL

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_translation_target_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.settings_translation_target_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = languages.find { it.code == currentLanguage }?.nativeName ?: currentLanguage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.nativeName) },
                        onClick = {
                            onLanguageChange(lang.code)
                            expanded = false
                        },
                        leadingIcon = if (lang.code == currentLanguage) {
                            { Icon(AppIcons.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                        } else null
                    )
                }
            }
        }
    }
}
