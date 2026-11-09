package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.cardDues
import com.himalayasoni.ledger.core.domain.cost
import com.himalayasoni.ledger.core.domain.outAndIn
import com.himalayasoni.ledger.core.domain.refundsAwaited
import com.himalayasoni.ledger.core.domain.spentIn
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CardsTest {
    private val today = LocalDate.parse("2026-11-09")
    private var t = 0L
    private fun e(type: EntryType, amount: Double, mode: String = "UPI", card: String = "", refund: Refund? = null) =
        Entry(id = "e${++t}", type = type, amount = amount, date = today, mode = mode, card = card, refund = refund, createdAt = t, updatedAt = t)

    @Test fun `a confirmed refund lowers spending straight away and shows as awaited`() {
        val ticket = e(EntryType.SPENT, 1500.0, refund = Refund(1320.0, RefundStatus.AWAITED, today.minusDays(9)))
        assertEquals(180.0, ticket.cost(), 0.0)
        assertEquals(180.0, listOf(ticket).spentIn(YearMonth.from(today)), 0.0)
        val awaited = listOf(ticket).refundsAwaited(today).single()
        assertEquals(1320.0, awaited.amount, 0.0); assertEquals(9, awaited.days)
    }

    @Test fun `card bills are not spending`() {
        val list = listOf(e(EntryType.SPENT, 2500.0, "Card", "HDFC"), e(EntryType.CARD_BILL, 2500.0, card = "HDFC"))
        assertEquals(2500.0, list.spentIn(YearMonth.from(today)), 0.0)
        assertEquals(2500.0, list.outAndIn().first, 0.0)
    }

    @Test fun `card due counts charges, refunds back to the card, and bills`() {
        val list = listOf(
            e(EntryType.SPENT, 3000.0, "Card", "HDFC"),
            e(EntryType.SPENT, 1000.0, "Card", "HDFC", Refund(400.0, RefundStatus.RECEIVED, today, today, "Card", "HDFC")),
            e(EntryType.SPENT, 500.0, "Card", "SBI"),
            e(EntryType.CARD_BILL, 2000.0, card = "HDFC"),
            e(EntryType.CARD_BILL, 900.0, card = "SBI"),
        )
        val dues = list.cardDues().associateBy { it.name }
        assertEquals(1600.0, dues["HDFC"]!!.due, 0.0)          // 3000 + 1000 − 400 − 2000
        assertEquals(0.0, dues["SBI"]!!.due, 0.0)
        assertEquals(400.0, dues["SBI"]!!.notLogged, 0.0)      // paid 900 for 500 of logged spends
    }
}
