package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.RefundStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Credit cards and refunds. Same rules as the web app.
 *
 * Card due = everything charged to the card − refunds that came back to it − bills paid.
 * A card spend counts in full here (even a shared dinner): the card was charged the whole bill.
 * If you've paid more in bills than Ledger saw charged (interest, fees, spends you didn't log,
 * or a balance from before Ledger), the difference is shown as "not logged in Ledger".
 */

data class CardDue(val name: String, val charged: Double, val refunded: Double, val paid: Double) {
    val due: Double get() = maxOf(0.0, charged - refunded - paid)
    val notLogged: Double get() = maxOf(0.0, paid - (charged - refunded))
}

private fun Entry.isCardSpend(name: String) =
    type == EntryType.SPENT && mode.equals("Card", true) && card.equals(name, true)

/** Every card you've named, newest use first, plus any from settings. */
fun List<Entry>.cardNames(saved: Collection<String> = emptyList()): List<String> {
    val used = filter { !it.deleted }.sortedByDescending { it.updatedAt }
        .flatMap { listOfNotNull(it.card.takeIf(String::isNotBlank), it.refund?.card?.takeIf(String::isNotBlank)) }
    return (used + saved).distinctBy { it.lowercase() }
}

fun List<Entry>.cardDues(saved: Collection<String> = emptyList()): List<CardDue> {
    val live = filter { !it.deleted }
    return live.cardNames(saved).map { name ->
        CardDue(
            name,
            charged = live.filter { it.isCardSpend(name) }.sumOf { it.amount },
            refunded = live.filter { it.type == EntryType.SPENT }.mapNotNull { it.refund }
                .filter { it.status == RefundStatus.RECEIVED && it.to.equals("Card", true) && it.card.equals(name, true) }.sumOf { it.amount },
            paid = live.filter { it.type == EntryType.CARD_BILL && it.card.equals(name, true) }.sumOf { it.amount },
        )
    }
}

/** An expense whose refund is confirmed but hasn't arrived. */
data class AwaitedRefund(val entry: Entry, val amount: Double, val days: Long)

fun List<Entry>.refundsAwaited(today: LocalDate): List<AwaitedRefund> =
    filter { !it.deleted && it.type == EntryType.SPENT && it.refund?.status == RefundStatus.AWAITED }
        .map { AwaitedRefund(it, it.refund!!.amount, ChronoUnit.DAYS.between(it.refund.confirmed, today)) }
        .sortedByDescending { it.days }

/** Refunds still waiting after this many days get a nudge on Home. */
const val REFUND_NUDGE_DAYS = 7

/* ---------- matching bills against card spends ---------- */

/** A card's settings: the day its statement closes and the day its bill is due (1–31), if set. Synced as cfg-cards. */
data class CardSettings(val name: String, val statementDay: Int? = null, val dueDay: Int? = null)

/** The next date a card bill is due, on or after [today]. Short months use their last day. */
fun nextDueDate(dueDay: Int, today: LocalDate): LocalDate {
    val ym = java.time.YearMonth.from(today)
    val thisMonth = ym.atDay(minOf(dueDay, ym.lengthOfMonth()))
    if (!thisMonth.isBefore(today)) return thisMonth
    val next = ym.plusMonths(1)
    return next.atDay(minOf(dueDay, next.lengthOfMonth()))
}

/** What a bill should be compared with: the period it pays for. */
enum class Verdict { MATCHES, PAID_MORE, PAID_LESS, NOT_PAID, OPEN }

/**
 * One billing period of a card.
 * With a statement day: the statement period (16 Oct – 15 Nov), and the bills paid after it closed
 * and before the next one closed. Without one: from the day after one bill up to the next bill.
 * Charges count by the spend's date; refunds back to the card by the day they arrived.
 */
data class CardCycle(
    val from: LocalDate?, val to: LocalDate, val open: Boolean,
    val charges: List<Entry>, val charged: Double, val refunded: Double,
    val bills: List<Entry>, val paid: Double,
) {
    val logged: Double get() = charged - refunded
    val difference: Double get() = paid - logged
    val verdict: Verdict get() = when {
        open -> Verdict.OPEN
        bills.isEmpty() -> Verdict.NOT_PAID
        Math.abs(difference) <= 1.0 -> Verdict.MATCHES   // rounding on a statement
        difference > 0 -> Verdict.PAID_MORE
        else -> Verdict.PAID_LESS
    }
}

/** Day [day] of [ym], or its last day when the month is shorter (statement on the 31st in February). */
private fun closingIn(ym: java.time.YearMonth, day: Int): LocalDate = ym.atDay(minOf(day, ym.lengthOfMonth()))

/** Billing periods for one card, newest first, ending with the one still open today. */
fun List<Entry>.cardCycles(card: String, statementDay: Int?, today: LocalDate): List<CardCycle> {
    val live = filter { !it.deleted }
    val charges = live.filter { it.type == EntryType.SPENT && it.mode.equals("Card", true) && it.card.equals(card, true) }
    val refunds = live.filter { it.type == EntryType.SPENT }.mapNotNull { e -> e.refund?.takeIf {
        it.status == RefundStatus.RECEIVED && it.to.equals("Card", true) && it.card.equals(card, true) && it.received != null } }
    val bills = live.filter { it.type == EntryType.CARD_BILL && it.card.equals(card, true) }.sortedBy { it.date }
    if (charges.isEmpty() && bills.isEmpty()) return emptyList()

    fun cycle(from: LocalDate?, to: LocalDate, open: Boolean, paidBy: List<Entry>): CardCycle {
        fun inside(d: LocalDate) = (from == null || !d.isBefore(from)) && !d.isAfter(to)
        val ch = charges.filter { inside(it.date) }.sortedByDescending { it.date }
        return CardCycle(from, to, open, ch, ch.sumOf { it.amount }, refunds.filter { inside(it.received!!) }.sumOf { it.amount }, paidBy, paidBy.sumOf { it.amount })
    }

    val out = mutableListOf<CardCycle>()
    if (statementDay != null) {
        // start one statement before the earliest spend or bill: a bill pays the statement that closed before it
        val first = (charges.map { it.date } + bills.map { it.date }).min()
        var end = closingIn(java.time.YearMonth.from(first).minusMonths(1), statementDay).let { if (it.isBefore(first.minusMonths(1))) closingIn(java.time.YearMonth.from(first), statementDay) else it }
        while (true) {
            val start = closingIn(java.time.YearMonth.from(end).minusMonths(1), statementDay).plusDays(1)
            val nextEnd = closingIn(java.time.YearMonth.from(end).plusMonths(1), statementDay)
            val open = end.isAfter(today) || end == today
            // bills paid after this statement closed, before the next one closed
            val paidBy = bills.filter { it.date.isAfter(end) && !it.date.isAfter(nextEnd) }
            val c = cycle(start, end, open, if (open) emptyList() else paidBy)
            if (open || c.charges.isNotEmpty() || c.bills.isNotEmpty() || c.refunded > 0) out += c   // skip empty months
            if (open) break
            end = nextEnd
        }
    } else {
        var from: LocalDate? = null
        for (b in bills) {
            out += cycle(from, b.date, false, listOf(b))
            from = b.date.plusDays(1)
        }
        out += cycle(from, today, true, emptyList())
    }
    return out.reversed()
}

/** What the sheet suggests when you log a bill: the last closed period's logged total (or everything since the last bill). */
fun List<Entry>.suggestedBill(card: String, statementDay: Int?, today: LocalDate): CardCycle? {
    val cycles = cardCycles(card, statementDay, today)
    return if (statementDay != null) cycles.firstOrNull { !it.open && it.bills.isEmpty() && it.logged > 0 }
    else cycles.firstOrNull { it.open && it.logged > 0 }
}
