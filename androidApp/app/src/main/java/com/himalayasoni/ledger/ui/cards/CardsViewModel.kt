package com.himalayasoni.ledger.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.data.VisibleEntries
import com.himalayasoni.ledger.data.sync.FirestoreSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** The card page: which card is open, its periods, and setting its statement day. */
class CardsViewModel(visible: VisibleEntries, settings: SettingsRepository, private val sync: FirestoreSync) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)

    val card: StateFlow<CardUiState?> = combine(selected, visible.entries, settings.cardSettings) { name, entries, cards ->
        name?.let { n ->
            val set = cards.firstOrNull { it.name.equals(n, true) }
            buildCardState(entries, n, set?.statementDay, LocalDate.now(), set?.dueDay)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(name: String) { selected.value = name }
    fun setStatementDay(name: String, day: Int?) = sync.setStatementDay(name, day)
    fun setDueDay(name: String, day: Int?) = sync.setDueDay(name, day)
}
