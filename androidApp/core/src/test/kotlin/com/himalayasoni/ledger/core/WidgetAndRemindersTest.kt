package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.CardSettings
import com.himalayasoni.ledger.core.domain.HIDDEN_AMOUNT
import com.himalayasoni.ledger.core.domain.cardBillNudges
import com.himalayasoni.ledger.core.domain.needsDailyNudge
import com.himalayasoni.ledger.core.domain.nextDaily
import com.himalayasoni.ledger.core.domain.nextWeekly
import com.himalayasoni.ledger.core.domain.owedNudge
import com.himalayasoni.ledger.core.domain.refundNudges
import com.himalayasoni.ledger.core.domain.stillRelevant
import com.himalayasoni.ledger.core.domain.widgetLogged
import com.himalayasoni.ledger.core.domain.widgetSummary
import com.himalayasoni.ledger.core.domain.widgetUsuals
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class WidgetAndRemindersTest {
    private val today = LocalDate.parse("2026-11-09") // a Monday
    private var t = 0L
    private fun at(d: LocalDate) = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() + (++t)
    private fun e(desc: String, amount: Double, cat: String = "Food", date: LocalDate = today, type: EntryType = EntryType.SPENT,
                  person: String = "", mode: String = "UPI", card: String = "", refund: Refund? = null, logged: LocalDate = date) =
        Entry(id = "e${++t}", type = type, amount = amount, date = date, description = desc, category = cat, person = person,
            mode = mode, card = card, refund = refund, createdAt = at(logged), updatedAt = at(logged))

    private val entries = listOf(
        e("Chai", 20.0), e("Chai", 20.0, date = today.minusDays(1)), e("Chai", 20.0, date = today.minusDays(2)),
        e("Uber", 240.0, "Transport", today.minusDays(1)), e("Uber", 260.0, "Transport", today.minusDays(4)),
        e("Concert", 1500.0, "Fun", today.minusDays(3)),                                    // only once: not a usual
        e("Therapy", 2000.0, "Health"), e("Therapy", 2000.0, "Health", today.minusDays(7)), // private
        e("HDFC", 5000.0, "", today.minusDays(1), EntryType.CARD_BILL, card = "HDFC"),
    )
    private val private = setOf("health")

    /* ---------- widget ---------- */

    @Test fun `the widget shows this month and today, without private spends`() {
        val w = widgetSummary(entries, private, today, hideAmounts = false)
        assertEquals("₹20", w.today)
        assertEquals("₹2,060", w.month) // 20×3 + 240 + 260 + 1500; no therapy, a card bill isn't spending
        assertEquals(listOf("Chai", "Uber"), w.chips.map { it.label })
        assertEquals(listOf("₹20", "₹240"), w.chips.map { it.amount })
    }

    @Test fun `private usuals never reach the widget, even when used most`() {
        val lots = entries + (1..5).map { e("Therapy", 2000.0, "Health", today.minusDays(it.toLong())) }
        assertFalse(lots.widgetUsuals(private).any { it.description == "Therapy" })
        assertTrue(lots.widgetUsuals(emptySet()).any { it.description == "Therapy" })
    }

    @Test fun `hidden amounts show dots and chips without prices`() {
        val w = widgetSummary(entries, private, today, hideAmounts = true)
        assertEquals(HIDDEN_AMOUNT, w.month); assertEquals(HIDDEN_AMOUNT, w.today)
        assertTrue(w.chips.all { it.amount.isEmpty() })
        val chai = entries.widgetUsuals(private).first()
        assertEquals("Logged Chai", chai.widgetLogged(true))
        assertEquals("Logged Chai ₹20", chai.widgetLogged(false))
    }

    @Test fun `at most four chips`() {
        val many = (1..6).flatMap { i -> listOf(e("Thing $i", 10.0 * i), e("Thing $i", 10.0 * i)) }
        assertEquals(4, widgetSummary(many, emptySet(), today, false).chips.size)
    }

    /* ---------- reminder times ---------- */

    @Test fun `the daily alarm goes off later today, or tomorrow once it's past`() {
        assertEquals(LocalDateTime.parse("2026-11-09T21:00"), nextDaily(LocalDateTime.parse("2026-11-09T18:30"), 21 * 60))
        assertEquals(LocalDateTime.parse("2026-11-10T21:00"), nextDaily(LocalDateTime.parse("2026-11-09T21:00"), 21 * 60))
    }

    @Test fun `the weekly summary goes off on Sunday evening`() {
        assertEquals(LocalDateTime.parse("2026-11-15T19:00"), nextWeekly(LocalDateTime.parse("2026-11-09T10:00"), DayOfWeek.SUNDAY, 19 * 60))
        assertEquals(LocalDateTime.parse("2026-11-22T19:00"), nextWeekly(LocalDateTime.parse("2026-11-15T19:30"), DayOfWeek.SUNDAY, 19 * 60))
    }

    @Test fun `no daily nudge once something was logged today`() {
        val old = listOf(e("Chai", 20.0, date = today, logged = today.minusDays(1)))
        assertTrue(old.needsDailyNudge(today, ZoneOffset.UTC))
        // an entry for last week, logged today, still counts as logging today
        val now = old + e("Rent", 18000.0, "Rent", today.minusDays(7), logged = today)
        assertFalse(now.needsDailyNudge(today, ZoneOffset.UTC))
    }

    /* ---------- refunds ---------- */

    @Test fun `a refund awaited a week gets one reminder`() {
        val ticket = e("Train to Jaipur", 1500.0, "Travel", today.minusDays(10), refund = Refund(1320.0, RefundStatus.AWAITED, today.minusDays(8)))
        val fresh = e("Shoes", 2000.0, "Shopping", refund = Refund(2000.0, RefundStatus.AWAITED, today.minusDays(2)))
        val n = listOf(ticket, fresh).refundNudges(emptySet(), today, emptySet()).single()
        assertEquals("Train to Jaipur: ₹1,320 refund still awaited after 8 days", n.text)
        assertEquals(ticket.id, n.entryId)
        assertTrue(listOf(ticket).refundNudges(emptySet(), today, setOf(n.key)).isEmpty())
    }

    @Test fun `a private refund is never mentioned`() {
        val x = e("Clinic", 900.0, "Health", today.minusDays(20), refund = Refund(900.0, RefundStatus.AWAITED, today.minusDays(15)))
        assertTrue(listOf(x).refundNudges(private, today, emptySet()).isEmpty())
    }

    /* ---------- card bills ---------- */

    private val spends = listOf(
        e("Shoes", 2499.0, "Shopping", today.minusDays(5), mode = "Card", card = "HDFC"),
        e("Groceries", 1801.0, "Groceries", today.minusDays(3), mode = "Card", card = "HDFC"),
    )

    @Test fun `card bill reminder three days before the due day`() {
        val cards = listOf(CardSettings("HDFC", statementDay = 20, dueDay = 12))
        val n = spends.cardBillNudges(emptySet(), cards, today, emptySet()).single()
        assertEquals("HDFC bill due 12 Nov · Ledger shows ₹4,300", n.text)
        assertEquals("HDFC bill due in 3 days", n.title)
        assertEquals("HDFC", n.card)
        // once only
        assertTrue(spends.cardBillNudges(emptySet(), cards, today, setOf(n.key)).isEmpty())
    }

    @Test fun `no card reminder too early, with nothing owed, or with no due day`() {
        assertTrue(spends.cardBillNudges(emptySet(), listOf(CardSettings("HDFC", dueDay = 20)), today, emptySet()).isEmpty())
        val paid = spends + e("", 4300.0, "", today, EntryType.CARD_BILL, card = "HDFC")
        assertTrue(paid.cardBillNudges(emptySet(), listOf(CardSettings("HDFC", dueDay = 10)), today, emptySet()).isEmpty())
        assertTrue(spends.cardBillNudges(emptySet(), listOf(CardSettings("HDFC")), today, emptySet()).isEmpty())
    }

    @Test fun `a bill due tomorrow still reminds if the earlier day was missed`() {
        val n = spends.cardBillNudges(emptySet(), listOf(CardSettings("HDFC", dueDay = 10)), today, emptySet()).single()
        assertEquals("HDFC bill due tomorrow", n.title)
    }

    /* ---------- people ---------- */

    @Test fun `weekly summary lists who owes you, biggest first`() {
        val loans = listOf(
            e("", 300.0, "", type = EntryType.LENT, person = "Priya"),
            e("", 2000.0, "", type = EntryType.LENT, person = "Rahul"),
            e("", 500.0, "", type = EntryType.GOT_BACK, person = "Rahul"),
            e("", 400.0, "", type = EntryType.BORROWED, person = "Amit"),
        )
        val n = loans.owedNudge(emptySet())!!
        assertEquals("Owed to you: Rahul ₹1,500 · Priya ₹300", n.text)
        assertEquals("₹1,800 owed to you", n.title)
        assertNull(listOf(loans[3]).owedNudge(emptySet()))
    }

    @Test fun `remembered reminders are forgotten once they can't come back`() {
        val ticket = e("Train", 1500.0, "Travel", refund = Refund(1320.0, RefundStatus.AWAITED, today.minusDays(9)))
        val kept = listOf(ticket).stillRelevant(setOf("refund:${ticket.id}", "refund:gone", "card:hdfc:2026-11-12", "card:hdfc:2026-10-12"), today)
        assertEquals(setOf("refund:${ticket.id}", "card:hdfc:2026-11-12"), kept)
    }
}
