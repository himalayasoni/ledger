package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import java.time.YearMonth

/*
 * The rules of the ledger, as plain functions over lists of entries.
 * Screens never do arithmetic themselves; they call these. Each rule is unit-tested.
 */

/** People on a shared expense. Older entries stored a single person instead of a list. */
fun Entry.people(): List<String> = when {
    withPeople.isNotEmpty() -> withPeople
    type == EntryType.SPENT && person.isNotBlank() -> listOf(person)
    else -> emptyList()
}

/** Each person's share of a shared expense. */
fun Entry.share(): Double {
    val ways = people().size + if (includesMe) 1 else 0
    return amount / maxOf(1, ways)
}

/** How much one person (or everyone, if [name] is null) paid back for this expense. */
fun Entry.repaid(name: String? = null): Double =
    if (name == null) paidBack.values.sum() else paidBack[name.lowercase()] ?: 0.0

/** What an entry really cost you: a shared bill minus what people paid back. */
fun Entry.cost(): Double = if (type == EntryType.SPENT) maxOf(0.0, amount - repaid()) else amount

/** Total spent in a month, after repayments. */
fun List<Entry>.spentIn(month: YearMonth): Double =
    filter { !it.deleted && it.type == EntryType.SPENT && YearMonth.from(it.date) == month }.sumOf { it.cost() }

/** Total received in a month. */
fun List<Entry>.receivedIn(month: YearMonth): Double =
    filter { !it.deleted && it.type == EntryType.RECEIVED && YearMonth.from(it.date) == month }.sumOf { it.amount }

/**
 * Where you stand with one person.
 * @property balance positive: they owe you; negative: you owe them
 * @property spentOnThem your money spent on them through shared expenses, minus what they paid back
 */
data class PersonBalance(
    val name: String,
    val balance: Double,
    val spentOnThem: Double,
    val entryCount: Int,
    val hasLoans: Boolean,
)

/** Balances for everyone who appears in your entries, keyed by lowercase name. */
fun List<Entry>.balances(): Map<String, PersonBalance> {
    data class Acc(var name: String, var balance: Double = 0.0, var spent: Double = 0.0, var count: Int = 0, var loans: Boolean = false)
    val acc = linkedMapOf<String, Acc>()
    fun touch(name: String) = acc.getOrPut(name.lowercase()) { Acc(name) }.also { it.count++ }
    for (e in this) {
        if (e.deleted) continue
        if (e.type.isDebt && e.person.isNotBlank()) {
            val a = touch(e.person)
            a.loans = true
            a.balance += e.type.balanceEffect * (e.amount - e.prior)
        }
        if (e.type == EntryType.SPENT) {
            val share = e.share()
            for (p in e.people()) {
                val a = touch(p)
                a.spent += maxOf(0.0, share - e.repaid(p))
            }
        }
    }
    return acc.mapValues { (_, a) ->
        PersonBalance(a.name, Math.round(a.balance * 100) / 100.0, Math.round(a.spent * 100) / 100.0, a.count, a.loans)
    }
}

/** Hides entries in private categories (loans have no category, so they always show). */
fun List<Entry>.withoutPrivate(privateCategories: Set<String>): List<Entry> {
    if (privateCategories.isEmpty()) return this
    val hidden = privateCategories.map { it.lowercase() }.toSet()
    return filterNot { !it.type.isDebt && it.category.lowercase() in hidden }
}
