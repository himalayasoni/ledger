package com.himalayasoni.ledger.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.Usual
import com.himalayasoni.ledger.core.domain.matching
import com.himalayasoni.ledger.core.domain.newEntryId
import com.himalayasoni.ledger.core.domain.people
import com.himalayasoni.ledger.core.domain.recentPeople
import com.himalayasoni.ledger.core.domain.top
import com.himalayasoni.ledger.core.domain.usuals
import com.himalayasoni.ledger.core.domain.withoutPrivate
import com.himalayasoni.ledger.core.model.DEFAULT_CATEGORIES
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.money.formatRupees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How the sheet was opened: plain +, a quick action (type preset), or tapping an entry (edit). */
data class AddRequest(
    val type: EntryType? = null,
    val withPeople: Boolean = false,
    val edit: Entry? = null,
)

/** What the add sheet is showing right now. Immutable: every change makes a new copy. */
data class AddEntryState(
    val editing: Entry? = null,
    val type: EntryType = EntryType.SPENT,
    val typeTouched: Boolean = false,
    val amountText: String = "",
    val description: String = "",
    val person: String = "",                 // one name (loans, received) or the name being typed (spent)
    val withPeople: List<String> = emptyList(),
    val includesMe: Boolean = true,
    val category: String = "",
    val mode: String = "UPI",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val keypadOn: Boolean = true,
    val error: String? = null,
    val done: String? = null,                // set after saving/deleting: the sheet closes and shows this
    // from your history
    val allUsuals: List<Usual> = emptyList(),
    val recentPeople: List<String> = emptyList(),
    val categories: List<String> = DEFAULT_CATEGORIES,
) {
    val amount: Double get() = amountText.toDoubleOrNull() ?: 0.0

    /** "Your usuals · one tap": only on a fresh, untouched entry. */
    val usuals: List<Usual> get() =
        if (editing != null || description.isNotBlank() || amountText.isNotEmpty()) emptyList()
        else allUsuals.top(if (typeTouched) type else null)

    /** Suggestions while typing the description. */
    val descriptionMatches: List<Usual> get() = allUsuals.matching(description)

    /** "+ Rahul" chips: recent names not already picked. */
    val personSuggestions: List<String> get() {
        val taken = (withPeople + person).map { it.trim().lowercase() }.toSet()
        return recentPeople.filter { it.lowercase() !in taken }
    }

    /** "Split 3 ways: ₹400 each" for a shared expense. */
    val splitHint: String? get() {
        if (type != EntryType.SPENT || withPeople.isEmpty()) return null
        val ways = withPeople.size + if (includesMe) 1 else 0
        return if (amount > 0) "Split $ways ways: ${formatRupees(amount / ways)} each" else "Split $ways ways"
    }
}

/** Everything the add sheet can ask for. The sheet depends on this, not on the ViewModel, so it can be previewed and screenshot-tested with a no-op. */
interface AddEntryActions {
    override fun onType(t: EntryType)
    override fun onKey(key: String)
    override fun onDescription(v: String)
    override fun pickUsual(u: Usual)
    override fun onPerson(v: String)
    fun addPerson(name: String)
    override fun removePerson(name: String)
    override fun onIncludesMe(v: Boolean)
    override fun onCategory(c: String)
    override fun onMode(m: String)
    override fun onDate(d: LocalDate)
    override fun onNote(v: String)
    override fun setKeypad(on: Boolean)
    override fun save()
    override fun delete()
}

/**
 * Holds the add sheet's state and rules (keypad limits, validation, saving).
 * The sheet only sends events in ("key 5 pressed") and draws the state that comes out.
 */
class AddEntryViewModel(
    private val repository: EntryRepository,
    settings: SettingsRepository,
) : ViewModel(), AddEntryActions {
    private val _state = MutableStateFlow(AddEntryState())
    val state: StateFlow<AddEntryState> = _state.asStateFlow()

    init {
        // keep usuals, names and categories in step with your entries (private ones left out)
        viewModelScope.launch {
            combine(repository.observeEntries(), settings.privateCategories) { all, private ->
                val visible = all.withoutPrivate(private)
                val hidden = private.map { it.lowercase() }.toSet()
                val used = visible.filter { !it.type.isDebt && it.category.isNotBlank() }.map { it.category }
                val cats = (DEFAULT_CATEGORIES + used).distinctBy { it.lowercase() }.filter { it.lowercase() !in hidden }
                Triple(visible.usuals(), visible.recentPeople(), cats)
            }.collect { (u, p, cats) ->
                _state.update { it.copy(allUsuals = u, recentPeople = p, categories = cats) }
            }
        }
    }

    fun start(req: AddRequest) = _state.update { s ->
        val fresh = AddEntryState(allUsuals = s.allUsuals, recentPeople = s.recentPeople, categories = s.categories)
        val e = req.edit
        when {
            e != null -> fresh.copy(
                editing = e, type = e.type, typeTouched = true, amountText = plain(e.amount),
                description = e.description, person = if (e.type == EntryType.SPENT) "" else e.person,
                withPeople = e.people(), includesMe = e.includesMe, category = e.category,
                mode = e.mode.ifBlank { "UPI" }, date = e.date, note = e.note, keypadOn = false,
            )
            req.type != null -> fresh.copy(type = req.type, typeTouched = true)
            else -> fresh
        }
    }

    fun onType(t: EntryType) = _state.update { it.copy(type = t, typeTouched = true, error = null) }
    fun onCategory(c: String) = _state.update { it.copy(category = if (it.category == c) "" else c) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onMode(m: String) = _state.update { it.copy(mode = m) }
    fun onDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }
    fun onIncludesMe(v: Boolean) = _state.update { it.copy(includesMe = v) }
    fun setKeypad(on: Boolean) = _state.update { it.copy(keypadOn = on) }

    /** For a shared expense, a comma finishes a name; otherwise it's the one person. */
    fun onPerson(v: String) = _state.update { s ->
        if (s.type == EntryType.SPENT && v.contains(',')) {
            val names = v.split(',')
            val done = names.dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
            s.copy(withPeople = (s.withPeople + done).distinctBy { it.lowercase() }, person = names.last().trimStart(), error = null)
        } else s.copy(person = v, error = null)
    }

    /** "Done" on the keyboard, or tapping a "+ Name" chip. */
    override fun addPerson(name: String) = _state.update { s ->
        val n = name.trim()
        when {
            n.isEmpty() -> s
            s.type == EntryType.SPENT -> s.copy(withPeople = (s.withPeople + n).distinctBy { it.lowercase() }, person = "")
            else -> s.copy(person = n, error = null)
        }
    }

    fun removePerson(name: String) = _state.update { s -> s.copy(withPeople = s.withPeople - name) }

    /** One tap on a usual fills in everything from the last time. */
    fun pickUsual(u: Usual) = _state.update { s ->
        s.copy(
            type = u.type, typeTouched = true, amountText = plain(u.amount), description = u.description,
            category = u.category, mode = u.mode.ifBlank { s.mode },
            person = if (u.type == EntryType.SPENT) "" else u.person,
            withPeople = if (u.type == EntryType.SPENT) u.withPeople else emptyList(),
            includesMe = u.includesMe, error = null,
        )
    }

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
        val withPeople = if (s.type == EntryType.SPENT) (s.withPeople + s.person.trim()).filter { it.isNotBlank() }.distinctBy { it.lowercase() } else emptyList()
        when {
            s.amount <= 0 -> { _state.update { it.copy(error = "Type the amount first", keypadOn = true) }; return }
            s.type.isDebt && s.person.isBlank() -> { _state.update { it.copy(error = "Add who this was with, so the balance adds up") }; return }
        }
        val now = System.currentTimeMillis()
        val base = s.editing
        val entry = Entry(
            id = base?.id ?: newEntryId(now), type = s.type, amount = Math.round(s.amount * 100) / 100.0, date = s.date,
            description = s.description.trim(),
            person = if (s.type == EntryType.SPENT) "" else s.person.trim(),
            category = if (s.type.isDebt) "" else s.category.ifBlank { if (s.type == EntryType.SPENT) "Other" else "" },
            mode = s.mode, note = s.note.trim(),
            withPeople = withPeople, includesMe = if (withPeople.isEmpty()) true else s.includesMe,
            paidBack = if (s.type == EntryType.SPENT && withPeople.isNotEmpty()) base?.paidBack.orEmpty() else emptyMap(),
            prior = if (s.type.isDebt) base?.prior ?: 0.0 else 0.0,
            createdAt = base?.createdAt ?: now, updatedAt = now,
        )
        viewModelScope.launch {
            repository.save(entry)
            _state.update { it.copy(done = if (base != null) "Changes saved" else "Logged ${formatRupees(entry.amount)}") }
        }
    }

    fun delete() {
        val id = _state.value.editing?.id ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(done = "Entry deleted") }
        }
    }

    private fun plain(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
}
