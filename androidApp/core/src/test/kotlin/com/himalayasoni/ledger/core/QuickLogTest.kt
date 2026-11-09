package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.ReplyResult
import com.himalayasoni.ledger.core.domain.loggedLine
import com.himalayasoni.ledger.core.domain.parseReply
import com.himalayasoni.ledger.core.domain.shortTitle
import com.himalayasoni.ledger.core.domain.toEntry
import com.himalayasoni.ledger.core.domain.usuals
import com.himalayasoni.ledger.core.domain.voiceContext
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickLogTest {
    private val today = LocalDate.parse("2026-11-09")
    private var t = 1_000L
    private fun e(desc: String, amount: Double, type: EntryType = EntryType.SPENT, cat: String = "Food", person: String = "",
                  mode: String = "UPI", card: String = "", with: List<String> = emptyList(), date: LocalDate = today.minusDays(3)) =
        Entry(id = "e${++t}", type = type, amount = amount, date = date, description = desc, category = cat, person = person,
            mode = mode, card = card, withPeople = with, createdAt = t, updatedAt = t)

    @Test fun `a usual logged again keeps its details and is dated today`() {
        val u = listOf(e("Chai", 20.0, mode = "Cash"), e("Chai", 20.0, mode = "Cash")).usuals().single()
        val entry = u.toEntry("new1", today, 5_000)
        assertEquals("new1", entry.id)
        assertEquals(today, entry.date)
        assertEquals(EntryType.SPENT, entry.type)
        assertEquals(20.0, entry.amount, 0.0)
        assertEquals("Food", entry.category)
        assertEquals("Cash", entry.mode)
        assertEquals(5_000, entry.createdAt)
    }

    @Test fun `a card usual stays on its card, a shared one keeps its people`() {
        val card = listOf(e("Netflix", 649.0, cat = "Fun", mode = "Card", card = "HDFC")).usuals().single().toEntry("a", today, 1)
        assertEquals("HDFC", card.card)
        val shared = listOf(e("Lunch", 600.0, with = listOf("Rahul"))).usuals().single().toEntry("b", today, 1)
        assertEquals(listOf("Rahul"), shared.withPeople)
        val loan = listOf(e("", 0.0), e("Auto", 300.0, EntryType.LENT, cat = "", person = "Priya")).usuals().first().toEntry("c", today, 1)
        assertEquals("Priya", loan.person); assertEquals("", loan.category)
    }

    @Test fun `a typed reply with an amount is saved straight away`() {
        val history = listOf(e("Chai", 20.0), e("Lent", 500.0, EntryType.LENT, cat = "", person = "Rahul"))
        val ctx = history.voiceContext(emptySet(), today)
        val r = parseReply("chai 20 cash", ctx, "id1", 9_000)
        assertTrue(r is ReplyResult.Ready)
        val entry = (r as ReplyResult.Ready).entry
        assertEquals(20.0, entry.amount, 0.0)
        assertEquals("Chai", entry.description)
        assertEquals("Cash", entry.mode)
        assertEquals("Food", entry.category)
        assertEquals(today, entry.date)
        assertEquals("Logged ₹20 · Chai", entry.loggedLine())
    }

    @Test fun `a reply says what's missing instead of guessing`() {
        val ctx = emptyList<Entry>().voiceContext(emptySet(), today)
        assertEquals(ReplyResult.Incomplete("the amount"), parseReply("chai", ctx, "x", 1))
        assertEquals(ReplyResult.Incomplete("who it was with"), parseReply("lent 200", ctx, "x", 1))
        assertEquals(ReplyResult.Empty, parseReply("   ", ctx, "x", 1))
    }

    @Test fun `yesterday in a reply dates the entry yesterday`() {
        val r = parseReply("auto 80 yesterday", emptyList<Entry>().voiceContext(emptySet(), today), "x", 1) as ReplyResult.Ready
        assertEquals(today.minusDays(1), r.entry.date)
        assertEquals("Transport", r.entry.category)
    }

    @Test fun `the parser never learns names or usuals from private entries`() {
        val history = listOf(e("Therapy", 2000.0, cat = "Health"), e("Chai", 20.0))
        val ctx = history.voiceContext(setOf("health"), today)
        assertFalse(ctx.usuals.any { it.description == "Therapy" })
        assertTrue(ctx.usuals.any { it.description == "Chai" })
    }

    @Test fun `a short title never names a private category`() {
        val x = e("", 500.0, cat = "Health")
        assertEquals("Health", x.shortTitle())
        assertEquals("Spent", x.shortTitle(setOf("health")))
        assertEquals("Lent to Ashish", e("", 200.0, EntryType.LENT, cat = "", person = "Ashish").shortTitle())
    }
}
