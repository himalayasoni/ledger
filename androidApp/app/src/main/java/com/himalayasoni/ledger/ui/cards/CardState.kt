package com.himalayasoni.ledger.ui.cards

import com.himalayasoni.ledger.core.domain.CardCycle
import com.himalayasoni.ledger.core.domain.Verdict
import com.himalayasoni.ledger.core.domain.cardCycles
import com.himalayasoni.ledger.core.domain.cardDues
import com.himalayasoni.ledger.core.domain.suggestedBill
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.ui.home.EntryRow
import com.himalayasoni.ledger.ui.home.entryRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class VerdictTone { GOOD, MORE, LESS, NEUTRAL }

/** One billing period on the card page. */
data class CycleUi(
    val title: String,
    val logged: String,            // "Logged on card ₹3,500 · 3 spends"
    val refunds: String?,          // "Refunds back to card −₹200"
    val paid: String?,             // "Bill paid ₹3,300 · 20 Nov"
    val verdict: String,
    val tone: VerdictTone,
    val rows: List<EntryRow>,
)

data class CardUiState(
    val name: String,
    val due: String,
    val notLogged: String?,
    val statementDay: Int?,
    val cycles: List<CycleUi>,
    val dueDay: Int? = null,
    val nextDue: String? = null,      // "Bill due 3 Dec"
)

private val DM = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun ordinal(d: Int) = "$d" + when { d in 11..13 -> "th"; d % 10 == 1 -> "st"; d % 10 == 2 -> "nd"; d % 10 == 3 -> "rd"; else -> "th" }

private fun CardCycle.ui(statementDay: Int?): CycleUi {
    val title = when {
        statementDay != null -> "${from!!.format(DM)} – ${to.format(DM)}" + if (open) " · closes ${to.format(DM)}" else ""
        open && from == null -> "Everything so far"
        open -> "Since ${from!!.minusDays(1).format(DM)} bill"
        from == null -> "Up to the ${to.format(DM)} bill"
        else -> "${from!!.format(DM)} – ${to.format(DM)} bill"
    }
    val n = charges.size
    val (verdict, tone) = when (verdict) {
        Verdict.MATCHES -> "Matches what you logged" to VerdictTone.GOOD
        Verdict.PAID_MORE -> "Paid ${formatRupees(abs(difference))} more than logged: interest, fees, or a spend that isn't in Ledger" to VerdictTone.MORE
        Verdict.PAID_LESS -> "Paid ${formatRupees(abs(difference))} less than logged: a part payment, or spends that went on the next statement" to VerdictTone.LESS
        Verdict.NOT_PAID -> "No bill logged for this one yet" to VerdictTone.NEUTRAL
        Verdict.OPEN -> (if (statementDay != null) "Still open. The bill for this comes after ${to.format(DM)}" else "Not billed yet") to VerdictTone.NEUTRAL
    }
    return CycleUi(
        title,
        "Logged on card ${formatRupees(charged)} · $n spend${if (n == 1) "" else "s"}",
        if (refunded > 0) "Refunds back to card −${formatRupees(refunded)}" else null,
        if (bills.isEmpty()) null else "Bill paid ${formatRupees(paid)}" + bills.joinToString(", ", " · ") { it.date.format(DM) },
        verdict, tone, charges.map { entryRow(it) },
    )
}

/** Pure: entries + card + its statement day → the card page. */
fun buildCardState(entries: List<Entry>, name: String, statementDay: Int?, today: LocalDate, dueDay: Int? = null): CardUiState {
    val due = entries.cardDues(listOf(name)).firstOrNull { it.name.equals(name, true) }
    return CardUiState(
        name, formatRupees(due?.due ?: 0.0),
        due?.notLogged?.takeIf { it > 0 }?.let { "${formatRupees(it)} paid in bills that isn't logged in Ledger (fees, interest or older spends)" },
        statementDay,
        entries.cardCycles(name, statementDay, today).map { it.ui(statementDay) },
        dueDay, dueDay?.let { "Bill due " + com.himalayasoni.ledger.core.domain.nextDueDate(it, today).format(DM) },
    )
}

/** The line and amount offered when logging a bill for [card]. */
data class BillHint(val text: String, val amount: Double)

fun billHint(entries: List<Entry>, card: String, statementDay: Int?, today: LocalDate): BillHint? {
    val c = entries.suggestedBill(card, statementDay, today) ?: return null
    val text = if (statementDay != null) "Statement ${c.from!!.format(DM)} – ${c.to.format(DM)}: ${formatRupees(c.logged)} logged on $card"
    else "Logged on $card since your last bill: ${formatRupees(c.logged)}"
    return BillHint(text, c.logged)
}
