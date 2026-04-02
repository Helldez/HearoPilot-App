package com.hearopilot.app.presentation.licenses

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for the Open Source Licenses screen.
 *
 * Manages the expand/collapse state of each license card.
 * The license data itself is static and lives in [com.hearopilot.app.domain.model.AppLicenses].
 */
@HiltViewModel
class LicensesViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(LicensesUiState())
    val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    /** Toggle the full-text expansion of the card identified by [entryId]. */
    fun toggleExpanded(entryId: String) {
        _uiState.update { current ->
            val newExpanded = if (entryId in current.expandedIds) {
                current.expandedIds - entryId
            } else {
                current.expandedIds + entryId
            }
            current.copy(expandedIds = newExpanded)
        }
    }
}
