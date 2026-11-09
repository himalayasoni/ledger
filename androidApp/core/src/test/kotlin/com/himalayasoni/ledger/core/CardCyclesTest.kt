package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.Verdict
import com.himalayasoni.ledger.core.domain.cardCycles
import com.himalayasoni.ledger.core.domain.suggestedBill
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CardCyclesTest {
    private val today = LocalDate.parse("2026-11-09")
    private var t = 0L
    private fun spend(amount: Double, date: String) = Entry(id = "s${++t}", type = EntryType.SPENT, amount = amount, date = LocalDate.parse(date),
        mode = "Card", card = "HDFC", createdAt = t, updatedAt = t)
    private fun bill(amount: Double, date: String) = Entry(id = "b${++t}", type = EntryType.CARD_BILL, amount = amount, date = LocalDate.parse(date),
        mode = "UPI", card = "HDFC", createdAt = t, updatedAt = t)

    private val list = listOf(
        spend(1000.0, "2026-09-20"), spend(500.0, "2026-10-10"),   // statement 16 Sep – 15 Oct: 1500
        bill(1500.0, "2026-10-25"),                                 // paid after it closed
        spend(2000.0, "2026-10-20"), spend(300.0, "2026-11-02"),   // statement 16 Oct – 15 Nov (open)
    )

    @Test fun `with a statement day, bills are matched to the statement they pay`() {
        val c = list.cardCycles("HDFC", 15, today)
        assertEquals(LocalDate.parse("2026-10-16"), c[0].from); assertEquals(true, c[0].open); assertEquals(2300.0, c[0].logged, 0.0)
        assertEquals(LocalDate.parse("2026-10-15"), c[1].to); assertEquals(Verdict.MATCHES, c[1].verdict)
    }

    @Test fun `without a statement day, each bill covers everything since the previous bill`() {
        val c = list.cardCycles("HDFC", null, today)
        // up to 25 Oct: 1000 + 500 + 2000 = 3500 logged, 1500 paid
        assertEquals(Verdict.PAID_LESS, c[1].verdict); assertEquals(-2000.0, c[1].difference, 0.0)
        assertEquals(300.0, c[0].logged, 0.0)
    }

    @Test fun `paying more than was logged shows the gap`() {
        val c = (list + bill(2450.0, "2026-11-20")).cardCycles("HDFC", 15, LocalDate.parse("2026-11-21"))
        val closed = c.first { it.to == LocalDate.parse("2026-11-15") }
        assertEquals(Verdict.PAID_MORE, closed.verdict); assertEquals(150.0, closed.difference, 0.0)
    }

    @Test fun `a bill paid just after the statement closed is matched to that statement`() {
        // first ever activity: a bill on 7 Nov for the statement that closed 5 Nov (nothing logged in it)
        val c = listOf(spend(2499.0, "2026-11-06"), bill(2000.0, "2026-11-07")).cardCycles("HDFC", 5, today)
        assertEquals(2, c.size)
        assertEquals(LocalDate.parse("2026-11-05"), c[1].to); assertEquals(Verdict.PAID_MORE, c[1].verdict)
        assertEquals(true, c[0].open); assertEquals(2499.0, c[0].logged, 0.0)
    }

    @Test fun `the sheet suggests the closed statement that isn't paid yet`() {
        assertEquals(2300.0, list.suggestedBill("HDFC", 15, LocalDate.parse("2026-11-18"))!!.logged, 0.0)
        assertEquals(300.0, list.suggestedBill("HDFC", null, today)!!.logged, 0.0)
    }
}
