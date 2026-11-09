package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/*
 * Habits: the things that make logging fast (usuals, recent people) and the streak.
 * Same rules as the web app.
 */

/** Something you log often ("Chai ₹20"). One tap fills the whole entry. */
data class Usual(
    val key: String,
    val description: String,
    val amount: Double,
    val type: EntryType,
    val category: String,
    val person: String,
    val withPeople: List<String>,
    val includesMe: Boolean,
    val mode: String,
    val count: Int,
    val lastUsed: Long,
    /** Which credit card, when it was paid by card (so logging it again counts towards that card). */
    val card: String = "",
)

/** Every description you've used, with the details from its latest use and how often you've used it. */
fun List<Entry>.usuals(): List<Usual> {
    val byKey = linkedMapOf<String, Usual>()
    for (e in filter { !it.deleted && it.description.isNotBlank() }.sortedByDescending { it.createdAt }) {
        val key = e.description.trim().lowercase()
        val u = byKey[key]
        byKey[key] = u?.copy(count = u.count + 1) ?: Usual(
            key, e.description.trim(), e.amount, e.type, e.category, e.person,
            e.people(), e.includesMe, e.mode, 1, e.createdAt, e.card,
        )
    }
    return byKey.values.toList()
}

/** The ones to offer: most used first, then most recent. Pass a type once the person has picked one. */
fun List<Usual>.top(type: EntryType? = null, limit: Int = 6): List<Usual> =
    filter { type == null || it.type == type }
        .sortedWith(compareByDescending<Usual> { it.count }.thenByDescending { it.lastUsed })
        .take(limit)

/** Usuals whose description starts with (or contains) what's typed so far. */
fun List<Usual>.matching(typed: String, limit: Int = 3): List<Usual> {
    val q = typed.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return filter { it.key != q && it.key.contains(q) }
        .sortedWith(compareBy<Usual> { !it.key.startsWith(q) }.thenByDescending { it.count })
        .take(limit)
}

/** Names you've used recently, newest first, without duplicates (case-insensitive). */
fun List<Entry>.recentPeople(limit: Int = 4): List<String> {
    val seen = linkedMapOf<String, String>()
    for (e in filter { !it.deleted }.sortedByDescending { it.createdAt }) {
        for (n in e.people() + listOfNotNull(e.person.takeIf { it.isNotBlank() })) {
            seen.putIfAbsent(n.trim().lowercase(), n.trim())
            if (seen.size >= limit) return seen.values.toList()
        }
    }
    return seen.values.toList()
}

/** Spending in a month up to (and including) a day of that month, for "this point last month". */
fun List<Entry>.spentInUntil(month: YearMonth, day: Int): Double =
    filter { !it.deleted && it.type == EntryType.SPENT && YearMonth.from(it.date) == month && it.date.dayOfMonth <= day }
        .sumOf { it.cost() }

/** Days on which you logged something (by when you logged it, not the entry's date). */
fun List<Entry>.loggedDays(zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
    filter { it.createdAt > 0 }.map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }.toSet()

/** Days in a row with something logged, ending today (or yesterday, if today is still empty). */
fun streak(days: Set<LocalDate>, today: LocalDate): Int {
    var d = if (today in days) today else today.minusDays(1)
    var n = 0
    while (d in days) { n++; d = d.minusDays(1) }
    return n
}

/**
 * The categories to offer, like the web's allCats(): the built-in ones (minus any you removed),
 * then ones you added, then any others found on your entries. No duplicates, ignoring case.
 */
fun List<Entry>.allCategories(custom: Collection<String>, removed: Collection<String>, extra: Collection<String> = emptyList()): List<String> {
    val gone = removed.map { it.lowercase() }.toSet()
    val used = filter { !it.deleted && !it.type.isDebt && it.category.isNotBlank() }.map { it.category }
    return (com.himalayasoni.ledger.core.model.DEFAULT_CATEGORIES.filter { it.lowercase() !in gone } + custom.sortedBy { it.lowercase() } + used + extra)
        .distinctBy { it.lowercase() }
}
