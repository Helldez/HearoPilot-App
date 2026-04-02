package com.hearopilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.components.GradientButton
import com.hearopilot.app.ui.ui.theme.BrandPrimary

/**
 * Returns the flag emoji for a BCP-47 language code using Unicode regional indicator symbols.
 *
 * Maps each code to its primary country so the flag emoji is unambiguous.
 * Regional indicators are Unicode code points U+1F1E6–U+1F1FF; two combined
 * characters (e.g. U+1F1EE U+1F1F9 = 🇮🇹) form a single flag emoji.
 */
private fun languageFlagEmoji(code: String): String {
    val countryCode = when (code) {
        "bg" -> "BG"
        "cs" -> "CZ"
        "da" -> "DK"
        "de" -> "DE"
        "el" -> "GR"
        "en" -> "GB"
        "es" -> "ES"
        "et" -> "EE"
        "fi" -> "FI"
        "fr" -> "FR"
        "hr" -> "HR"
        "hu" -> "HU"
        "it" -> "IT"
        "lt" -> "LT"
        "lv" -> "LV"
        "mt" -> "MT"
        "nl" -> "NL"
        "pl" -> "PL"
        "pt" -> "PT"
        "ro" -> "RO"
        "ru" -> "RU"
        "sk" -> "SK"
        "sl" -> "SI"
        "sv" -> "SE"
        "uk" -> "UA"
        else -> code.uppercase()
    }
    // Convert two-letter country code to regional indicator symbols
    val offset = 0x1F1E6 - 'A'.code
    return countryCode.map { char ->
        String(Character.toChars(char.code + offset))
    }.joinToString("")
}

/**
 * Onboarding screen that showcases the 25 supported languages.
 *
 * Purely informational — no selection required. Displays each language as a
 * flag emoji plus its native name in a scrollable grid. The user taps "Continue"
 * to proceed to the STT download step.
 *
 * @param onContinue Callback when user taps the Continue button
 */
@Composable
fun SupportedLanguagesScreen(
    onContinue: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Extra clearance for the floating back-arrow (8 dp offset + 48 dp button = 56 dp from safe area top)
            Spacer(modifier = Modifier.height(72.dp))

            Text(
                text = stringResource(R.string.onboarding_languages_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_languages_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SupportedLanguages.ALL) { language ->
                    LanguageCell(
                        flag = languageFlagEmoji(language.code),
                        name = language.nativeName
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GradientButton(
                text = stringResource(R.string.onboarding_languages_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )

            // Extra clearance for the floating page-dot indicator (10dp dots + 24dp padding = ~34dp from bottom)
            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

@Composable
private fun LanguageCell(flag: String, name: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = flag,
                fontSize = 28.sp
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
