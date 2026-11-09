package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The numbers behind Insights, ported from the web app (paceChart, heatmap, catBreakdown,
 * catDetail, trendCard, weekdays, regulars, othersCard, biggest, paidWith). Pure functions.
 * "Spent" always means what it really cost you: shared bills minus what people paid back.
 */

private fun Entry.isSpend() = !deleted && type == EntryType.SPENT
private fun Entry.cat() = category.ifBlank { "Other" }

fun List<Entry>.spends(): List<Entry> = filter { it.isSpend() }
fun List<Entry>.costSum(): Double = sumOf { it.cost() }
fun List<Entry>.inMonth(m: YearMonth) = filter { !it.deleted && YearMonth.from(it.date) == m }
fun List<Entry>.inYear(y: Int) = filter { !it.deleted && it.date.year == y }
fun List<Entry>.receivedSum() = filter { !it.deleted && it.type == EntryType.RECEIVED }.sumOf { it.amount }

/** A round number just above [v] for a chart's top line (100, 200, 250, 500, 1000…). */
fun niceMax(v: Double): Double {
    if (v <= 0) return 100.0
    val p = 10.0.pow(floor(log10(v)))
    val n = v / p
    return (if (n <= 1) 1.0 else if (n <= 2) 2.0 else if (n <= 2.5) 2.5 else if (n <= 5) 5.0 else 10.0) * p
}

/** Running total per day of a month: index 0 is 0, index d is the total up to day d. */
fun List<Entry>.cumulative(m: YearMonth): DoubleArray {
    val a = DoubleArray(m.lengthOfMonth() + 1)
    for (e in this) if (e.isSpend() && YearMonth.from(e.date) == m) a[e.date.dayOfMonth] += e.cost()
    for (i in 1 until a.size) a[i] += a[i - 1]
    return a
}

/** Spending per day of a month (index = day). */
fun List<Entry>.perDay(m: YearMonth): DoubleArray {
    val a = DoubleArray(m.lengthOfMonth() + 1)
    for (e in this) if (e.isSpend() && YearMonth.from(e.date) == m) a[e.date.dayOfMonth] += e.cost()
    return a
}

/** Heatmap shade 0–4 for a day's spending, on a square-root scale so small days still show. */
fun heatLevel(v: Double, max: Double): Int = if (v <= 0 || max <= 0) 0 else max(1, ceil(sqrt(v / max) * 4).toInt())

/** Spending by category, biggest first. */
fun List<Entry>.byCategory(): List<Pair<String, Double>> =
    spends().groupBy { it.cat() }.mapValues { (_, l) -> l.costSum() }.toList().sortedByDescending { it.second }

fun List<Entry>.categoryIn(cat: String, m: YearMonth): Double =
    filter { it.isSpend() && it.cat() == cat && YearMonth.from(it.date) == m }.costSum()

/** What you spent on within a category: description, times, total. Top [limit] by total. */
data class ItemGroup(val name: String, val count: Int, val total: Double)

fun List<Entry>.topItems(limit: Int = 4): List<ItemGroup> =
    groupBy { it.description.ifBlank { "No description" } }
        .map { (k, l) -> ItemGroup(k, l.size, l.costSum()) }
        .sortedByDescending { it.total }.take(limit)

/** Things you buy again and again (at least twice), most frequent first. */
fun List<Entry>.regulars(limit: Int = 5): List<ItemGroup> =
    filter { it.description.isNotBlank() }
        .groupBy { it.description.trim().lowercase() }
        .map { (_, l) -> ItemGroup(l.first().description.trim(), l.size, l.costSum()) }
        .filter { it.count >= 2 }
        .sortedWith(compareByDescending<ItemGroup> { it.count }.thenByDescending { it.total })
        .take(limit)

/** Average spent per weekday (Mon..Sun) over the 12 weeks ending on [end]. */
fun List<Entry>.weekdayAverages(end: LocalDate): DoubleArray {
    val start = end.minusDays(83)
    val sums = DoubleArray(7)
    for (e in this) if (e.isSpend() && !e.date.isBefore(start) && !e.date.isAfter(end)) sums[e.date.dayOfWeek.value - 1] += e.cost()
    return DoubleArray(7) { sums[it] / 12.0 } // 84 days = exactly 12 of each weekday
}

/** Your unpaid shares on others' behalf, per person, biggest first, plus the total. */
fun List<Entry>.spentOnOthers(limit: Int = 5): Pair<List<Pair<String, Double>>, Double> {
    val m = linkedMapOf<String, Pair<String, Double>>()
    var total = 0.0
    for (e in spends()) {
        val with = e.people(); if (with.isEmpty()) continue
        val share = e.share()
        for (n in with) {
            val v = maxOf(0.0, share - e.repaid(n)); if (v <= 0) continue
            val k = n.lowercase()
            m[k] = (m[k]?.first ?: n) to ((m[k]?.second ?: 0.0) + v)
            total += v
        }
    }
    return m.values.sortedByDescending { it.second }.take(limit) to total
}

fun List<Entry>.biggest(limit: Int = 5): List<Entry> = spends().sortedByDescending { it.amount }.take(limit)

/** Spending by how you paid; card spends are split by card name when you've named it ("Card · HDFC"). */
fun List<Entry>.byMode(): List<Pair<String, Double>> =
    spends().groupBy { if (it.mode.equals("Card", true) && it.card.isNotBlank()) "Card · ${it.card}" else it.mode.ifBlank { "Other" } }.mapValues { (_, l) -> l.costSum() }.toList().sortedByDescending { it.second }

/** One bar group in "Category trends": this period's value and (optionally) the year-before comparison. */
data class TrendGroup(val label: String, val full: String, val value: Double, val previous: Double?, val previousLabel: String)

private val MON = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val MONTH_FULL = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
fun monthShort(m: Int) = MON[m - 1]
fun monthFull(m: Int) = MONTH_FULL[m - 1]

/** All categories that have spending, biggest total first (the chips above the trend chart). */
fun List<Entry>.trendCategories(): List<String> = byCategory().map { it.first }

/** The 12 months ending at [end], each with the same month a year before. [cat] empty = all spending. */
fun List<Entry>.monthTrend(end: YearMonth, cat: String): List<TrendGroup> {
    val s = spends().filter { cat.isEmpty() || it.cat() == cat }
    fun sum(m: YearMonth) = s.filter { YearMonth.from(it.date) == m }.costSum()
    return (11 downTo 0).map { i ->
        val m = end.minusMonths(i.toLong())
        TrendGroup(monthShort(m.monthValue), "${monthFull(m.monthValue)} ${m.year}", sum(m), sum(m.minusYears(1)), "${monthShort(m.monthValue)} ${m.year - 1}")
    }
}

/** Up to the last 6 years with spending; this year also compares with last year to the same date. */
fun List<Entry>.yearTrend(cat: String, today: LocalDate): List<TrendGroup> {
    val s = spends().filter { cat.isEmpty() || it.cat() == cat }
    val years = s.map { it.date.year }.distinct().sorted().takeLast(6)
    return years.map { y ->
        val total = s.filter { it.date.year == y }.costSum()
        val prev = if (y == today.year) s.filter { it.date.year == y - 1 && it.date.withYear(y) <= today }.costSum() else null
        TrendGroup("$y", if (y == today.year) "$y so far" else "$y", total, prev, "${y - 1} by the same date")
    }
}

/** Last year's spending up to the same date (for "Up 12% from 2025 by this date"). */
fun List<Entry>.spentInYearUntil(year: Int, monthDay: LocalDate): Double =
    spends().filter { it.date.year == year && it.date.withYear(monthDay.year) <= monthDay }.costSum()

fun Year.months(): List<YearMonth> = (1..12).map { atMonth(it) }

