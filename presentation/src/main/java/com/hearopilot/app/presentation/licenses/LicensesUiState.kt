package com.hearopilot.app.presentation.licenses

import com.hearopilot.app.domain.model.AppLicenses
import com.hearopilot.app.domain.model.LicenseEntry

/**
 * UI state for the Open Source Licenses screen.
 *
 * @param licenses Ordered list of all license entries.
 * @param expandedIds Set of entry IDs whose full text is currently visible.
 */
data class LicensesUiState(
    val licenses: List<LicenseEntry> = AppLicenses.ALL,
    val expandedIds: Set<String> = AppLicenses.ALL
        .filter { it.expandedByDefault }
        .map { it.id }
        .toSet()
)
