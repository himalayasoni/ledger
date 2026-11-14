package com.himalayasoni.ledger.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.domain.cardNames
import com.himalayasoni.ledger.core.domain.duplicateAsNew
import com.himalayasoni.ledger.core.domain.markRefundArrived
import com.himalayasoni.ledger.core.domain.net
import com.himalayasoni.ledger.core.domain.newEntryId
import com.himalayasoni.ledger.core.domain.recentPeople
import com.himalayasoni.ledger.core.domain.withMoneyBack
import com.himalayasoni.ledger.core.domain.withoutMoneyBack
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.data.VisibleEntries
import com.himalayasoni.ledger.ui.people.Undoable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The open entry page: which id, and its content (null once the entry is gone, e.g. deleted elsewhere). */
data class EntryPageState(val id: String, val detail: EntryDetail?)

/**
 * The entry page, and everything you can do to an entry after logging it (from its page or a row's
 * long-press): money back, mark arrived, duplicate, delete. Each change goes through the repository,
 * so it syncs, and comes with an Undo that puts the entry back as it was (with a fresh updatedAt,
 * so the restored copy wins on the other devices too).
 */
class EntryViewModel(visible: VisibleEntries, private val repository: EntryRepository) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    @Volatile private var latest: List<Entry> = emptyList()

    init { viewModelScope.launch { visible.entries.collect { latest = it } } }

    val page: StateFlow<EntryPageState?> = combine(visible.entries, selected) { entries, id ->
        id?.let { EntryPageState(it, entries.firstOrNull { e -> e.id == it }?.let { e -> buildEntryDetail(e, entries, LocalDate.now()) }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(id: String?) { selected.value = id }

    fun entry(id: String): Entry? = latest.firstOrNull { it.id == id }

    /** The Money back card for an entry, filled in from its money back if it has some. */
    fun moneyBackForm(id: String): MoneyBackForm? {
        val e = entry(id) ?: return null
        return moneyBackForm(e, latest.recentPeople(8), latest.cardNames(), LocalDate.now())
    }

    /** The entry as a new one for today, for the add sheet ("Duplicate"). */
    fun duplicate(id: String): Entry? {
        val now = System.currentTimeMillis()
        return entry(id)?.duplicateAsNew(newEntryId(now), LocalDate.now(), now)
    }

    fun markArrived(id: String, onDone: (Undoable) -> Unit) = change(id, onDone) { e, now ->
        val after = e.markRefundArrived(LocalDate.now(), now)
        after to (if (e.refund?.from?.isNotBlank() == true) "Money back marked arrived" else "Refund marked arrived")
    }

    fun saveMoneyBack(f: MoneyBackForm, onDone: (Undoable) -> Unit) = change(f.entryId, onDone) { e, now ->
        val after = e.withMoneyBack(f.amount, f.from, f.arrived, f.confirmed, f.received, f.to, f.card, now)
        after to "${formatRupees(after.refund?.amount ?: 0.0)} back on ${e.description.ifBlank { e.category }} · counts as ${formatRupees(after.net())} spent"
    }

    fun removeMoneyBack(id: String, onDone: (Undoable) -> Unit) = change(id, onDone) { e, now ->
        e.withoutMoneyBack(now) to "Money back removed"
    }

    fun delete(id: String, onDone: (Undoable) -> Unit) = viewModelScope.launch {
        val before = entry(id) ?: return@launch
        repository.delete(id)
        onDone(Undoable("Deleted") { restore(before) })
    }

    private fun change(id: String, onDone: (Undoable) -> Unit, make: (Entry, Long) -> Pair<Entry, String>) = viewModelScope.launch {
        val before = entry(id) ?: return@launch
        val (after, message) = make(before, System.currentTimeMillis())
        if (after === before) return@launch
        repository.save(after)
        onDone(Undoable(message) { restore(before) })
    }

    private fun restore(before: Entry) = viewModelScope.launch {
        repository.save(before.copy(deleted = false, updatedAt = System.currentTimeMillis()))
    }
}
