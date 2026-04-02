package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hearopilot.app.ui.R
import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.*

/**
 * Custom dialog for creating a new transcription session.
 *
 * Layout: gradient header with title + close icon, scrollable body with:
 * output language selector (first), topic/name field, mode cards,
 * insight strategy chips, and primary CTA.
 *
 * Output language defaults to the device locale if it is among the 25 supported
 * languages, or falls back to English. The user can always override it.
 * For REAL_TIME_TRANSLATION the language selector acts as the translation target
 * and is seeded from [AppSettings.translationTargetLanguage].
 *
 * @param onDismiss Called when user dismisses the dialog.
 * @param onConfirm Called when user confirms; receives (sessionName, mode, outputLanguage, insightStrategy, topic).
 *                  outputLanguage is always a non-null BCP-47 code (never empty).
 * @param settings Current app settings used to show the insight interval per mode and
 *                 to seed the default language selection for translation mode.
 */
@Composable
fun NewSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?, RecordingMode, String?, InsightStrategy, String?) -> Unit,
    settings: AppSettings = AppSettings()
) {
    val languages = com.hearopilot.app.domain.model.SupportedLanguages.ALL
    val supportedCodes = remember { languages.map { it.code }.toSet() }

    // Resolve the device locale to a supported BCP-47 code (e.g. "it"), with "en" fallback.
    val deviceLanguageCode = remember {
        val lang = java.util.Locale.getDefault().language
        if (lang in supportedCodes) lang else "en"
    }

    // Single combined input: used as both session name and topic
    var sessionTopic by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(RecordingMode.SIMPLE_LISTENING) }
    // Always a valid BCP-47 code — device locale for analysis modes, translation target for translation mode.
    var outputLanguage by remember { mutableStateOf(deviceLanguageCode) }
    // Default strategy for the selected mode, updated when mode changes.
    var insightStrategy by remember {
        mutableStateOf(settings.simpleListeningDefaultStrategy)
    }

    // When mode changes: update language default and strategy default.
    LaunchedEffect(selectedMode) {
        outputLanguage = if (selectedMode == RecordingMode.REAL_TIME_TRANSLATION) {
            settings.translationTargetLanguage
        } else {
            deviceLanguageCode
        }
        insightStrategy = when (selectedMode) {
            RecordingMode.SIMPLE_LISTENING      -> settings.simpleListeningDefaultStrategy
            RecordingMode.SHORT_MEETING         -> settings.shortMeetingDefaultStrategy
            RecordingMode.LONG_MEETING          -> settings.longMeetingDefaultStrategy
            RecordingMode.REAL_TIME_TRANSLATION -> settings.translationDefaultStrategy
        }
    }

    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column {
                // ── Gradient header ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(BrandPurpleDark)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.new_session_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.new_session_recording_mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
                        }
                    }
                }

                // ── Scrollable body ──────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Language selector — shown first so the user picks the output language
                    // before choosing a mode. For REAL_TIME_TRANSLATION it is the translation
                    // target; for all other modes it selects which locale's system prompt is used.
                    LanguageSelector(
                        label = if (selectedMode == RecordingMode.REAL_TIME_TRANSLATION) {
                            stringResource(R.string.new_session_output_language)
                        } else {
                            stringResource(R.string.new_session_output_language_label)
                        },
                        languages = languages,
                        selectedCode = outputLanguage,
                        showAutoOption = false,
                        onLanguageSelected = { outputLanguage = it }
                    )

                    // Combined name/topic field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = sessionTopic,
                            onValueChange = { sessionTopic = it },
                            label = { Text(stringResource(R.string.new_session_name_label)) },
                            placeholder = { Text(stringResource(R.string.new_session_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        if (settings.llmEnabled) {
                            Text(
                                text = stringResource(R.string.session_topic_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mode cards
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecordingMode.values().forEach { mode ->
                            val intervalLabel = if (settings.llmEnabled) {
                                if (insightStrategy == InsightStrategy.END_OF_SESSION && mode == selectedMode) {
                                    stringResource(R.string.insight_strategy_batch)
                                } else {
                                    when (mode) {
                                        RecordingMode.SIMPLE_LISTENING      -> "${settings.simpleListeningIntervalSeconds}s"
                                        RecordingMode.SHORT_MEETING         -> "${settings.shortMeetingIntervalSeconds}s"
                                        RecordingMode.LONG_MEETING          -> "${settings.longMeetingIntervalMinutes}min"
                                        RecordingMode.REAL_TIME_TRANSLATION -> "${settings.translationIntervalSeconds}s"
                                    }
                                }
                            } else null

                            ModeCard(
                                mode = mode,
                                isSelected = mode == selectedMode,
                                onClick = { selectedMode = mode },
                                insightIntervalLabel = intervalLabel
                            )
                        }
                    }

                    // Insight strategy selector — shown only when LLM is enabled.
                    if (settings.llmEnabled) {
                        InsightStrategySelector(
                            selected = insightStrategy,
                            onStrategySelected = { insightStrategy = it }
                        )
                    }

                    // Primary CTA
                    com.hearopilot.app.ui.components.GradientButton(
                        text = stringResource(R.string.start),
                        onClick = {
                            val langArg = outputLanguage.ifEmpty { null }
                            val nameAndTopic = sessionTopic.trim().takeIf { it.isNotBlank() }
                            onConfirm(
                                nameAndTopic,
                                selectedMode,
                                langArg,
                                insightStrategy,
                                nameAndTopic
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Selectable card for a recording mode.
 * Optionally shows an insight-interval badge when the LLM is active.
 */
@Composable
private fun ModeCard(
    mode: RecordingMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    insightIntervalLabel: String? = null
) {
    val accentColor = when (mode) {
        RecordingMode.SIMPLE_LISTENING      -> ModeSkyBlueTint
        RecordingMode.SHORT_MEETING         -> ModeShortMeetingTint
        RecordingMode.LONG_MEETING          -> ModeAmberTint
        RecordingMode.REAL_TIME_TRANSLATION -> ModeEmeraldTint
    }
    val borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface

    val icon = when (mode) {
        RecordingMode.SIMPLE_LISTENING      -> AppIcons.ModeSimpleListening
        RecordingMode.SHORT_MEETING         -> AppIcons.ModeShortMeeting
        RecordingMode.LONG_MEETING          -> AppIcons.ModeLongMeeting
        RecordingMode.REAL_TIME_TRANSLATION -> AppIcons.ModeTranslation
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (isSelected) accentColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (mode) {
                    RecordingMode.SIMPLE_LISTENING      -> stringResource(R.string.mode_simple_listening)
                    RecordingMode.SHORT_MEETING         -> stringResource(R.string.mode_short_meeting)
                    RecordingMode.LONG_MEETING          -> stringResource(R.string.mode_long_meeting)
                    RecordingMode.REAL_TIME_TRANSLATION -> stringResource(R.string.mode_translation_live)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (mode) {
                    RecordingMode.SIMPLE_LISTENING      -> stringResource(R.string.mode_simple_listening_desc)
                    RecordingMode.SHORT_MEETING         -> stringResource(R.string.mode_short_meeting_desc)
                    RecordingMode.LONG_MEETING          -> stringResource(R.string.mode_long_meeting_desc)
                    RecordingMode.REAL_TIME_TRANSLATION -> stringResource(R.string.mode_translation_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Insight interval badge — shown only when LLM is active
            if (insightIntervalLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Schedule,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.new_session_insight_interval, insightIntervalLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isSelected) {
            Icon(
                imageVector = AppIcons.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Two-chip selector for the insight generation strategy.
 */
@Composable
private fun InsightStrategySelector(
    selected: InsightStrategy,
    onStrategySelected: (InsightStrategy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.insight_strategy_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == InsightStrategy.REAL_TIME,
                onClick = { onStrategySelected(InsightStrategy.REAL_TIME) },
                label = { Text(stringResource(R.string.insight_strategy_realtime)) },
                leadingIcon = {
                    Icon(AppIcons.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            FilterChip(
                selected = selected == InsightStrategy.END_OF_SESSION,
                onClick = { onStrategySelected(InsightStrategy.END_OF_SESSION) },
                label = { Text(stringResource(R.string.insight_strategy_batch)) },
                leadingIcon = {
                    Icon(AppIcons.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
fun LanguageSelector(
    languages: List<com.hearopilot.app.domain.model.SupportedLanguage>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    label: String = stringResource(R.string.new_session_output_language),
    showAutoOption: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    val autoLabel = stringResource(R.string.new_session_language_auto)
    val displayName = if (selectedCode.isEmpty()) autoLabel
                      else languages.find { it.code == selectedCode }?.nativeName ?: selectedCode

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(displayName, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                if (showAutoOption) {
                    DropdownMenuItem(
                        text = { Text(autoLabel) },
                        onClick = { onLanguageSelected(""); expanded = false },
                        leadingIcon = if (selectedCode.isEmpty()) {
                            { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    HorizontalDivider()
                }
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.nativeName) },
                        onClick = { onLanguageSelected(lang.code); expanded = false },
                        leadingIcon = if (lang.code == selectedCode) {
                            { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}
