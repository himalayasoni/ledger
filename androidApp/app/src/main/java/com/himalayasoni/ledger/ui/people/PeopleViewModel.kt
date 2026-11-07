package com.himalayasoni.ledger.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.domain.OldLoanIssue
import com.himalayasoni.ledger.core.domain.fixOldLoan
import com.himalayasoni.ledger.core.domain.markSharesPaid
import com.himalayasoni.ledger.core.domain.peopleIndex
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.data.VisibleEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A result to show as a toast, with an undo that restores the entries as they were. */
data class Undoable(val message: String, val undo: (() -> Unit)?)

class PeopleViewModel(visible: VisibleEntries, private val repository: EntryRepository) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val all = repository.observeEntries()

    val people: StateFlow<PeopleUiState?> = combine(visible.entries, all) { v, a -> buildPeopleState(v, a, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val person: StateFlow<PersonUiState?> = combine(selected, visible.entries, all) { name, v, a ->
        name?.let { buildPersonState(it, v, a, LocalDate.now()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(name: String) { selected.value = name }

    /** "Rahul paid their share": settles all their open shares. Reports back with an Undo. */
    fun markSharesPaid(name: String, onDone: (Undoable) -> Unit) = viewModelScope.launch {
        val entries = all.first()
        val (changed, total) = entries.markSharesPaid(name, System.currentTimeMillis())
        if (changed.isEmpty()) return@launch
        val before = entries.filter { e -> changed.any { it.id == e.id } }
        changed.forEach { repository.save(it) }
        onDone(Undoable("$name paid back ${formatRupees(total)}") { restore(before) })
    }

    /** "It was from before. Fix it". */
    fun fixOldLoan(issue: OldLoanIssue, onDone: (Undoable) -> Unit) = viewModelScope.launch {
        val entries = all.first()
        val changed = entries.fixOldLoan(issue, System.currentTimeMillis())
        if (changed.isEmpty()) return@launch
        changed.forEach { repository.save(it) }
        val after = all.first().peopleIndex()[issue.name.lowercase()]
        val msg = when {
            after == null || after.balance == 0.0 -> "Fixed. You and ${issue.name} are settled"
            after.balance > 0 -> "Fixed. ${after.name} owes you ${formatRupees(after.balance)}"
            else -> "Fixed. You owe ${after.name} ${formatRupees(after.balance)}"
        }
        onDone(Undoable(msg, null))
    }

    private fun restore(before: List<Entry>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        before.forEach { repository.save(it.copy(updatedAt = now)) }
    }
}
