package com.hearopilot.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearopilot.app.domain.usecase.transcription.SearchTranscriptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTranscriptionsUseCase: SearchTranscriptionsUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")

    // Debounced results stream — DB is only queried 300 ms after the user stops typing
    private val _searchResults = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { q ->
            if (q.length < MIN_QUERY_LENGTH) {
                flowOf(Pair(emptyList<com.hearopilot.app.domain.model.SearchResult>(), false))
            } else {
                searchTranscriptionsUseCase(q).map { results -> Pair(results, true) }
            }
        }

    // Combine raw query (updates immediately on every keystroke) with debounced results so
    // the TextField always reflects what the user typed without lag.
    val uiState: StateFlow<SearchUiState> = combine(_query, _searchResults) { q, (results, hasSearched) ->
        SearchUiState(
            query = q,
            results = results,
            isSearching = false,
            hasSearched = hasSearched
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState()
        )

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun clearQuery() {
        _query.value = ""
    }

    companion object {
        // Debounce delay to avoid firing a DB query on every keystroke
        private const val SEARCH_DEBOUNCE_MS = 300L

        // Minimum query length to avoid broad full-table scans
        private const val MIN_QUERY_LENGTH = 2
    }
}
