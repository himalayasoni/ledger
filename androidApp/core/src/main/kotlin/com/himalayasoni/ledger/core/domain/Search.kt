package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Search and filters, the same rules as the web app's filtered(). */

enum class DateRange(val label: String) { ALL("All time"), LAST_7_DAYS("Last 7 days"), THIS_MONTH("This month"), LAST_MONTH("Last month"), CUSTOM("Pick dates") }

enum class SortBy(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), BIGGEST("Biggest amount"), SMALLEST("Smallest amount"), NAME("Name A–Z") }

data class Filters(
    val query: String = "",
    val types: Set<EntryType> = emptySet(),     // empty = all types
    val range: DateRange = DateRange.ALL,
    val from: LocalDate? = null,                // for CUSTOM
    val to: LocalDate? = null,
    val person: String = "",
    val category: String = "",
    val mode: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val onlyWithNote: Boolean = false,
    val sort: SortBy = SortBy.NEWEST,
) {
    val isDefault: Boolean get() = this == Filters()
    /** Anything set under "More filters"? (the web opens that panel when so) */
    val hasMore: Boolean get() = person.isNotEmpty() || category.isNotEmpty() || mode.isNotEmpty() || min != null || max != null || onlyWithNote || sort != SortBy.NEWEST
}

/** The dates a range covers, inclusive. Nulls mean "no limit". */
fun DateRange.bounds(today: LocalDate, from: LocalDate?, to: LocalDate?): Pair<LocalDate?, LocalDate?> = when (this) {
    DateRange.ALL -> null to null
    DateRange.LAST_7_DAYS -> today.minusDays(6) to today
    DateRange.THIS_MONTH -> today.withDayOfMonth(1) to today
    DateRange.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
    DateRange.CUSTOM -> from to to
}

private val SEARCH_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

/** Plain amount text as typed or shown on the web ("240", "1499.5"). */
private fun amountText(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/** Every word you type must appear somewhere: description, people, category, comment, mode, type, amount or date. */
private fun Entry.matches(words: List<String>): Boolean {
    val hay = listOf(description, person, people().joinToString(" "), category, note, mode, card, type.label,
        if (refund != null) "refund" else "", amountText(amount), date.format(SEARCH_DATE))
        .joinToString(" ").lowercase()
    return words.all { hay.contains(it) }
}

fun List<Entry>.search(f: Filters, today: LocalDate): List<Entry> {
    val (from, to) = f.range.bounds(today, f.from, f.to)
    val words = f.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val result = filter { e ->
        !e.deleted &&
            (f.types.isEmpty() || e.type in f.types) &&
            (from == null || !e.date.isBefore(from)) &&
            (to == null || !e.date.isAfter(to)) &&
            (f.person.isEmpty() || e.involves(f.person)) &&
            (f.category.isEmpty() || e.category == f.category) &&
            (f.mode.isEmpty() || e.mode == f.mode) &&
            (f.min == null || e.amount >= f.min) &&
            (f.max == null || e.amount <= f.max) &&
            (!f.onlyWithNote || e.note.isNotBlank()) &&
            (words.isEmpty() || e.matches(words))
    }
    val newest = compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }
    return when (f.sort) {
        SortBy.NEWEST -> result.sortedWith(newest)
        SortBy.OLDEST -> result.sortedWith(newest.reversed())
        SortBy.BIGGEST -> result.sortedByDescending { it.amount }
        SortBy.SMALLEST -> result.sortedBy { it.amount }
        SortBy.NAME -> result.sortedBy { it.description.ifBlank { it.person }.lowercase() }
    }
}

/** "Out ₹x · In ₹y" for a set of results: money out counts what it really cost you. Card bills aren't spending, so they're left out. */
fun List<Entry>.outAndIn(): Pair<Double, Double> {
    var out = 0.0; var inn = 0.0
    for (e in this) if (e.type == EntryType.CARD_BILL) continue else if (e.type.sign < 0) out += e.cost() else inn += e.amount
    return out to inn
}
