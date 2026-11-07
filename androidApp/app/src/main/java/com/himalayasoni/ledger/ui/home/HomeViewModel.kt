package com.himalayasoni.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.data.VisibleEntries
import com.himalayasoni.ledger.core.model.Entry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Turns the live list of entries into Home's screen state. Survives screen rotation;
 * stops listening 5 s after the screen goes away (WhileSubscribed) to save battery.
 */
class HomeViewModel(visible: VisibleEntries) : ViewModel() {
    @Volatile private var current: List<Entry> = emptyList()

    val state: StateFlow<HomeUiState> = visible.entries.map { entries ->
        current = entries
        buildHomeState(entries, LocalDate.now())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** The entry behind a row, to open it for editing. */
    fun entry(id: String): Entry? = current.firstOrNull { it.id == id }
}
