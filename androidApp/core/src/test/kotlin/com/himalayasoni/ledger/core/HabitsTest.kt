package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.matching
import com.himalayasoni.ledger.core.domain.recentPeople
import com.himalayasoni.ledger.core.domain.spentInUntil
import com.himalayasoni.ledger.core.domain.streak
import com.himalayasoni.ledger.core.domain.top
import com.himalayasoni.ledger.core.domain.usuals
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HabitsTest {
    private var t = 1_000L
    private fun e(desc: String, amount: Double, type: EntryType = EntryType.SPENT, date: String = "2026-11-05", person: String = "", with: List<String> = emptyList()) =
        Entry(id = "e${t}", type = type, amount = amount, date = LocalDate.parse(date), description = desc, person = person,
            withPeople = with, category = "Food", createdAt = ++t, updatedAt = t)

    @Test fun `usuals keep the latest amount and rank by how often`() {
        val list = listOf(e("Chai", 15.0), e("Uber", 240.0), e("chai ", 20.0), e("Chai", 20.0))
        val top = list.usuals().top()
        assertEquals(listOf("Chai", "Uber"), top.map { it.description })
        assertEquals(3, top[0].count)
        assertEquals(20.0, top[0].amount, 0.0)
    }

    @Test fun `usuals can be limited to one type`() {
        val list = listOf(e("Chai", 20.0), e("Rahul", 500.0, EntryType.LENT, person = "Rahul"))
        assertEquals(listOf("Rahul"), list.usuals().top(EntryType.LENT).map { it.description })
    }

    @Test fun `typing suggests matching usuals, prefix matches first`() {
        val u = listOf(e("Uber to office", 240.0), e("Office lunch", 300.0)).usuals()
        assertEquals(listOf("Office lunch", "Uber to office"), u.matching("off").map { it.description })
        assertEquals(emptyList<String>(), u.matching("").map { it.description })
    }

    @Test fun `recent people are unique and newest first`() {
        val list = listOf(e("Lunch", 400.0, with = listOf("Rahul", "Amit")), e("Loan", 100.0, EntryType.LENT, person = "rahul"), e("x", 1.0, EntryType.LENT, person = "Sneha"))
        assertEquals(listOf("Sneha", "rahul", "Amit"), list.recentPeople())
    }

    @Test fun `this point last month only counts days up to today's date`() {
        val list = listOf(e("a", 100.0, date = "2026-10-03"), e("b", 50.0, date = "2026-10-20"))
        assertEquals(100.0, list.spentInUntil(YearMonth.of(2026, 10), 7), 0.0)
    }

    @Test fun `streak counts back from today, or from yesterday if today is empty`() {
        val today = LocalDate.parse("2026-11-07")
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(2, streak(days, today))
        assertEquals(3, streak(days + today, today))
        assertEquals(0, streak(emptySet(), today))
    }
}
