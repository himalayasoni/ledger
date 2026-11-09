package com.himalayasoni.ledger.core.model

import java.time.LocalDate

/**
 * One line in your ledger. The fields mirror what the web app stores in Firestore,
 * so both apps can read each other's entries.
 *
 * @property withPeople people a shared expense was with ("Spent with")
 * @property includesMe whether you also had a share of a shared expense
 * @property paidBack how much each person (lowercase name) paid you back for their share
 * @property prior part of a repayment that settled a loan from before Ledger existed
 * @property deleted deletions are kept as markers ("tombstones") so they can sync to other devices
 */
enum class RefundStatus(val key: String) {
    /** Confirmed by the shop or IRCTC, but the money hasn't arrived yet. */
    AWAITED("awaited"),
    RECEIVED("received");

    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.key == k } }
}

/**
 * Money coming back on an expense. As soon as it's confirmed the expense counts at its net cost
 * (₹1,500 ticket − ₹1,320 refund = ₹180), while the refund shows as awaited until it arrives.
 * @property to where it came back: "Card", "UPI", "Bank", "Cash" (with [card] when it's a card)
 */
data class Refund(
    val amount: Double,
    val status: RefundStatus,
    val confirmed: LocalDate,
    val received: LocalDate? = null,
    val to: String = "",
    val card: String = "",
)

data class Entry(
    val id: String,
    val type: EntryType,
    val amount: Double,
    val date: LocalDate,
    val description: String = "",
    val person: String = "",
    val category: String = "",
    val mode: String = "UPI",
    val note: String = "",
    val withPeople: List<String> = emptyList(),
    val includesMe: Boolean = true,
    val paidBack: Map<String, Double> = emptyMap(),
    val prior: Double = 0.0,
    /** A full or partial refund on an expense (cancelled ticket, returned order). */
    val refund: Refund? = null,
    /** Which credit card: on a card spend (mode "Card") or a card bill. */
    val card: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Categories a new install starts with. */
val DEFAULT_CATEGORIES = listOf(
    "Food", "Groceries", "Transport", "Bills", "Shopping", "Rent", "Health", "Fun", "Travel", "Gifts", "Other",
)

/** Ways to pay. */
val PAYMENT_MODES = listOf("UPI", "Cash", "Card", "Other")
