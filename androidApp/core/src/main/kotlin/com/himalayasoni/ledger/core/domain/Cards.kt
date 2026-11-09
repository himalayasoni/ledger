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
