package com.hearopilot.app.domain.model

/**
 * A third-party component that the app must acknowledge for license compliance.
 *
 * @param id Stable identifier used as Compose key.
 * @param componentName Display name of the library or model.
 * @param licenseType Human-readable license name (e.g. "MIT", "Apache 2.0", "CC BY 4.0").
 * @param shortNotice Optional attribution or notice text shown directly in the UI. Empty = hidden.
 * @param fullText Complete license text shown when the user expands the card.
 * @param primaryUrl Main URL (license page or project homepage).
 * @param secondaryUrl Optional second link (e.g. model card on Hugging Face).
 * @param githubUrl Optional GitHub repository link shown as a dedicated "GitHub" chip.
 * @param expandedByDefault True for entries with the strongest legal obligations
 *                          (Gemma, Parakeet) so they are visible without interaction.
 */
data class LicenseEntry(
    val id: String,
    val componentName: String,
    val licenseType: String,
    val shortNotice: String,
    val fullText: String,
    val primaryUrl: String,
    val secondaryUrl: String? = null,
    val githubUrl: String? = null,
    val expandedByDefault: Boolean = false
)
