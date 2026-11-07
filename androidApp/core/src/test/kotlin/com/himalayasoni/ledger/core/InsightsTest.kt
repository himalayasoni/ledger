package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.byCategory
import com.himalayasoni.ledger.core.domain.cumulative
import com.himalayasoni.ledger.core.domain.heatLevel
import com.himalayasoni.ledger.core.domain.monthTrend
import com.himalayasoni.ledger.core.domain.niceMax
import com.himalayasoni.ledger.core.domain.regulars
import com.himalayasoni.ledger.core.domain.spentOnOthers
import com.himalayasoni.ledger.core.domain.weekdayAverages
import com.himalayasoni.ledger.core.domain.yearTrend
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InsightsTest {
    private var t = 0L
    private fun e(desc: String, amount: Double, date: String, cat: String = "Food", with: List<String> = emptyList(), type: EntryType = EntryType.SPENT) =
        Entry(id = "e${++t}", type = type, amount = amount, date = LocalDate.parse(date), description = desc, category = cat, withPeople = with, createdAt = t, updatedAt = t)

    private val list = listOf(
        e("Chai", 20.0, "2026-11-02"), e("Chai", 20.0, "2026-11-03"), e("Rent", 18000.0, "2026-11-01", "Rent"),
        e("Dinner", 900.0, "2026-11-04", with = listOf("Rahul", "Amit")), e("Rent", 17000.0, "2025-11-01", "Rent"),
        e("Salary", 50000.0, "2026-11-01", "Salary", type = EntryType.RECEIVED),
    )

    @Test fun `nice chart maxima`() {
        assertEquals(100.0, niceMax(0.0), 0.0); assertEquals(2500.0, niceMax(2100.0), 0.0); assertEquals(10000.0, niceMax(7300.0), 0.0)
    }

    @Test fun `running total and heat levels`() {
        val c = list.cumulative(YearMonth.of(2026, 11))
        assertEquals(18000.0, c[1], 0.0); assertEquals(18940.0, c[4], 0.0); assertEquals(18940.0, c[30], 0.0)
        assertEquals(4, heatLevel(100.0, 100.0)); assertEquals(1, heatLevel(1.0, 100.0)); assertEquals(0, heatLevel(0.0, 100.0))
    }

    @Test fun `categories, regulars and others`() {
        val nov = list.filter { it.date.year == 2026 }
        assertEquals(listOf("Rent", "Food"), nov.byCategory().map { it.first })
        assertEquals("Chai", nov.regulars().single().name)
        val (people, total) = nov.spentOnOthers()
        assertEquals(600.0, total, 0.0); assertEquals(2, people.size)
    }

    @Test fun `trends compare with a year before`() {
        val m = list.monthTrend(YearMonth.of(2026, 11), "Rent")
        assertEquals(12, m.size); assertEquals(18000.0, m.last().value, 0.0); assertEquals(17000.0, m.last().previous!!, 0.0)
        val y = list.yearTrend("", LocalDate.parse("2026-11-07"))
        assertEquals(listOf("2025", "2026"), y.map { it.label }); assertEquals(17000.0, y.last().previous!!, 0.0)
    }

    @Test fun `weekday averages over 12 weeks`() {
        val avg = list.weekdayAverages(LocalDate.parse("2026-11-07"))
        assertEquals(18000.0 / 12, avg[6], 0.001) // 1 Nov 2026 is a Sunday
    }
}
