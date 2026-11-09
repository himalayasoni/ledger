package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.voice.VoiceContext
import com.himalayasoni.ledger.core.voice.jsNumberToString
import com.himalayasoni.ledger.core.voice.parseLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VoiceParserTest {
    private val ctx = VoiceContext(people = listOf("Rahul", "Priya", "Kanika", "Neha Gupta"))

    @Test fun `a plain spend gets amount, description and category`() {
        val r = parseLine("spent 200 on lunch", ctx)
        assertEquals(EntryType.SPENT, r.type)
        assertEquals(200.0, r.amount, 0.0)
        assertEquals("Lunch", r.description)
        assertEquals("Food", r.category)
    }

    @Test fun `loans find the type and the person`() {
        assertEquals(EntryType.LENT to "Rahul", parseLine("lent 500 to rahul", ctx).let { it.type to it.person })
        assertEquals(EntryType.BORROWED to "Priya", parseLine("Priya gave me 750", ctx).let { it.type to it.person })
        assertEquals(EntryType.GOT_BACK to "Rahul", parseLine("got back 300 from rahul", ctx).let { it.type to it.person })
        assertEquals(EntryType.PAID_BACK to "Kanika", parseLine("paid back 100 to kanika", ctx).let { it.type to it.person })
    }

    @Test fun `speech-recognition slips still work`() {
        assertEquals(EntryType.LENT, parseLine("lented 200 to ashish", ctx).type)
        assertEquals("Kanika", parseLine("lent 300 to kanikaa", ctx).person) // one letter off a known name
        assertEquals("Neha Gupta", parseLine("received 1000 from neha", ctx).person) // first name finds the full name
    }

    @Test fun `amounts can be words, k, lakh or have commas`() {
        assertEquals(1500.0, parseLine("1.5k on dinner", ctx).amount, 0.0)
        assertEquals(3500.0, parseLine("three thousand five hundred rent", ctx).amount, 0.0)
        assertEquals(100000.0, parseLine("paid 1,00,000 for the hotel", ctx).amount, 0.0)
        assertEquals(200000.0, parseLine("two lakh for the car", ctx).amount, 0.0)
        assertEquals(0.0, parseLine("spent on lunch", ctx).amount, 0.0)
    }

    @Test fun `shared spends list the people, mode and day are picked up`() {
        val r = parseLine("dinner 1800 with Rahul and Priya by card yesterday", ctx)
        assertEquals(listOf("Rahul", "Priya"), r.withPeople)
        assertEquals("Card", r.mode)
        assertEquals(1, r.daysAgo)
        assertEquals(LocalDate.of(2026, 11, 6), r.date(LocalDate.of(2026, 11, 7)))
        assertEquals("Dinner", r.description)
    }

    // Saturday 7 Nov 2026: dates said without a year or month count back from this day.
    private val saturday = LocalDate.of(2026, 11, 7)
    private val onSaturday = ctx.copy(today = saturday)
    private fun dateOf(line: String) = parseLine(line, onSaturday).date(saturday)

    @Test fun `weekdays and days of the month are the latest one that isn't in the future`() {
        assertEquals(LocalDate.of(2026, 11, 2), dateOf("spent 400 on monday on dinner"))
        assertEquals(LocalDate.of(2026, 11, 6), dateOf("last friday paid 450 for uber"))
        assertEquals(saturday, dateOf("on saturday chai 20")) // said on a Saturday: today
        assertEquals(LocalDate.of(2026, 10, 31), dateOf("last saturday chai 20")) // "last" is never today
        assertEquals(LocalDate.of(2026, 11, 3), dateOf("spent 120 on the 3rd"))
        assertEquals(LocalDate.of(2026, 10, 8), dateOf("spent 50 on 8th")) // the 8th hasn't come yet this month
        assertEquals(LocalDate.of(2025, 12, 25), dateOf("on 25/12 bought gift 500")) // day first, as in India
        assertEquals(LocalDate.of(2026, 11, 3), dateOf("3rd nov dinner 800"))
        assertEquals(LocalDate.of(2026, 11, 3), dateOf("nov 3 dinner 800"))
    }

    @Test fun `date words don't end up in the description or the amount`() {
        val r = parseLine("on 3 november spent 250 on lunch", onSaturday)
        assertEquals(250.0, r.amount, 0.0)
        assertEquals("Lunch", r.description)
        assertEquals("2nd hand cycle", parseLine("bought 2nd hand cycle 3000", onSaturday).description.lowercase()) // not a date
    }

    @Test fun `fillers go but real words that contain them stay`() {
        assertEquals("Momos", parseLine("um so like I spent uh 200 on uh momos", ctx).description)
        assertEquals("Chai", parseLine("maine 250 ki chai", ctx).description)
        assertEquals("Uber", parseLine("uber 240", ctx).description)
        assertEquals("Kiwi", parseLine("kiwi 120", ctx).description)
        assertEquals("Likes", parseLine("likes 50", ctx).description)
    }

    @Test fun `currency glued to the digits is still the amount`() {
        for (line in listOf("Rs500 for wifi", "rs.500 for wifi", "₹500 for wifi", "inr 500 for wifi", "paid 500/- for wifi")) {
            val r = parseLine(line, ctx)
            assertEquals(line, 500.0, r.amount, 0.0)
            assertEquals(line, "Wifi", r.description)
        }
        assertEquals(1200.0, parseLine("Rs 1,200 for shoes", ctx).amount, 0.0)
    }

    @Test fun `repaying and getting back find the person next to the verb`() {
        assertEquals(EntryType.PAID_BACK to "Rahul", parseLine("repaid rahul 5000", ctx).let { it.type to it.person })
        assertEquals(EntryType.PAID_BACK to "Neha Gupta", parseLine("paid back neha 200", ctx).let { it.type to it.person })
        assertEquals(EntryType.GOT_BACK to "Rahul", parseLine("rahul returned 500", ctx).let { it.type to it.person })
        assertEquals(EntryType.RECEIVED, parseLine("got 15000 for freelance gig", ctx).type)
        assertEquals(EntryType.SPENT, parseLine("got pizza for 300", ctx).type) // "got" a thing is a spend
    }

    @Test fun `a shop after from is not a person, but a new name on a loan is`() {
        val refund = parseLine("refund 799 from flipkart", ctx)
        assertEquals("" to "Refund flipkart", refund.person to refund.description)
        assertEquals("Sameer", parseLine("borrowed 500 from sameer", ctx).person)
        val withOla = VoiceContext(people = listOf("Ola"))
        assertEquals("Ola", parseLine("received 200 from ola", withOla).person) // you know an Ola: a person
        assertEquals("Ola" to "Transport", parseLine("ola 240", withOla).let { it.description to it.category }) // still a cab ride
    }

    @Test fun `every name after with is someone the spend was shared with`() {
        assertEquals(listOf("Rahul", "Priya"), parseLine("spent 300 with rahul with priya", ctx).withPeople)
        assertEquals(listOf("Rahul", "Priya", "Neha Gupta"), parseLine("dinner 1200 with rahul, priya and neha", ctx).withPeople)
        assertEquals(listOf("Neha Gupta", "Priya"), parseLine("lunch 900 with neha gupta and priya", ctx).withPeople)
    }

    @Test fun `letters of any script stay, absurd amounts and amounts joined by and`() {
        assertEquals("Café latte", parseLine("spent 200 on café latte", ctx).description)
        assertEquals("चाय", parseLine("चाय 20", ctx).description)
        assertEquals("José", parseLine("lent 500 to josé", ctx).person)
        val huge = parseLine("spent 99999999999999999999999 on everything", ctx)
        assertEquals(0.0 to "Everything", huge.amount to huge.description)
        val two = parseLine("spent 50 on parking and 20 on toll", ctx)
        assertEquals(70.0 to "Parking and toll", two.amount to two.description)
        assertEquals("Constructor", parseLine("constructor 500", ctx).description) // a JavaScript quirk, fixed on the web too
    }

    @Test fun `numbers are written back into the text the way JavaScript writes them`() {
        assertEquals("200", jsNumberToString(200.0))
        assertEquals("1.5", jsNumberToString(1.5))
        assertEquals("0.05", jsNumberToString(0.05))
        assertEquals("1e+23", jsNumberToString(1e23))
    }
}
