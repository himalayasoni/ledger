package com.himalayasoni.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.withoutPrivate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Turns the live list of entries into Home's screen state. Survives screen rotation;
 * stops listening 5 s after the screen goes away (WhileSubscribed) to save battery.
 */
class HomeViewModel(repository: EntryRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(repository.observeEntries(), settings.privateCategories) { entries, private ->
        buildHomeState(entries.withoutPrivate(private), LocalDate.now())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)
}
