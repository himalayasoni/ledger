package com.himalayasoni.ledger.ui.entry

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyBackFormTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val ticket = Entry(
        id = "t", type = EntryType.SPENT, amount = 1500.0, date = today.minusDays(3), description = "Train to Jaipur",
        category = "Travel", mode = "Card", card = "HDFC", createdAt = 1, updatedAt = 1,
    )

    @Test fun `opens as shop refund, full, awaited`() {
        val f = moneyBackForm(ticket, listOf("Dad", "Sneha"), listOf("SBI"), today)
        assertEquals("", f.from)
        assertTrue(f.full)
        assertFalse(f.arrived)
        assertFalse(f.existing)
        assertEquals(1500.0, f.amount, 0.0)
        assertEquals("Counts as ₹0 spent · ₹1,500 shows as awaited until it arrives", f.countsAs)
        assertEquals(listOf("SBI", "HDFC"), f.cards)
    }

    @Test fun `people the spend is split with are not offered`() {
        val f = moneyBackForm(ticket.copy(withPeople = listOf("Sneha")), listOf("Dad", "sneha", "Rahul"), emptyList(), today)
        assertEquals(listOf("Dad", "Rahul"), f.people)
        assertEquals(listOf("Sneha"), f.shared)
    }

    @Test fun `picking a person switches to arrived, back to the card`() {
        val f = moneyBackForm(ticket, listOf("Dad"), emptyList(), today).pickFrom("Dad")
        assertTrue(f.arrived)
        assertEquals("Card", f.to)
        assertEquals("HDFC", f.card)
        val upi = moneyBackForm(ticket.copy(mode = "UPI", card = ""), emptyList(), emptyList(), today).pickArrived(true)
        assertEquals("UPI", upi.to)
    }

    @Test fun `the part is capped at the amount`() {
        val f = moneyBackForm(ticket, emptyList(), emptyList(), today)
        assertEquals("1320", f.typePart("1,320").partText)
        assertEquals(180.0, 1500 - f.typePart("1320").amount, 0.0)
        assertEquals("1500", f.typePart("99999").partText)
        assertEquals("Type how much came back", f.pickFull(false).problem())
        assertNull(f.problem())
    }

    @Test fun `opens existing money back as it is`() {
        val e = ticket.copy(refund = Refund(500.0, RefundStatus.RECEIVED, today.minusDays(2), today.minusDays(1), "UPI", from = "Dad"))
        val f = moneyBackForm(e, listOf("Sneha"), emptyList(), today)
        assertTrue(f.existing)
        assertEquals("Dad", f.from)
        assertEquals(listOf("Dad", "Sneha"), f.people)
        assertFalse(f.full)
        assertEquals("500", f.partText)
        assertTrue(f.arrived)
        assertEquals(today.minusDays(1), f.received)
        assertEquals(today.minusDays(2), f.confirmed)
    }
}
