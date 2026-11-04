package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.balances
import com.himalayasoni.ledger.core.domain.cost
import com.himalayasoni.ledger.core.domain.share
import com.himalayasoni.ledger.core.domain.spentIn
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerMathTest {
    private val day = LocalDate.of(2026, 11, 4)
    private fun entry(type: EntryType, amount: Double, person: String = "", with: List<String> = emptyList(),
                      paidBack: Map<String, Double> = emptyMap(), prior: Double = 0.0, date: LocalDate = day) =
        Entry(id = "e${amount}${person}${type}", type = type, amount = amount, date = date, person = person, withPeople = with,
              paidBack = paidBack, prior = prior, createdAt = 0, updatedAt = 0)

    @Test fun `a shared bill splits between everyone including me`() {
        val dinner = entry(EntryType.SPENT, 1200.0, with = listOf("Rahul", "Amit"))
        assertEquals(400.0, dinner.share(), 0.001)
    }

    @Test fun `money paid back lowers what the bill cost me`() {
        val dinner = entry(EntryType.SPENT, 1200.0, with = listOf("Rahul", "Amit"), paidBack = mapOf("rahul" to 400.0))
        assertEquals(800.0, dinner.cost(), 0.001)
        assertEquals(800.0, listOf(dinner).spentIn(YearMonth.of(2026, 11)), 0.001)
    }

    @Test fun `lending and repayments move a person's balance`() {
        val list = listOf(entry(EntryType.LENT, 1000.0, "Ravi"), entry(EntryType.GOT_BACK, 400.0, "ravi"))
        assertEquals(600.0, list.balances().getValue("ravi").balance, 0.001)
    }

    @Test fun `a repayment of a loan from before Ledger doesn't count`() {
        // paid Kanika back ₹700 for a loan Ledger never saw: the ₹700 is all "prior"
        val list = listOf(entry(EntryType.PAID_BACK, 700.0, "Kanika", prior = 700.0))
        assertEquals(0.0, list.balances().getValue("kanika").balance, 0.001)
    }

    @Test fun `spent on them excludes their paid-back share`() {
        val dinner = entry(EntryType.SPENT, 1200.0, with = listOf("Rahul", "Amit"), paidBack = mapOf("rahul" to 400.0))
        val b = listOf(dinner).balances()
        assertEquals(0.0, b.getValue("rahul").spentOnThem, 0.001)
        assertEquals(400.0, b.getValue("amit").spentOnThem, 0.001)
    }

    @Test fun `other months don't count`() {
        val old = entry(EntryType.SPENT, 500.0, date = LocalDate.of(2026, 10, 31))
        assertEquals(0.0, listOf(old).spentIn(YearMonth.of(2026, 11)), 0.001)
    }
}
