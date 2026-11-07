package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import java.time.LocalDate

/*
 * The People tab's rules, ported from the web app (peopleIndex, involves, oldLoanIssues,
 * fixOldLoan, markSharesPaid). Pure functions: entries in, answers (or updated entries) out.
 */

/**
 * Everything Ledger knows about one person.
 * @property balance positive: they owe you; negative: you owe them (loans only)
 * @property spentOnThem their unpaid shares of expenses you paid ("treats")
 * @property sharedCount how many shared expenses they were part of
 * @property repaid how much they've paid back for their shares
 * @property unsettled shared expenses where their share is still open
 */
data class Person(
    val name: String,
    val balance: Double,
    val entryCount: Int,
    val lastDate: LocalDate,
    val hasLoans: Boolean,
    val spentOnThem: Double,
    val sharedCount: Int,
    val repaid: Double,
    val unsettled: Int,
)

private fun r2(v: Double) = Math.round(v * 100) / 100.0

/** Everyone in your entries, keyed by lowercase name. The newest spelling of a name wins. */
fun List<Entry>.peopleIndex(): Map<String, Person> {
    class Acc(var name: String, var last: LocalDate) {
        var balance = 0.0; var count = 0; var loans = false
        var treat = 0.0; var treats = 0; var repaid = 0.0; var unsettled = 0
    }
    val m = linkedMapOf<String, Acc>()
    fun get(name: String, date: LocalDate): Acc {
        val a = m.getOrPut(name.lowercase()) { Acc(name, date) }
        a.count++
        if (date >= a.last) { a.last = date; a.name = name }
        return a
    }
    for (e in this) {
        if (e.deleted) continue
        if (e.person.isNotBlank() && e.type != EntryType.SPENT) {
            val a = get(e.person, e.date)
            if (e.type.isDebt) { a.loans = true; a.balance += e.type.balanceEffect * (e.amount - e.prior) }
        }
        val with = e.people()
        if (with.isNotEmpty()) {
            val share = e.share()
            for (n in with) {
                val a = get(n, e.date)
                val back = minOf(share, e.repaid(n))
                a.treat += share - back; a.treats++; a.repaid += back
                if (share - back > 0.5) a.unsettled++
            }
        }
    }
    return m.mapValues { (_, a) ->
        Person(a.name, r2(a.balance), a.count, a.last, a.loans, r2(a.treat), a.treats, r2(a.repaid), a.unsettled)
    }
}

/** The People list order: biggest balance first, then most recent. */
fun Map<String, Person>.sortedForList(): List<Person> =
    values.sortedWith(compareByDescending<Person> { Math.abs(it.balance) }.thenByDescending { it.lastDate })

/** Does this entry involve [name]? (as the loan's person, or as one of the people you spent with) */
fun Entry.involves(name: String): Boolean {
    val n = name.lowercase()
    return (type != EntryType.SPENT && person.lowercase() == n) || people().any { it.lowercase() == n }
}

/**
 * A repayment with no matching loan in Ledger, usually a loan from before you started using it.
 * [type] is the repayment type to fix (PAID_BACK or GOT_BACK); [extra] is how much doesn't add up.
 */
data class OldLoanIssue(val name: String, val type: EntryType, val extra: Double)

private fun List<Entry>.sumFor(type: EntryType, n: String) =
    filter { !it.deleted && it.type == type && it.person.lowercase() == n }.sumOf { it.amount - it.prior }

/** The issue for one person, if any. Same rule as the web's oldLoanIssues(). */
fun List<Entry>.oldLoanIssue(p: Person): OldLoanIssue? {
    val n = p.name.lowercase()
    return when {
        p.balance > 0 -> minOf(p.balance, maxOf(0.0, sumFor(EntryType.PAID_BACK, n) - sumFor(EntryType.BORROWED, n)))
            .takeIf { it > 0 }?.let { OldLoanIssue(p.name, EntryType.PAID_BACK, it) }
        p.balance < 0 -> minOf(-p.balance, maxOf(0.0, sumFor(EntryType.GOT_BACK, n) - sumFor(EntryType.LENT, n)))
            .takeIf { it > 0 }?.let { OldLoanIssue(p.name, EntryType.GOT_BACK, it) }
        else -> null
    }
}

fun List<Entry>.oldLoanIssues(): List<OldLoanIssue> = peopleIndex().values.mapNotNull { oldLoanIssue(it) }

/**
 * "It was from before. Fix it": marks the extra part of the newest repayments as `prior`,
 * so it no longer counts towards the balance. Returns only the entries that changed.
 */
fun List<Entry>.fixOldLoan(issue: OldLoanIssue, now: Long): List<Entry> {
    val n = issue.name.lowercase()
    var left = issue.extra
    val out = mutableListOf<Entry>()
    for (e in filter { !it.deleted && it.type == issue.type && it.person.lowercase() == n }
        .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })) {
        if (left <= 0) break
        val room = e.amount - e.prior
        if (room <= 0) continue
        val take = minOf(room, left); left -= take
        out += e.copy(prior = r2(e.prior + take), updatedAt = now)
    }
    return out
}

/** "Rahul paid their share": settles every open share of theirs. Returns the changed entries and the total. */
fun List<Entry>.markSharesPaid(name: String, now: Long): Pair<List<Entry>, Double> {
    val n = name.lowercase()
    var total = 0.0
    val out = mutableListOf<Entry>()
    for (e in this) {
        if (e.deleted || e.type != EntryType.SPENT || e.people().none { it.lowercase() == n }) continue
        val share = r2(e.share()); val got = e.repaid(n)
        if (share - got <= 0.5) continue
        out += e.copy(withPeople = e.people(), paidBack = e.paidBack + (n to share), updatedAt = now)
        total += share - got
    }
    return out to r2(total)
}
