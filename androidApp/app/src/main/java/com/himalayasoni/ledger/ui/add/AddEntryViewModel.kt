package com.himalayasoni.ledger.ui.add

import com.himalayasoni.ledger.core.domain.isSplit
import com.himalayasoni.ledger.core.domain.mainCategory
import com.himalayasoni.ledger.core.domain.cleanParts
import com.himalayasoni.ledger.core.model.Part
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
import com.himalayasoni.ledger.core.domain.categoriesByUse
import com.himalayasoni.ledger.core.domain.cardNames
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import com.himalayasoni.ledger.core.voice.VoiceContext
import com.himalayasoni.ledger.core.voice.parseLine
import java.time.LocalDate

/** Everything the sheet derives from your history, recomputed when entries change. */
/** A recent expense you can link money in to. */
data class LinkOption(val id: String, val title: String, val detail: String)

/** One part of a payment split across categories, as typed. A blank amount means "whatever's left". */
data class SplitPart(val amountText: String = "", val category: String = "") {
    val amount: Double get() = amountText.toDoubleOrNull() ?: 0.0
    val splitting: Boolean get() = splits.isNotEmpty()
    /** What the one blank part gets: the total less the typed parts (0 if none or more than one is blank). */
    val splitRest: Double get() = if (splits.count { it.amountText.isBlank() } == 1) maxOf(0.0, amount - splits.sumOf { it.amount }) else 0.0
    /** Total less every part, the blank one included: 0 when it adds up. */
    val splitLeft: Double get() = Math.round((amount - splits.sumOf { it.amount } - splitRest) * 100) / 100.0
}

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
    val people: List<String>, val voiceCats: List<String>, val cards: List<String>, val byUse: List<String>,
    val lastMode: String, val lastCard: String,
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
    val said: String? = null,     // a notification reply that needs finishing: fill the form from it
    val prefill: Entry? = null,   // "Duplicate": a new entry with these details (see core's duplicateAsNew)
    val mini: Boolean = false,    // the small quick add (amount, keypad, usuals); "More" opens the full form
    val scan: Scan? = null,       // a payment screenshot shared to Ledger, or the screen scanned
    val scanSource: String = "screenshot", // "screenshot" or "screen": only changes the wording
) {
    /** Where reading a shared screenshot is up to. */
    sealed interface Scan {
        data object Reading : Scan
        data class Read(val payment: com.himalayasoni.ledger.core.scan.ScannedPayment) : Scan
        data object NotAPayment : Scan
        data object Unreadable : Scan
    }
}

/** What the add sheet is showing right now. Immutable: every change makes a new copy. */
data class AddEntryState(
    val editing: Entry? = null,
    val type: EntryType = EntryType.SPENT,
    val typeTouched: Boolean = false,
    val amountText: String = "",
    val splits: List<SplitPart> = emptyList(), // a spend split across categories; empty = one category
    val activePart: Int = 0,                   // which part the category chips set
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
    // money in that was really for an expense: saved as money back on that expense instead of income
    val linkTo: String? = null,
    val linkable: List<LinkOption> = emptyList(),
    val billHints: Map<String, com.himalayasoni.ledger.ui.cards.BillHint> = emptyMap(),  // card name (lowercase) → what's due per Ledger
    val keypadOn: Boolean = true,
    val mini: Boolean = false,               // the small quick add layout
    val modeTouched: Boolean = false,        // picked a payment mode (or a usual did): don't replace it with the last used one
    val lastMode: String = "UPI",            // how your latest spend was paid (the mini quick add starts with it)
    val categoriesByUse: List<String> = emptyList(), // categories, most used in the last 90 days first
    val lastCard: String = "",
    val error: String? = null,
    val done: String? = null,                // set after saving/deleting: the sheet closes and shows this
    val heard: String? = null,               // the last thing said or typed in the say bar
    val heardNote: String? = null,           // "tap in the amount", "add who it was with"…
    val heardFrom: String? = null,           // set when the form was filled from a screenshot, not a line: "Paytm screenshot"
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

    /** The mini quick add's chips: your categories, the ones you use most first (private ones never). */
    val miniCategories: List<String> get() = categoriesByUse.ifEmpty { categories }.take(8)

    /**
     * The category the mini quick add saves with (it has no category chips): the one you used last time
     * for the same description, else what the voice parser makes of the description, else Other.
     * Only categories you can see here (never a private one).
     */
    val miniCategory: String get() {
        if (category.isNotBlank()) return category
        val key = description.trim().lowercase()
        if (key.isEmpty()) return "Other"
        allUsuals.firstOrNull { it.key == key && it.type == EntryType.SPENT && it.category.isNotBlank() }?.let { return it.category }
        val parsed = parseLine(description, VoiceContext(emptyList(), voiceCategories, allUsuals, LocalDate.now(), cards)).category
        return categories.firstOrNull { it.equals(parsed, true) } ?: "Other"
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
    /** The mini quick add's "More": the full form, keeping what's typed. */
    fun expand() {}
    /** Received: "this was for that expense" (null unlinks). */
    fun linkTo(id: String?)
    /** "Split into categories": this category becomes part one, and a second part is ready for the next. */
    fun startSplit() {}
    fun stopSplit() {}
    fun addPart() {}
    fun removePart(i: Int) {}
    /** Which part the category chips set (tapping a part, or typing its amount). */
    fun onPartActive(i: Int) {}
    fun onPartAmount(i: Int, text: String) {}
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
    private val historyLoaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Waits (up to 2 s) until usuals, names and categories have loaded, so a line can be parsed with them. */
    suspend fun awaitHistory() { kotlinx.coroutines.withTimeoutOrNull(2_000) { historyLoaded.await() } }

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
                val last = visible.filter { it.type == EntryType.SPENT && !it.deleted }.maxByOrNull { it.createdAt }
                VoiceInputs(visible.usuals(), visible.recentPeople(), cats, names, voiceCats, visible.cardNames(),
                    visible.categoriesByUse(LocalDate.now().minusDays(90), cats),
                    last?.mode?.ifBlank { null } ?: "UPI", if (last?.mode.equals("Card", true)) last?.card.orEmpty() else "")
            }.collect { v ->
                _state.update {
                    // the mini quick add starts with how you paid last time (history can arrive after it opens)
                    val useLast = it.mini && !it.modeTouched
                    it.copy(allUsuals = v.usuals, recentPeople = v.recent, categories = v.cats, voicePeople = v.people, voiceCategories = v.voiceCats,
                        categoriesByUse = v.byUse,
                        cards = (v.cards + it.cards).distinctBy { c -> c.lowercase() }, lastMode = v.lastMode, lastCard = v.lastCard,
                        mode = if (useLast) v.lastMode else it.mode, card = if (useLast) v.lastCard else it.card)
                }
                historyLoaded.complete(Unit)
            }
        }
    }

    fun start(req: AddRequest) = _state.update { s ->
        val fresh = AddEntryState(allUsuals = s.allUsuals, recentPeople = s.recentPeople, categories = s.categories, cards = s.cards, billHints = s.billHints,
            linkable = s.linkable, lastMode = s.lastMode, lastCard = s.lastCard,
            voicePeople = s.voicePeople, voiceCategories = s.voiceCategories)
        val e = req.edit ?: req.prefill
        when {
            e != null -> fresh.copy(
                editing = req.edit, type = e.type, typeTouched = true, amountText = plain(e.amount),
                description = e.description, person = if (e.type == EntryType.SPENT) "" else e.person,
                withPeople = e.people(), includesMe = e.includesMe, category = e.category,
                splits = if (e.isSplit) e.parts.map { SplitPart(plain(it.amount), it.category) } else emptyList(),
                mode = e.mode.ifBlank { "UPI" }, date = e.date, note = e.note, keypadOn = false,
                card = e.card, modeTouched = true,
            )
            req.type != null -> fresh.copy(
                type = req.type, typeTouched = true,
                person = if (req.type == EntryType.SPENT) "" else req.person.orEmpty(),
                withPeople = if (req.type == EntryType.SPENT) listOfNotNull(req.person) else emptyList(),
                amountText = req.amount?.let { plain(Math.abs(it)) }.orEmpty(),
                card = req.card.orEmpty(),
            )
            req.scan != null -> fromScan(fresh.copy(mini = true, mode = s.lastMode, card = s.lastCard), req.scan, req.scanSource)
            req.mini -> fresh.copy(mini = true, mode = s.lastMode, card = s.lastCard)
            else -> fresh
        }
    }

    /**
     * Fill the pop-up from a screenshot. The UPI reference goes in the comment ("UPI ref 4330…"): it's on your
     * bank statement too, and it's how a second share of the same screenshot is spotted.
     */
    private fun fromScan(base: AddEntryState, scan: AddRequest.Scan, source: String): AddEntryState = when (scan) {
        AddRequest.Scan.Reading -> base.copy(heardFrom = "your $source", heard = "", heardNote = "Reading it…", keypadOn = false)
        AddRequest.Scan.NotAPayment -> base.copy(heardFrom = "your $source", heard = "",
            heardNote = if (source == "screen") "No amount found. Some payment screens block capture; take a screenshot and share it instead, or type it in."
            else "No amount found on it. Type it in, or share the payment's done screen.")
        AddRequest.Scan.Unreadable -> base.copy(heardFrom = "your $source", heard = "",
            heardNote = if (source == "screen") "Couldn't capture the screen this time. Try again, or share a screenshot."
            else "Couldn't read it. Text reading may still be downloading; try again in a minute.")
        is AddRequest.Scan.Read -> {
            val p = scan.payment
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
            val dupe = p.ref?.let { ref -> latest.firstOrNull { !it.deleted && it.note.contains(ref) } }
            val known = p.party?.let { who -> base.voicePeople.firstOrNull { n -> n.equals(who, true) || n.equals(who.substringBefore(' '), true) } }
            val notes = buildList {
                if (p.failed) add("it says the payment failed")
                else if (p.pending) add("it says the payment is pending")
                dupe?.let { add("already logged: ${it.description.ifBlank { it.category }} on ${it.date.format(fmt)}") }
                if (known != null && !p.received) add("$known is in your People: if it was a loan, tap More")
            }
            base.copy(
                type = if (p.received) EntryType.RECEIVED else EntryType.SPENT, typeTouched = true,
                amountText = p.amount?.let(::plain).orEmpty(),
                description = if (p.received) "" else p.party.orEmpty(),
                person = if (p.received) (known ?: p.party.orEmpty()) else "",
                date = p.date ?: LocalDate.now(),
                note = p.ref?.let { "UPI ref $it" }.orEmpty(),
                mode = if (p.viaCreditCard) "Card" else "UPI", modeTouched = true,
                keypadOn = p.amount == null,
                heardFrom = (p.app ?: "payment") + " " + source, heard = "",
                heardNote = if (notes.isEmpty()) "Check it, pick a category and tap Save." else notes.joinToString(" · ").replaceFirstChar { it.uppercase() },
            )
        }
    }

    override fun onType(t: EntryType) = _state.update { it.copy(type = t, typeTouched = true, error = null) }
    override fun onCategory(c: String) = _state.update {
        if (it.splitting) it.copy(splits = it.splits.mapIndexed { i, p ->
            if (i == it.activePart) p.copy(category = if (p.category == c) "" else c) else p
        }, error = null)
        else it.copy(category = if (it.category == c) "" else c)
    }

    override fun startSplit() = _state.update {
        it.copy(splits = listOf(SplitPart("", it.category), SplitPart()), activePart = 1, error = null)
    }
    /** Back to one category: the biggest part's (or the one you were on). */
    override fun stopSplit() = _state.update {
        val keep = it.splits.maxByOrNull { p -> p.amount }?.category?.ifBlank { null } ?: it.splits.getOrNull(it.activePart)?.category.orEmpty()
        it.copy(splits = emptyList(), activePart = 0, category = keep, error = null)
    }
    override fun addPart() = _state.update { it.copy(splits = it.splits + SplitPart(), activePart = it.splits.size, error = null) }
    override fun removePart(i: Int) = _state.update {
        val left = it.splits.filterIndexed { j, _ -> j != i }
        if (left.size < 2) it.copy(splits = emptyList(), activePart = 0, category = left.firstOrNull()?.category.orEmpty())
        else it.copy(splits = left, activePart = it.activePart.coerceAtMost(left.size - 1))
    }
    override fun onPartActive(i: Int) = _state.update { it.copy(activePart = i) }
    override fun onPartAmount(i: Int, text: String) = _state.update {
        val t = text.filter { c -> c.isDigit() || c == '.' }.let { v -> if (v.count { c -> c == '.' } > 1) v.dropLast(1) else v }.take(9)
        it.copy(splits = it.splits.mapIndexed { j, p -> if (j == i) p.copy(amountText = t) else p }, activePart = i, error = null)
    }
    override fun onDescription(v: String) = _state.update { it.copy(description = v) }
    override fun onMode(m: String) = _state.update { it.copy(mode = m, modeTouched = true) }
    // keeps everything typed; the category the mini layout would have used shows as picked
    override fun expand() = _state.update { it.copy(mini = false, category = if (it.description.isNotBlank()) it.miniCategory else it.category) }
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
            category = u.category, mode = u.mode.ifBlank { s.mode }, modeTouched = true,
            card = if (u.mode.equals("Card", true)) u.card.ifBlank { s.card } else s.card,
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
        }
        // a split: one blank part takes what's left; every part needs a category; they must add up to the total
        val parts: List<Part> = if (s.type != EntryType.SPENT || !s.splitting) emptyList() else {
            val filled = s.splits.map { p -> Part(if (p.amountText.isBlank()) s.splitRest else p.amount, p.category) }.filter { it.amount > 0 }
            when {
                s.splits.count { it.amountText.isBlank() } > 1 -> { _state.update { it.copy(error = "Type each part's amount (one can stay blank for the rest)") }; return }
                filled.any { it.category.isBlank() } -> { _state.update { it.copy(error = "Pick a category for each part") }; return }
            }
            cleanParts(filled, Math.round(s.amount * 100) / 100.0) ?: run {
                val left = s.splitLeft
                _state.update { it.copy(error = if (left > 0) "The parts are ${formatRupees(left)} short of ${formatRupees(s.amount)}" else "The parts are ${formatRupees(-left)} more than ${formatRupees(s.amount)}") }
                return
            }
        }
        val now = System.currentTimeMillis()
        val base = s.editing
        if (s.type == EntryType.RECEIVED && s.linkTo != null) { saveAsMoneyBack(s, now); return }
        val entry = Entry(
            id = base?.id ?: newEntryId(now), type = s.type, amount = Math.round(s.amount * 100) / 100.0, date = s.date,
            description = s.description.trim(),
            person = if (s.type == EntryType.SPENT) "" else s.person.trim(),
            parts = parts,
            category = when {
                !s.type.hasCategory -> ""
                parts.isNotEmpty() -> mainCategory(parts)
                s.mini -> s.miniCategory
                else -> s.category.ifBlank { if (s.type == EntryType.SPENT) "Other" else "" }
            },
            mode = if (s.type == EntryType.CARD_BILL && s.mode.equals("Card", true)) "UPI" else s.mode, note = s.note.trim(),
            card = when {
                s.type == EntryType.CARD_BILL -> s.card
                s.type == EntryType.SPENT && s.mode.equals("Card", true) -> s.card
                else -> ""
            },
            // money back is changed from the entry's page now, never here: an edit keeps it as it was
            // (cut down if the amount went below it), and drops it if this is no longer a spend
            refund = base?.refund?.takeIf { s.type == EntryType.SPENT && base.type == EntryType.SPENT }
                ?.let { r -> if (r.amount > Math.round(s.amount * 100) / 100.0) r.copy(amount = Math.round(s.amount * 100) / 100.0) else r },
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
                // the mini quick add is for plain spends: anything else (a loan, a split, a card bill) needs the full form
                mini = s.mini && r.type == EntryType.SPENT && r.withPeople.isEmpty(),
                modeTouched = s.modeTouched || r.mode.isNotBlank(),
                heard = line, heardFrom = null,
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


    override fun addCategory(name: String) {
        val n = name.trim().replace(Regex("\\s+"), " ").take(24).replaceFirstChar { it.uppercase() }
        if (n.isEmpty()) return
        val existing = _state.value.categories.firstOrNull { it.equals(n, true) }
        if (existing == null) sync.addCategory(n)
        _state.update {
            val cats = if (existing == null) it.categories + n else it.categories
            if (it.splitting) it.copy(categories = cats, splits = it.splits.mapIndexed { i, p -> if (i == it.activePart) p.copy(category = existing ?: n) else p })
            else it.copy(category = existing ?: n, categories = cats)
        }
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
