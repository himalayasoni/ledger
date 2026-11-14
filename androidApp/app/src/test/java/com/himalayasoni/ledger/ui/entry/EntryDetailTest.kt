package com.himalayasoni.ledger.ui.entry

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import com.himalayasoni.ledger.ui.home.entryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryDetailTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun spend(amount: Double, refund: Refund? = null, with: List<String> = emptyList(), mode: String = "UPI", card: String = "") = Entry(
        id = "s", type = EntryType.SPENT, amount = amount, date = today.minusDays(3), description = "Train to Jaipur",
        category = "Travel", mode = mode, card = card, withPeople = with, refund = refund, createdAt = 1, updatedAt = 1,
    )
    private fun actions(d: EntryDetail) = d.actions.map { it.action }

    @Test fun `a spend with money back shows what it came to and what was paid`() {
        val e = spend(1500.0, Refund(1320.0, RefundStatus.AWAITED, today.minusDays(1)))
        val d = buildEntryDetail(e, listOf(e), today)
        assertEquals("₹180", d.amount)
        assertEquals("₹1,500 paid", d.paidLine)
        assertTrue(d.moneyBack!!.awaited)
        assertEquals("Refund", d.moneyBack!!.title)
        assertEquals(listOf(EntryAction.MARK_ARRIVED, EntryAction.EDIT, EntryAction.MONEY_BACK, EntryAction.DUPLICATE, EntryAction.DELETE), actions(d))
        assertEquals("Edit money back", d.actions.first { it.action == EntryAction.MONEY_BACK }.label)
        assertTrue(d.actions.first().primary)
    }

    @Test fun `a plain spend offers Got money back, with Edit as the main button`() {
        val d = buildEntryDetail(spend(240.0), emptyList(), today)
        assertNull(d.paidLine)
        assertNull(d.moneyBack)
        assertEquals("Got money back", d.actions.first { it.action == EntryAction.MONEY_BACK }.label)
        assertTrue(d.actions.first { it.action == EntryAction.EDIT }.primary)
        assertEquals(1, d.actions.count { it.primary })
    }

    @Test fun `a card spend links to its card`() {
        val d = buildEntryDetail(spend(2499.0, mode = "Card", card = "HDFC"), emptyList(), today)
        val f = d.facts.first { it.label == "Paid with" }
        assertEquals("HDFC card", f.value)
        assertEquals("HDFC", f.card)
    }

    @Test fun `a shared spend lists each share and who paid back`() {
        val e = spend(3000.0, with = listOf("Rahul", "Amit")).copy(paidBack = mapOf("rahul" to 1000.0, "amit" to 400.0))
        val d = buildEntryDetail(e, listOf(e), today)
        assertEquals("Split 3 ways · ₹1,000 each", d.sharesTitle)
        assertEquals(listOf("Paid back", "₹400 paid back"), d.shares.map { it.status })
        assertTrue(d.shares[0].settled)
        assertFalse(d.shares[1].settled)
    }

    @Test fun `a loan with an open balance offers Got it back`() {
        val lent = Entry(id = "l", type = EntryType.LENT, amount = 2000.0, date = today, person = "Rahul", createdAt = 1, updatedAt = 1)
        val back = lent.copy(id = "b", type = EntryType.GOT_BACK, amount = 500.0)
        val d = buildEntryDetail(lent, listOf(lent, back), today)
        assertEquals(1500.0, d.balance, 0.0)
        assertEquals("Rahul", d.person)
        assertEquals(EntryAction.GOT_IT_BACK, d.actions.first().action)
        assertFalse(EntryAction.MONEY_BACK in actions(d))
    }

    @Test fun `rows know when to offer money back and the Mark arrived chip`() {
        assertTrue(entryRow(spend(100.0)).canMoneyBack)
        val awaited = entryRow(spend(100.0, Refund(100.0, RefundStatus.AWAITED, today)))
        assertFalse(awaited.canMoneyBack)
        assertTrue(awaited.refundAwaited)
        assertFalse(entryRow(spend(100.0, Refund(100.0, RefundStatus.RECEIVED, today, today, "UPI"))).refundAwaited)
    }
}
