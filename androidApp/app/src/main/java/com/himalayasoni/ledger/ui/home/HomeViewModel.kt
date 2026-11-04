package com.himalayasoni.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.data.EntryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Turns the live list of entries into Home's screen state. Survives screen rotation;
 * stops listening 5 s after the screen goes away (WhileSubscribed) to save battery.
 */
class HomeViewModel(repository: EntryRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = repository.observeEntries()
        .map { buildHomeState(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)
}
