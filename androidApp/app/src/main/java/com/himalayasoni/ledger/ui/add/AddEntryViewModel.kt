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
import com.himalayasoni.ledger.core.domain.peopleIndex
import com.himalayasoni.ledger.core.domain.allCategories
import com.himalayasoni.ledger.core.domain.cardNames
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import com.himalayasoni.ledger.core.voice.VoiceContext
import com.himalayasoni.ledger.core.voice.parseLine
import java.time.LocalDate

/** Everything the sheet derives from your history, recomputed when entries change. */
/** A recent expense you can link money in to. */
data class LinkOption(val id: String, val title: String, val detail: String)

/** One key press applied to the amount being typed. */
internal fun nextAmount(t: String, key: String): String = when (key) {
    "del" -> t.dropLast(1)
    "." -> if (t.contains('.')) t else (t.ifEmpty { "0" } + ".")
    else -> when {
        t.contains('.') && t.substringAfter('.').length >= 2 -> t
        !t.contains('.') && t.trimStart('0').length >= 9 -> t
        t == "0" -> key
        else -> t + key
    }
}

private data class VoiceInputs(
    val usuals: List<Usual>, val recent: List<String>, val cats: List<String>,
    val people: List<String>, val voiceCats: List<String>, val cards: List<String>,
)

/** How the sheet was opened: plain +, a quick action (type preset), or tapping an entry (edit). */
data class AddRequest(
    val type: EntryType? = null,
    val withPeople: Boolean = false,
    val edit: Entry? = null,
    val person: String? = null,   // from a person's page: "Lend" to Rahul
    val amount: Double? = null,   // "Got it back": the full balance
    val listen: Boolean = false,  // "Say it": start voice input straight away
    val card: String? = null,     // "Log a bill payment" on a card page
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
    val card: String = "",                   // which card, for a card spend or a card bill
    val cards: List<String> = emptyList(),   // your named cards
    // refund (spends only)
    val refundOn: Boolean = false,
    val refundFull: Boolean = true,
    val refundText: String = "",             // partial amount, as typed
    val refundArrived: Boolean = false,
    val refundConfirmed: LocalDate = LocalDate.now(),
    val refundReceived: LocalDate = LocalDate.now(),
    val refundTo: String = "",               // "Card", "UPI", "Bank", "Cash"
    val refundCard: String = "",
    val refundFrom: String = "",             // "" = a shop/service refund; a name = someone paid you back
    // money in that was really for an expense: saved as money back on that expense instead of income
    val linkTo: String? = null,
    val linkable: List<LinkOption> = emptyList(),
    val billHints: Map<String, com.himalayasoni.ledger.ui.cards.BillHint> = emptyMap(),  // card name (lowercase) → what's due per Ledger
    val keypadOn: Boolean = true,
    val error: String? = null,
    val done: String? = null,                // set after saving/deleting: the sheet closes and shows this
    val heard: String? = null,               // the last thing said or typed in the say bar
    val heardNote: String? = null,           // "tap in the amount", "add who it was with"…
    val voicePeople: List<String> = emptyList(),
    val voiceCategories: List<String> = DEFAULT_CATEGORIES,
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
    fun onType(t: EntryType)
    fun onKey(key: String)
    fun onDescription(v: String)
    fun pickUsual(u: Usual)
    fun onPerson(v: String)
    fun addPerson(name: String)
    fun removePerson(name: String)
    fun onIncludesMe(v: Boolean)
    fun onCategory(c: String)
    fun onMode(m: String)
    fun onDate(d: LocalDate)
    fun onNote(v: String)
    fun setKeypad(on: Boolean)
    fun save()
    fun delete()
    /** A line from the say bar or the microphone: "lent 200 to Ashish". */
    fun applySaid(text: String)
    /** "+ New" in the category row: adds the category everywhere and picks it. */
    fun addCategory(name: String)
    /** Pick (or add) the credit card for a card spend or a card bill. */
    fun onCard(name: String)
    /** "Use ₹12,340": put Ledger's logged total in as the bill amount. */
    fun useAmount(amount: Double)
    /** Refund on a spend: turn it on/off, full or partial, amount, awaited or arrived, where it came back. */
    fun onRefund(change: (AddEntryState) -> AddEntryState)
    /** Received: "this was for that expense" (null unlinks). */
    fun linkTo(id: String?)
}

/**
 * Holds the add sheet's state and rules (keypad limits, validation, saving).
 * The sheet only sends events in ("key 5 pressed") and draws the state that comes out.
 */
class AddEntryViewModel(
    private val repository: EntryRepository,
    settings: SettingsRepository,
    visible: com.himalayasoni.ledger.data.VisibleEntries,
    private val sync: com.himalayasoni.ledger.data.sync.FirestoreSync,
) : ViewModel(), AddEntryActions {
    private val _state = MutableStateFlow(AddEntryState())
    @Volatile private var latest: List<Entry> = emptyList()
    val state: StateFlow<AddEntryState> = _state.asStateFlow()

    init {
        // what Ledger thinks each card's next bill is (shown when you log a card bill)
        viewModelScope.launch {
            combine(visible.entries, settings.cardSettings) { entries, cards ->
                val today = LocalDate.now()
                entries.cardNames(cards.map { it.name }).mapNotNull { name ->
                    val day = cards.firstOrNull { it.name.equals(name, true) }?.statementDay
                    com.himalayasoni.ledger.ui.cards.billHint(entries, name, day, today)?.let { name.lowercase() to it }
                }.toMap()
            }.collect { h -> _state.update { it.copy(billHints = h) } }
        }
        // recent expenses that money in can be linked to (no money back on them yet)
        viewModelScope.launch {
            visible.entries.collect { entries ->
                latest = entries
                val since = LocalDate.now().minusDays(120)
                val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
                val options = entries.filter { it.type == EntryType.SPENT && !it.deleted && it.refund == null && !it.date.isBefore(since) }
                    .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }).take(30)
                    .map { e -> LinkOption(e.id, e.description.ifBlank { e.category.ifBlank { "Expense" } }, "${formatRupees(e.amount)} · ${e.date.format(fmt)}") }
                _state.update { it.copy(linkable = options) }
            }
        }
        // keep usuals, names and categories in step with your entries (private ones left out)
        viewModelScope.launch {
            combine(
                visible.entries, settings.privateCategories, settings.showPrivate, settings.customCategories, settings.removedCategories,
            ) { visible, private, show, custom, removed ->
                val hidden = if (show) emptySet() else private.map { it.lowercase() }.toSet()
                val used = visible.filter { !it.type.isDebt && it.category.isNotBlank() }.map { it.category }
                // built-in (minus removed), your own, then any others on your entries; private ones only when showing
                val cats = visible.allCategories(custom, removed, if (show) private else emptySet()).filter { it.lowercase() !in hidden }
                // what the voice parser knows about you: same rules as the web (allPeople(), allCats())
                val names = visible.peopleIndex().values.sortedByDescending { it.lastDate }.map { it.name }
                val voiceCats = (used + (if (show) emptySet() else private) + DEFAULT_CATEGORIES).distinctBy { it.lowercase() }
                VoiceInputs(visible.usuals(), visible.recentPeople(), cats, names, voiceCats, visible.cardNames())
            }.collect { v ->
                _state.update {
                    it.copy(allUsuals = v.usuals, recentPeople = v.recent, categories = v.cats, voicePeople = v.people, voiceCategories = v.voiceCats,
                        cards = (v.cards + it.cards).distinctBy { c -> c.lowercase() })
                }
            }
        }
    }

    fun start(req: AddRequest) = _state.update { s ->
        val fresh = AddEntryState(allUsuals = s.allUsuals, recentPeople = s.recentPeople, categories = s.categories, cards = s.cards, billHints = s.billHints,
            linkable = s.linkable,
            voicePeople = s.voicePeople, voiceCategories = s.voiceCategories)
        val e = req.edit
        when {
            e != null -> fresh.copy(
                editing = e, type = e.type, typeTouched = true, amountText = plain(e.amount),
                description = e.description, person = if (e.type == EntryType.SPENT) "" else e.person,
                withPeople = e.people(), includesMe = e.includesMe, category = e.category,
                mode = e.mode.ifBlank { "UPI" }, date = e.date, note = e.note, keypadOn = false,
                card = e.card,
                refundOn = e.refund != null, refundFull = e.refund?.let { it.amount >= e.amount } ?: true,
                refundText = e.refund?.let { plain(it.amount) }.orEmpty(),
                refundArrived = e.refund?.status == RefundStatus.RECEIVED,
                refundConfirmed = e.refund?.confirmed ?: LocalDate.now(),
                refundReceived = e.refund?.received ?: LocalDate.now(),
                refundTo = e.refund?.to.orEmpty(), refundCard = e.refund?.card.orEmpty(), refundFrom = e.refund?.from.orEmpty(),
            )
            req.type != null -> fresh.copy(
                type = req.type, typeTouched = true,
                person = if (req.type == EntryType.SPENT) "" else req.person.orEmpty(),
                withPeople = if (req.type == EntryType.SPENT) listOfNotNull(req.person) else emptyList(),
                amountText = req.amount?.let { plain(Math.abs(it)) }.orEmpty(),
                card = req.card.orEmpty(),
            )
            else -> fresh
        }
    }

    override fun onType(t: EntryType) = _state.update { it.copy(type = t, typeTouched = true, error = null) }
    override fun onCategory(c: String) = _state.update { it.copy(category = if (it.category == c) "" else c) }
    override fun onDescription(v: String) = _state.update { it.copy(description = v) }
    override fun onMode(m: String) = _state.update { it.copy(mode = m) }
    override fun onDate(d: LocalDate) = _state.update { it.copy(date = d) }
    override fun onNote(v: String) = _state.update { it.copy(note = v) }
    override fun onIncludesMe(v: Boolean) = _state.update { it.copy(includesMe = v) }
    override fun setKeypad(on: Boolean) = _state.update { it.copy(keypadOn = on) }

    /** For a shared expense, a comma finishes a name; otherwise it's the one person. */
    override fun onPerson(v: String) = _state.update { s ->
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

    override fun removePerson(name: String) = _state.update { s -> s.copy(withPeople = s.withPeople - name) }

    /** One tap on a usual fills in everything from the last time. */
    override fun pickUsual(u: Usual) = _state.update { s ->
        s.copy(
            type = u.type, typeTouched = true, amountText = plain(u.amount), description = u.description,
            category = u.category, mode = u.mode.ifBlank { s.mode },
            person = if (u.type == EntryType.SPENT) "" else u.person,
            withPeople = if (u.type == EntryType.SPENT) u.withPeople else emptyList(),
            includesMe = u.includesMe, error = null,
        )
    }

    /** Same keypad rules as the web app: max 2 decimals, max 9 digits. "00" is two zeros (not on an empty amount). */
    override fun onKey(key: String) = _state.update { s ->
        val next = if (key == "00") (if (s.amountText.isEmpty() || s.amountText == "0") s.amountText else nextAmount(nextAmount(s.amountText, "0"), "0"))
        else nextAmount(s.amountText, key)
        s.copy(amountText = next, error = null)
    }

    override fun save() {
        val s = _state.value
        val withPeople = if (s.type == EntryType.SPENT) (s.withPeople + s.person.trim()).filter { it.isNotBlank() }.distinctBy { it.lowercase() } else emptyList()
        when {
            s.amount <= 0 -> { _state.update { it.copy(error = "Type the amount first", keypadOn = true) }; return }
            s.type.isDebt && s.person.isBlank() -> { _state.update { it.copy(error = "Add who this was with, so the balance adds up") }; return }
            s.type == EntryType.CARD_BILL && s.card.isBlank() -> { _state.update { it.copy(error = "Pick which card this bill is for") }; return }
            s.type == EntryType.SPENT && s.refundOn && refundAmount(s) <= 0 -> { _state.update { it.copy(error = "Type how much is coming back") }; return }
        }
        val now = System.currentTimeMillis()
        val base = s.editing
        if (s.type == EntryType.RECEIVED && s.linkTo != null) { saveAsMoneyBack(s, now); return }
        val entry = Entry(
            id = base?.id ?: newEntryId(now), type = s.type, amount = Math.round(s.amount * 100) / 100.0, date = s.date,
            description = s.description.trim(),
            person = if (s.type == EntryType.SPENT) "" else s.person.trim(),
            category = if (!s.type.hasCategory) "" else s.category.ifBlank { if (s.type == EntryType.SPENT) "Other" else "" },
            mode = if (s.type == EntryType.CARD_BILL && s.mode.equals("Card", true)) "UPI" else s.mode, note = s.note.trim(),
            card = when {
                s.type == EntryType.CARD_BILL -> s.card
                s.type == EntryType.SPENT && s.mode.equals("Card", true) -> s.card
                else -> ""
            },
            refund = if (s.type == EntryType.SPENT && s.refundOn) Refund(
                amount = minOf(refundAmount(s), Math.round(s.amount * 100) / 100.0),
                status = if (s.refundArrived) RefundStatus.RECEIVED else RefundStatus.AWAITED,
                confirmed = s.editing?.refund?.confirmed ?: s.refundConfirmed,
                received = if (s.refundArrived) s.refundReceived else null,
                to = if (s.refundArrived) s.refundTo else "",
                card = if (s.refundArrived && s.refundTo.equals("Card", true)) s.refundCard.ifBlank { s.card } else "",
                from = s.refundFrom.trim(),
            ) else null,
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

    override fun linkTo(id: String?) = _state.update { it.copy(linkTo = id, error = null) }

    /**
     * Money in that was for an expense ("Dad gave me 1,500 for the treat"): instead of income, it
     * becomes money back on that expense, so the expense's month shows what it really cost you.
     */
    private fun saveAsMoneyBack(s: AddEntryState, now: Long) {
        val target = latest.firstOrNull { it.id == s.linkTo }
        if (target == null) { _state.update { it.copy(error = "That expense isn't there any more. Pick another.", linkTo = null) }; return }
        val back = minOf(Math.round(s.amount * 100) / 100.0, target.amount)
        val updated = target.copy(
            refund = Refund(
                amount = back, status = RefundStatus.RECEIVED, confirmed = s.date, received = s.date,
                to = if (s.mode.equals("Card", true)) "Card" else s.mode, from = s.person.trim(),
            ),
            updatedAt = now,
        )
        viewModelScope.launch {
            repository.save(updated)
            s.editing?.let { repository.delete(it.id) }   // an income entry being turned into money back
            val name = target.description.ifBlank { target.category }
            _state.update { it.copy(done = "${formatRupees(back)} back on $name · counts as ${formatRupees(maxOf(0.0, target.amount - back))} spent") }
        }
    }

    /** Same as the web's applyParsed(): fill the form from what was heard, then say what's missing. */
    override fun applySaid(text: String) {
        val line = text.trim()
        if (line.isEmpty()) return
        _state.update { s ->
            val today = LocalDate.now() // one "today" for both the parse and the date, even at midnight
            val r = parseLine(line, VoiceContext(s.voicePeople, s.voiceCategories, s.allUsuals, today, s.cards))
            val notes = buildList {
                if (r.amount <= 0) add("tap in the amount")
                if (r.type.isDebt && r.person.isBlank()) add("add who it was with")
                if (r.type == EntryType.CARD_BILL && r.card.isBlank()) add("pick which card")
            }
            s.copy(
                type = r.type, typeTouched = true,
                amountText = if (r.amount > 0) plain(r.amount) else s.amountText,
                description = r.description,
                withPeople = if (r.type == EntryType.SPENT) r.withPeople else emptyList(),
                person = if (r.type == EntryType.SPENT) "" else r.person,
                category = r.category.ifBlank { s.category },
                mode = r.mode.ifBlank { s.mode },
                date = r.date(today),
                card = r.card.ifBlank { s.card },
                cards = if (r.card.isNotBlank() && s.cards.none { it.equals(r.card, true) }) s.cards + r.card else s.cards,
                keypadOn = r.amount <= 0,
                heard = line,
                heardNote = if (notes.isEmpty()) "Check it and tap Save." else notes.joinToString(" · "),
                error = null,
            )
        }
    }

    override fun onCard(name: String) {
        val n = name.trim().replace(Regex("\\s+"), " ").take(20)
        if (n.isEmpty()) return
        _state.update { st ->
            val existing = st.cards.firstOrNull { it.equals(n, true) }
            st.copy(card = existing ?: n, cards = if (existing == null) st.cards + n else st.cards, error = null)
        }
    }

    override fun useAmount(amount: Double) = _state.update { it.copy(amountText = plain(amount), error = null) }

    override fun onRefund(change: (AddEntryState) -> AddEntryState) = _state.update { change(it).copy(error = null) }

    /** Full refund = the whole amount; partial = what was typed. */
    private fun refundAmount(s: AddEntryState): Double = if (s.refundFull) s.amount else s.refundText.toDoubleOrNull() ?: 0.0

    override fun addCategory(name: String) {
        val n = name.trim().replace(Regex("\\s+"), " ").take(24).replaceFirstChar { it.uppercase() }
        if (n.isEmpty()) return
        val existing = _state.value.categories.firstOrNull { it.equals(n, true) }
        if (existing == null) sync.addCategory(n)
        _state.update { it.copy(category = existing ?: n, categories = if (existing == null) it.categories + n else it.categories) }
    }

    override fun delete() {
        val id = _state.value.editing?.id ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(done = "Entry deleted") }
        }
    }

    private fun plain(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
}
