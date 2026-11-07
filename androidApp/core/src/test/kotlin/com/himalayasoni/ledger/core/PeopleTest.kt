package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.fixOldLoan
import com.himalayasoni.ledger.core.domain.markSharesPaid
import com.himalayasoni.ledger.core.domain.oldLoanIssues
import com.himalayasoni.ledger.core.domain.peopleIndex
import com.himalayasoni.ledger.core.domain.sortedForList
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeopleTest {
    private var t = 0L
    private fun e(type: EntryType, amount: Double, person: String = "", with: List<String> = emptyList(), date: String = "2026-11-01",
                  paidBack: Map<String, Double> = emptyMap(), prior: Double = 0.0) =
        Entry(id = "e${++t}", type = type, amount = amount, date = LocalDate.parse(date), person = person, withPeople = with,
            paidBack = paidBack, prior = prior, createdAt = t, updatedAt = t)

    @Test fun `loans and shared spends add up per person`() {
        val p = listOf(
            e(EntryType.LENT, 1000.0, "Rahul"),
            e(EntryType.GOT_BACK, 400.0, "rahul"),
            e(EntryType.SPENT, 900.0, with = listOf("Rahul", "Amit")),      // 300 each
            e(EntryType.BORROWED, 200.0, "Amit", date = "2026-11-03"),
        ).peopleIndex()
        assertEquals(600.0, p["rahul"]!!.balance, 0.0)
        assertEquals(300.0, p["rahul"]!!.spentOnThem, 0.0)
        assertEquals(-200.0, p["amit"]!!.balance, 0.0)
        assertEquals(1, p["amit"]!!.unsettled)
        assertEquals(listOf("Rahul", "Amit"), p.sortedForList().map { it.name })
    }

    @Test fun `newest spelling of a name wins`() {
        val p = listOf(e(EntryType.LENT, 10.0, "rahul", date = "2026-10-01"), e(EntryType.LENT, 10.0, "Rahul", date = "2026-11-01")).peopleIndex()
        assertEquals("Rahul", p["rahul"]!!.name)
        assertEquals(2, p["rahul"]!!.entryCount)
    }

    @Test fun `a repayment with no loan is flagged and can be fixed`() {
        // the Kanika case: you paid her back 700 for a loan from before Ledger
        val list = listOf(e(EntryType.PAID_BACK, 700.0, "Kanika"))
        val issue = list.oldLoanIssues().single()
        assertEquals(700.0, issue.extra, 0.0)
        val fixed = list.fixOldLoan(issue, now = 99)
        assertEquals(700.0, fixed.single().prior, 0.0)
        assertEquals(0.0, fixed.peopleIndex()["kanika"]!!.balance, 0.0)
        assertTrue(fixed.oldLoanIssues().isEmpty())
    }

    @Test fun `paid their share settles every open share`() {
        val list = listOf(
            e(EntryType.SPENT, 800.0, with = listOf("Rahul")),                                 // 400 each
            e(EntryType.SPENT, 600.0, with = listOf("Rahul", "Amit"), paidBack = mapOf("rahul" to 100.0)), // 200 each, 100 back
        )
        val (changed, total) = list.markSharesPaid("rahul", now = 5)
        assertEquals(2, changed.size)
        assertEquals(500.0, total, 0.0)
        assertEquals(0, changed.peopleIndex()["rahul"]!!.unsettled)
    }
}
