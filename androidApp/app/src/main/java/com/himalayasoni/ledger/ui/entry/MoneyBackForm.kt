package com.himalayasoni.ledger.ui.entry

import com.himalayasoni.ledger.core.domain.people
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.RefundStatus
import com.himalayasoni.ledger.core.money.formatRupees
import java.time.LocalDate

/** The four places money can come back to. */
val CAME_BACK_TO = listOf("Card", "UPI", "Bank", "Cash")

/**
 * The "Money back" card's form. Immutable and pure: every tap makes a new copy, so it can be
 * screenshot-tested and the rules (defaults, the cap) live in one place.
 */
data class MoneyBackForm(
    val entryId: String,
    val title: String,               // "Train to Jaipur"
    val entryAmount: Double,
    val entryMode: String,
    val entryCard: String,
    val shared: List<String>,        // who the spend is split with (they use "paid their share" instead)
    val people: List<String>,        // recent names to pick from, without [shared]
    val cards: List<String>,
    val existing: Boolean,           // editing money back that's already there: offer "Remove"
    val confirmed: LocalDate,
    val received: LocalDate,
    val from: String = "",           // "" = a shop refund
    val full: Boolean = true,
    val partText: String = "",
    val arrived: Boolean = false,
    val to: String = "",
    val card: String = "",
    val error: String? = null,
) {
    /** What comes back: the whole amount, or the part typed (never more than was paid). */
    val amount: Double get() = if (full) entryAmount else minOf(partText.toDoubleOrNull() ?: 0.0, entryAmount)

    /** "Counts as ₹180 spent". */
    val countsAs: String get() =
        "Counts as ${formatRupees(maxOf(0.0, entryAmount - amount))} spent" +
            if (!arrived && amount > 0) " · ${formatRupees(amount)} shows as awaited until it arrives" else ""

    /** Where it comes back by default: a card spend to its card, otherwise how it was paid (or UPI). */
    private val defaultTo: String get() = if (entryMode.equals("Card", true)) "Card" else entryMode.takeIf { it in CAME_BACK_TO } ?: "UPI"

    fun pickFrom(name: String): MoneyBackForm =
        // a person paying you back has usually already paid: switch to Arrived (you can switch back)
        (if (name.isBlank()) copy(from = "") else copy(from = name.trim(), arrived = true)).filledIn()

    fun pickFull(on: Boolean) = copy(full = on, error = null)

    /** The part's box: digits and one dot, and never more than the amount. */
    fun typePart(text: String): MoneyBackForm {
        val t = text.filter { it.isDigit() || it == '.' }.take(10)
        val v = t.toDoubleOrNull()
        return copy(partText = if (v != null && v > entryAmount) plain(entryAmount) else t, full = false, error = null)
    }

    fun pickArrived(on: Boolean) = copy(arrived = on).filledIn()
    fun pickReceived(d: LocalDate) = copy(received = d)
    fun pickTo(t: String) = copy(to = t, card = if (t == "Card") card.ifBlank { entryCard } else card)
    fun pickCard(name: String) = copy(card = name)

    private fun filledIn() = if (arrived && to.isBlank()) pickTo(defaultTo) else this

    /** Checked before saving; null when it's fine. */
    fun problem(): String? = if (amount <= 0) "Type how much came back" else null
}

private fun plain(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')

/** Pure: the form for an entry, from its money back if it has some, else Shop refund · Full · Awaited. */
fun moneyBackForm(e: Entry, recentPeople: List<String>, cards: List<String>, today: LocalDate): MoneyBackForm {
    val shared = e.people()
    val taken = shared.map { it.lowercase() }.toSet()
    val r = e.refund
    val names = (listOfNotNull(r?.from?.takeIf { it.isNotBlank() }) + recentPeople)
        .distinctBy { it.lowercase() }.filter { it.lowercase() !in taken && it.isNotBlank() }
    val base = MoneyBackForm(
        entryId = e.id, title = e.description.ifBlank { e.category.ifBlank { "Expense" } },
        entryAmount = e.amount, entryMode = e.mode, entryCard = e.card,
        shared = shared, people = names,
        cards = (cards + listOfNotNull(e.card.takeIf { it.isNotBlank() }, r?.card?.takeIf { it.isNotBlank() })).distinctBy { it.lowercase() },
        existing = r != null, confirmed = r?.confirmed ?: today, received = r?.received ?: today,
    )
    if (r == null) return base
    return base.copy(
        from = r.from, full = r.amount >= e.amount, partText = if (r.amount >= e.amount) "" else plain(r.amount),
        arrived = r.status == RefundStatus.RECEIVED, to = r.to, card = r.card,
    )
}
