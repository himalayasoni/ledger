package com.himalayasoni.ledger.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.domain.newEntryId
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the add sheet is showing right now. Immutable: every change makes a new copy. */
data class AddEntryState(
    val type: EntryType = EntryType.SPENT,
    val amountText: String = "",
    val category: String = "",
    val description: String = "",
    val person: String = "",
    val mode: String = "UPI",
    val date: LocalDate = LocalDate.now(),
    val error: String? = null,
    val saved: Boolean = false,
) {
    val amount: Double get() = amountText.toDoubleOrNull() ?: 0.0
}

/**
 * Holds the add sheet's state and rules (keypad limits, validation, saving).
 * The sheet only sends events in ("key 5 pressed") and draws the state that comes out.
 */
class AddEntryViewModel(private val repository: EntryRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddEntryState())
    val state: StateFlow<AddEntryState> = _state.asStateFlow()

    fun reset() { _state.value = AddEntryState() }
    fun onType(t: EntryType) = _state.update { it.copy(type = t, error = null) }
    fun onCategory(c: String) = _state.update { it.copy(category = if (it.category == c) "" else c) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onPerson(v: String) = _state.update { it.copy(person = v, error = null) }
    fun onMode(m: String) = _state.update { it.copy(mode = m) }
    fun onDate(d: LocalDate) = _state.update { it.copy(date = d) }

    /** Same keypad rules as the web app: max 2 decimals, max 9 digits. */
    fun onKey(key: String) = _state.update { s ->
        val t = s.amountText
        val next = when (key) {
            "del" -> t.dropLast(1)
            "." -> if (t.contains('.')) t else (t.ifEmpty { "0" } + ".")
            else -> when {
                t.contains('.') && t.substringAfter('.').length >= 2 -> t
                !t.contains('.') && t.trimStart('0').length >= 9 -> t
                t == "0" -> key
                else -> t + key
            }
        }
        s.copy(amountText = next, error = null)
    }

    fun save() {
        val s = _state.value
        when {
            s.amount <= 0 -> { _state.update { it.copy(error = "Type the amount first") }; return }
            s.type.isDebt && s.person.isBlank() -> { _state.update { it.copy(error = "Add who this was with, so the balance adds up") }; return }
        }
        val now = System.currentTimeMillis()
        val entry = Entry(
            id = newEntryId(now), type = s.type, amount = Math.round(s.amount * 100) / 100.0, date = s.date,
            description = s.description.trim(), person = if (s.type == EntryType.SPENT) "" else s.person.trim(),
            category = if (s.type.isDebt) "" else s.category.ifBlank { if (s.type == EntryType.SPENT) "Other" else "" },
            mode = s.mode, createdAt = now, updatedAt = now,
        )
        viewModelScope.launch {
            repository.save(entry)
            _state.update { it.copy(saved = true) }
        }
    }
}
