package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import java.time.LocalDate

/*
 * Things you do to an entry after it's logged: money back, "it arrived", duplicate.
 * The web app has the same four rules with the same names; keep them in step.
 * Every rule returns a new entry with [Entry.updatedAt] = now (newer wins when syncing).
 */

private fun round2(v: Double) = Math.round(v * 100) / 100.0

/**
 * Money back on a spend: a shop refund ([from] = "") or a person paying you back for it ([from] = their name).
 *
 * - [amount] is capped at the entry's amount (₹1,500 ticket, at most ₹1,500 back), rounded to paise.
 * - Awaited ([arrived] false): no received date, no "came back to".
 * - Arrived: [received] (or [confirmed] if not given), [to] ("Card", "UPI", "Bank", "Cash") and,
 *   for "Card", [card] or the entry's own card.
 * - Only spends get money back, and only a positive amount: anything else returns the entry unchanged.
 */
fun Entry.withMoneyBack(
    amount: Double,
    from: String,
    arrived: Boolean,
    confirmed: LocalDate,
    received: LocalDate?,
    to: String,
    card: String,
    now: Long,
): Entry {
    if (type != EntryType.SPENT || amount <= 0) return this
    val back = minOf(round2(amount), this.amount)
    val toCard = arrived && to.equals("Card", true)
    return copy(
        refund = Refund(
            amount = back,
            status = if (arrived) RefundStatus.RECEIVED else RefundStatus.AWAITED,
            confirmed = confirmed,
            received = if (arrived) received ?: confirmed else null,
            to = if (arrived) to else "",
            card = if (toCard) card.ifBlank { this.card } else "",
            from = from.trim(),
        ),
        updatedAt = now,
    )
}

/**
 * "Mark arrived": an awaited refund came in today. Keeps where it was going to come back if that
 * was set; otherwise a card spend comes back to the card (the entry's card), anything else to the
 * mode it was paid with (or UPI). Returns the entry unchanged when there's nothing awaited.
 */
fun Entry.markRefundArrived(today: LocalDate, now: Long): Entry {
    val r = refund ?: return this
    if (r.status != RefundStatus.AWAITED) return this
    val to = r.to.ifBlank { if (mode.equals("Card", true)) "Card" else mode.ifBlank { "UPI" } }
    return copy(
        refund = r.copy(
            status = RefundStatus.RECEIVED,
            received = today,
            to = to,
            card = if (to.equals("Card", true)) r.card.ifBlank { card } else "",
        ),
        updatedAt = now,
    )
}

/**
 * "Duplicate": the same spend again, as a new entry today. Keeps what it was (amount, description,
 * category, payment mode, card, person, who it was shared with and whether you had a share); drops
 * what belonged to that one time: money back, who paid back their share, an old-loan part, the comment.
 */
fun Entry.duplicateAsNew(id: String, today: LocalDate, now: Long): Entry = copy(
    id = id,
    date = today,
    refund = null,
    paidBack = emptyMap(),
    prior = 0.0,
    note = "",
    createdAt = now,
    updatedAt = now,
    deleted = false,
)

/** "Remove money back": the spend counts at its full amount again. */
fun Entry.withoutMoneyBack(now: Long): Entry = if (refund == null) this else copy(refund = null, updatedAt = now)
