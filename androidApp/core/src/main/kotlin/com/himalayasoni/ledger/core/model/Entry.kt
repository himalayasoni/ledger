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
