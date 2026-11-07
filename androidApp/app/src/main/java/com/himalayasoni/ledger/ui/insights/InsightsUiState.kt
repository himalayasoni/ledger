package com.himalayasoni.ledger.ui.insights

import com.himalayasoni.ledger.core.domain.ItemGroup
import com.himalayasoni.ledger.core.domain.TrendGroup
import com.himalayasoni.ledger.core.domain.biggest
import com.himalayasoni.ledger.core.domain.byCategory
import com.himalayasoni.ledger.core.domain.byMode
import com.himalayasoni.ledger.core.domain.categoryIn
import com.himalayasoni.ledger.core.domain.costSum
import com.himalayasoni.ledger.core.domain.cumulative
import com.himalayasoni.ledger.core.domain.heatLevel
import com.himalayasoni.ledger.core.domain.inMonth
import com.himalayasoni.ledger.core.domain.inYear
import com.himalayasoni.ledger.core.domain.monthFull
import com.himalayasoni.ledger.core.domain.monthShort
import com.himalayasoni.ledger.core.domain.monthTrend
import com.himalayasoni.ledger.core.domain.niceMax
import com.himalayasoni.ledger.core.domain.perDay
import com.himalayasoni.ledger.core.domain.receivedSum
import com.himalayasoni.ledger.core.domain.regulars
import com.himalayasoni.ledger.core.domain.spends
import com.himalayasoni.ledger.core.domain.spentInYearUntil
import com.himalayasoni.ledger.core.domain.spentOnOthers
import com.himalayasoni.ledger.core.domain.topItems
import com.himalayasoni.ledger.core.domain.trendCategories
import com.himalayasoni.ledger.core.domain.weekdayAverages
import com.himalayasoni.ledger.core.domain.yearTrend
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.core.money.formatShort
import com.himalayasoni.ledger.ui.home.EntryRow
import com.himalayasoni.ledger.ui.home.entryRow
import com.himalayasoni.ledger.ui.people.dayLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** What you've picked on the Insights tab. Lives in the ViewModel; everything else is derived. */
data class InsightsSelection(
    val yearView: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val year: Int = month.year,
    val day: LocalDate? = null,          // the heatmap's chosen day
    val openCategory: String? = null,    // the expanded row in "Where it went"
    val trendCategory: String = "",      // "" = all spending
    val trendYears: Boolean = false,
    val trendIndex: Int? = null,         // tapped bar (null = latest)
)

data class Mini(val label: String, val value: String)
data class Hero(val label: String, val spent: String, val vs: String, val minis: List<Mini>)
data class Pace(val cur: List<Double>, val prev: List<Double>, val days: Int, val ymax: Double, val curName: String, val prevName: String,
                val curShort: String, val prevShort: String, val caption: String)
data class HeatDay(val day: Int, val level: Int, val future: Boolean, val selected: Boolean, val date: LocalDate)
data class Heat(val lead: Int, val days: List<HeatDay>, val dayLabel: String, val dayTotal: String?, val rows: List<EntryRow>)
data class CatPoint(val label: String, val value: Double, val current: Boolean)
data class CatRow(val name: String, val amount: String, val pct: Int, val fraction: Float, val open: Boolean,
                  val points: List<CatPoint>, val caption: String, val items: List<ItemGroup>)
data class Bar(val label: String, val value: Double, val top: String, val highlight: Boolean, val month: YearMonth?)
data class Bars(val title: String, val bars: List<Bar>, val caption: String?)
data class Trend(val categories: List<String>, val selected: String, val years: Boolean, val groups: List<TrendGroup>, val ymax: Double,
                 val index: Int, val caption: String)
data class Weekdays(val averages: List<Double>, val top: Int, val caption: String)
data class Line(val name: String, val note: String?, val amount: String)

data class InsightsUiState(
    val selection: InsightsSelection,
    val title: String,
    val canGoNext: Boolean,
    val hero: Hero,
    val pace: Pace?,
    val heat: Heat?,
    val categories: List<CatRow>,
    val trend: Trend?,
    val columns: Bars,
    val weekdays: Weekdays?,
    val regulars: List<Line>,
    val othersTitle: String?,
    val others: List<Line>,
    val biggest: List<EntryRow>,
    val paidWith: List<Line>,
)

/** Fits the small boxes: "₹12,000" but "₹1.2L" when it's long. Same as the web's fitR(). */
fun fit(v: Double): String {
    val sign = if (v < 0) "−" else ""
    val f = sign + formatRupees(abs(v))
    return if (f.length > 7) sign + formatShort(abs(v)) else f
}

private fun pctChange(now: Double, before: Double) = (abs(now - before) / before * 100).roundToInt()

private val LONG_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

fun buildInsights(all: List<Entry>, sel: InsightsSelection, today: LocalDate): InsightsUiState {
    val nowMonth = YearMonth.from(today)
    return if (sel.yearView) buildYear(all, sel, today) else buildMonth(all, sel, today, nowMonth)
}

private fun buildMonth(all: List<Entry>, sel: InsightsSelection, today: LocalDate, nowMonth: YearMonth): InsightsUiState {
    val m = sel.month
    val isCur = m == nowMonth
    val month = all.inMonth(m)
    val spentL = month.spends()
    val spent = spentL.costSum()
    val prevM = m.minusMonths(1)
    val prev = all.inMonth(prevM).spends().costSum()
    val elapsed = if (isCur) today.dayOfMonth else m.lengthOfMonth()
    val vs = if (prev > 0) "${if (spent - prev <= 0) "Down" else "Up"} ${pctChange(spent, prev)}% from ${monthShort(prevM.monthValue)} (${formatRupees(prev)})"
    else if (spentL.isNotEmpty()) "${spentL.size} expenses" else "No expenses logged this month"
    val recv = month.receivedSum()
    val hero = Hero("Spent", formatRupees(spent), vs, listOf(
        Mini("Received", fit(recv)), Mini("Daily average", fit(spent / maxOf(1, elapsed))), Mini("Net", fit(recv - spent)),
    ))

    // spending pace: running totals, this month vs last
    val cur = all.cumulative(m); val pr = all.cumulative(prevM)
    val upto = if (isCur) today.dayOfMonth else m.lengthOfMonth()
    val pace = if (cur[upto] == 0.0 && pr.last() == 0.0) null else {
        val cv = cur[upto]; val pv = pr[minOf(upto, pr.size - 1)]; val diff = cv - pv
        Pace(
            cur.take(upto + 1), pr.toList(), maxOf(m.lengthOfMonth(), prevM.lengthOfMonth()), niceMax(maxOf(cv, pr.last())),
            monthFull(m.monthValue), monthFull(prevM.monthValue), monthShort(m.monthValue), monthShort(prevM.monthValue),
            if (pv > 0) "By day $upto: ${formatRupees(cv)}, ${formatRupees(abs(diff))} ${if (diff <= 0) "less" else "more"} than ${monthShort(prevM.monthValue)} by the same day."
            else "By day $upto: ${formatRupees(cv)}.",
        )
    }

    // day by day
    val heat = if (spentL.isEmpty()) null else {
        val per = all.perDay(m); val mx = per.max()
        val lead = m.atDay(1).dayOfWeek.value - 1
        val chosen = sel.day?.takeIf { YearMonth.from(it) == m }
            ?: if (isCur) today else m.atDay(maxOf(1, per.indexOfFirst { it == mx }))
        val dayList = all.filter { !it.deleted && it.date == chosen }.sortedByDescending { it.createdAt }
        val total = dayList.spends().costSum()
        Heat(
            lead,
            (1..m.lengthOfMonth()).map { d -> val date = m.atDay(d); HeatDay(d, heatLevel(per[d], mx), date.isAfter(today), date == chosen, date) },
            dayLabel(chosen, today), if (total > 0) "Spent ${formatRupees(total)}" else null, dayList.map { entryRow(it) },
        )
    }

    val six = (5 downTo 0).map { i ->
        val ym = m.minusMonths(i.toLong()); val v = all.inMonth(ym).spends().costSum()
        Bar(monthShort(ym.monthValue), v, if (v > 0) formatShort(v) else "", ym == m, ym)
    }
    val end = if (isCur) today else m.atEndOfMonth()
    return common(all, sel, today, spentL, spent, hero, pace, heat, Bars("Last 6 months", six, null), end,
        title = "${monthFull(m.monthValue)} ${m.year}", canNext = m.isBefore(YearMonth.from(today)),
        catPoints = { c -> (5 downTo 0).map { i -> val ym = m.minusMonths(i.toLong()); CatPoint(monthShort(ym.monthValue), all.categoryIn(c, ym), i == 0) } },
        trendEnd = m)
}

private fun buildYear(all: List<Entry>, sel: InsightsSelection, today: LocalDate): InsightsUiState {
    val y = sel.year
    val isCur = y == today.year
    val list = all.inYear(y)
    val spentL = list.spends()
    val spent = spentL.costSum()
    val recv = list.receivedSum()
    val until = if (isCur) today else LocalDate.of(y, 12, 31)
    val prevSame = all.spentInYearUntil(y - 1, until)
    val vs = if (prevSame > 0) "${if (spent - prevSame <= 0) "Down" else "Up"} ${pctChange(spent, prevSame)}% from ${y - 1}${if (isCur) " by this date" else ""} (${formatRupees(prevSame)})"
    else if (spentL.isNotEmpty()) "${spentL.size} expenses" else "No expenses logged this year"
    val months = if (isCur) today.monthValue else 12
    val hero = Hero("Spent in $y", formatRupees(spent), vs, listOf(
        Mini("Received", fit(recv)), Mini("Monthly average", fit(spent / months)), Mini("Net", fit(recv - spent)),
    ))
    val ms = (1..12).map { mo -> all.inMonth(YearMonth.of(y, mo)).spends().costSum() }
    val topM = ms.indices.maxBy { ms[it] }
    val bars = ms.mapIndexed { i, v ->
        val hi = i == topM && v > 0
        Bar(monthShort(i + 1), v, if (hi) formatShort(v) else "", hi, YearMonth.of(y, i + 1))
    }
    val cap = (if (ms[topM] > 0) "Highest: ${monthFull(topM + 1)} (${formatRupees(ms[topM])}). " else "") + "Tap a month to open it."
    return common(all, sel, today, spentL, spent, hero, null, null, Bars("Month by month", bars, cap), until,
        title = "$y", canNext = y < today.year,
        catPoints = { c -> (1..12).map { mo -> CatPoint(monthShort(mo).take(1), all.categoryIn(c, YearMonth.of(y, mo)), false) } },
        trendEnd = if (isCur) YearMonth.from(today) else YearMonth.of(y, 12))
}

private fun common(
    all: List<Entry>, sel: InsightsSelection, today: LocalDate, spentL: List<Entry>, spent: Double, hero: Hero, pace: Pace?, heat: Heat?,
    columns: Bars, weekEnd: LocalDate, title: String, canNext: Boolean, catPoints: (String) -> List<CatPoint>, trendEnd: YearMonth,
): InsightsUiState {
    val cats = spentL.byCategory()
    val catRows = cats.map { (c, v) ->
        val open = sel.openCategory == c
        val items = spentL.filter { it.category.ifBlank { "Other" } == c }
        CatRow(
            c, formatRupees(v), if (spent > 0) (v / spent * 100).roundToInt() else 0, (v / cats.first().second).toFloat(), open,
            if (open) catPoints(c) else emptyList(),
            "${items.size} entr${if (items.size == 1) "y" else "ies"} · average ${formatRupees((items.costSum() / items.size).roundToInt().toDouble())} each",
            if (open) items.topItems() else emptyList(),
        )
    }

    // category trends
    val tcats = all.trendCategories()
    val trend = if (tcats.isEmpty()) null else {
        val tc = sel.trendCategory.takeIf { it in tcats }.orEmpty()
        val groups = if (sel.trendYears) all.yearTrend(tc, today) else all.monthTrend(trendEnd, tc)
        val idx = sel.trendIndex?.takeIf { it < groups.size } ?: (groups.size - 1)
        val g = groups[idx]; val before = groups.getOrNull(idx - 1)
        fun pct(a: Double, b: Double) = if (b > 0) " (${if (a - b <= 0) "−" else "+"}${pctChange(a, b)}%)" else ""
        var cap = "${g.full}: ${formatRupees(g.value)}"
        if (before != null) cap += " · ${before.full}: ${formatRupees(before.value)}${pct(g.value, before.value)}"
        if (g.previous != null) cap += " · ${g.previousLabel}: ${formatRupees(g.previous!!)}${pct(g.value, g.previous!!)}"
        Trend(tcats, tc, sel.trendYears, groups, niceMax(maxOf(1.0, groups.maxOf { maxOf(it.value, it.previous ?: 0.0) })), idx, cap)
    }

    val avg = all.weekdayAverages(weekEnd)
    val mx = avg.max()
    val names = listOf("Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays", "Sundays")
    val weekdays = if (mx <= 0) null else {
        val top = avg.indexOfFirst { it == mx }
        Weekdays(avg.toList(), top, "Average spent per day over the 12 weeks to ${weekEnd.format(LONG_DATE)}. You spend the most on ${names[top]}.")
    }

    val (others, otherTotal) = spentL.spentOnOthers()
    return InsightsUiState(
        sel, title, canNext, hero, pace, heat, catRows, trend, columns, weekdays,
        regulars = spentL.regulars().map { Line(it.name, "${it.count} times · avg ${formatRupees((it.total / it.count).roundToInt().toDouble())}", formatRupees(it.total)) },
        othersTitle = if (others.isEmpty()) null else "Spent on others · ${formatRupees(otherTotal)}",
        others = others.map { Line(it.first, null, formatRupees(it.second)) },
        biggest = spentL.biggest().map { e -> entryRow(e).let { it.copy(subtitle = listOf(it.subtitle, e.date.format(LONG_DATE)).filter(String::isNotBlank).joinToString(" · ")) } },
        paidWith = spentL.byMode().map { (k, v) -> Line(k, "${if (spent > 0) (v / spent * 100).roundToInt() else 0}%", formatRupees(v)) },
    )
}
