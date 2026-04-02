package com.hearopilot.app.presentation.search

import com.hearopilot.app.domain.model.SearchResult

/**
 * UI state for the search screen.
 *
 * @param query       Current text in the search bar.
 * @param results     List of search results for the current query.
 * @param isSearching True while a debounce delay is pending (spinner hint).
 * @param hasSearched True once at least one search has been dispatched (distinguishes
 *                    "idle/empty query" from "searched but nothing found").
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false
)
