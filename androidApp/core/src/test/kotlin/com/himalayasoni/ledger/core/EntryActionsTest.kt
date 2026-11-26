package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.cost
import com.himalayasoni.ledger.core.domain.duplicateAsNew
import com.himalayasoni.ledger.core.domain.markRefundArrived
import com.himalayasoni.ledger.core.domain.withMoneyBack
import com.himalayasoni.ledger.core.domain.withoutMoneyBack
import com.himalayasoni.ledger.core.domain.withSharePaid
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryActionsTest {
    private val day = LocalDate.of(2026, 11, 4)
    private val today = LocalDate.of(2026, 11, 14)
    private val now = 1_800_000_000_000L
    private val ticket = Entry(
        id = "t1", type = EntryType.SPENT, amount = 1500.0, date = day, description = "Train to Jaipur",
        category = "Travel", mode = "UPI", createdAt = 1, updatedAt = 1,
    )

    @Test fun `an awaited shop refund has no arrival details and counts at once`() {
        val e = ticket.withMoneyBack(1320.0, "", arrived = false, confirmed = day, received = today, to = "UPI", card = "HDFC", now = now)
        val r = e.refund!!
        assertEquals(1320.0, r.amount, 0.0)
        assertEquals(RefundStatus.AWAITED, r.status)
        assertEquals(day, r.confirmed)
        assertNull(r.received)
        assertEquals("", r.to)
        assertEquals("", r.card)
        assertEquals("", r.from)
        assertEquals(180.0, e.cost(), 0.001)
        assertEquals(now, e.updatedAt)
    }

    @Test fun `money back is capped at the amount and rounded to paise`() {
        assertEquals(1500.0, ticket.withMoneyBack(9999.0, "", false, day, null, "", "", now).refund!!.amount, 0.0)
        assertEquals(100.13, ticket.withMoneyBack(100.126, "", false, day, null, "", "", now).refund!!.amount, 0.0)
    }

    @Test fun `money back from a person, arrived to a card, takes the entry's card when none is picked`() {
        val dinner = ticket.copy(mode = "Card", card = "HDFC")
        val r = dinner.withMoneyBack(1500.0, " Dad ", arrived = true, confirmed = day, received = today, to = "Card", card = "", now = now).refund!!
        assertEquals("Dad", r.from)
        assertEquals(RefundStatus.RECEIVED, r.status)
        assertEquals(today, r.received)
        assertEquals("Card", r.to)
        assertEquals("HDFC", r.card)
        val sbi = dinner.withMoneyBack(1500.0, "", true, day, null, "Card", "SBI", now).refund!!
        assertEquals("SBI", sbi.card)
        assertEquals(day, sbi.received) // no received date given: the confirmed date
        assertEquals("", dinner.withMoneyBack(1500.0, "", true, day, today, "UPI", "SBI", now).refund!!.card)
    }

    @Test fun `only spends get money back, and only a positive amount`() {
        val lent = ticket.copy(type = EntryType.LENT, person = "Rahul")
        assertSame(lent, lent.withMoneyBack(100.0, "", false, day, null, "", "", now))
        assertSame(ticket, ticket.withMoneyBack(0.0, "", false, day, null, "", "", now))
    }

    @Test fun `mark arrived keeps a chosen destination`() {
        val e = ticket.copy(refund = Refund(1320.0, RefundStatus.AWAITED, day, to = "Bank"))
        val r = e.markRefundArrived(today, now).refund!!
        assertEquals(RefundStatus.RECEIVED, r.status)
        assertEquals(today, r.received)
        assertEquals("Bank", r.to)
        assertEquals("", r.card)
        assertEquals(now, e.markRefundArrived(today, now).updatedAt)
    }

    @Test fun `mark arrived sends a card spend back to its card, anything else to how it was paid`() {
        val card = ticket.copy(mode = "Card", card = "HDFC", refund = Refund(1320.0, RefundStatus.AWAITED, day))
        card.markRefundArrived(today, now).refund!!.let { assertEquals("Card", it.to); assertEquals("HDFC", it.card) }
        val cash = ticket.copy(mode = "Cash", refund = Refund(100.0, RefundStatus.AWAITED, day))
        assertEquals("Cash", cash.markRefundArrived(today, now).refund!!.to)
        val blank = ticket.copy(mode = "", refund = Refund(100.0, RefundStatus.AWAITED, day))
        assertEquals("UPI", blank.markRefundArrived(today, now).refund!!.to)
    }

    @Test fun `mark arrived does nothing without an awaited refund`() {
        assertSame(ticket, ticket.markRefundArrived(today, now))
        val done = ticket.copy(refund = Refund(100.0, RefundStatus.RECEIVED, day, day, "UPI"))
        assertSame(done, done.markRefundArrived(today, now))
    }

    @Test fun `duplicate is a new entry today without that one time's extras`() {
        val dinner = ticket.copy(
            description = "Dinner at Toit", category = "Food", mode = "Card", card = "HDFC", note = "Birthday",
            withPeople = listOf("Rahul", "Amit"), includesMe = false, paidBack = mapOf("rahul" to 400.0), prior = 50.0,
            refund = Refund(100.0, RefundStatus.AWAITED, day), deleted = true,
        )
        val copy = dinner.duplicateAsNew("new1", today, now)
        assertEquals("new1", copy.id)
        assertEquals(today, copy.date)
        assertEquals(now, copy.createdAt)
        assertEquals(now, copy.updatedAt)
        assertNull(copy.refund)
        assertTrue(copy.paidBack.isEmpty())
        assertEquals(0.0, copy.prior, 0.0)
        assertEquals("", copy.note)
        assertEquals(false, copy.deleted)
        // kept
        assertEquals(1500.0, copy.amount, 0.0)
        assertEquals("Dinner at Toit", copy.description)
        assertEquals("Food", copy.category)
        assertEquals("Card", copy.mode)
        assertEquals("HDFC", copy.card)
        assertEquals(listOf("Rahul", "Amit"), copy.withPeople)
        assertEquals(false, copy.includesMe)
        assertEquals(EntryType.SPENT, copy.type)
    }

    @Test fun `duplicating a loan keeps the person`() {
        val lent = ticket.copy(type = EntryType.LENT, person = "Rahul", description = "")
        assertEquals("Rahul", lent.duplicateAsNew("n", today, now).person)
    }

    @Test fun `removing money back puts the full amount back`() {
        val e = ticket.copy(refund = Refund(1320.0, RefundStatus.AWAITED, day))
        val off = e.withoutMoneyBack(now)
        assertNull(off.refund)
        assertEquals(1500.0, off.cost(), 0.0)
        assertEquals(now, off.updatedAt)
        assertSame(ticket, ticket.withoutMoneyBack(now))
    }

    @Test fun `marking a share paid back from the entry page, and undoing it`() {
        val party = Entry(
            id = "p1", type = EntryType.SPENT, amount = 3223.5, date = day, description = "Haldiram (Office Party)",
            category = "Party", mode = "UPI", withPeople = listOf("Aryan shah", "Siddhant"), includesMe = true, createdAt = 1, updatedAt = 1,
        )
        val paid = party.withSharePaid("Aryan Shah", true, now)
        assertEquals(1074.5, paid.paidBack["aryan shah"]!!, 0.001)
        assertEquals(3223.5 - 1074.5, paid.cost(), 0.001)
        assertEquals(now, paid.updatedAt)
        val undone = paid.withSharePaid("aryan shah", false, now + 1)
        assertTrue(undone.paidBack.isEmpty())
        assertSame(party, party.withSharePaid("Nobody", true, now))
    }
}
