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

    @Test fun `numbers are written back into the text the way JavaScript writes them`() {
        assertEquals("200", jsNumberToString(200.0))
        assertEquals("1.5", jsNumberToString(1.5))
        assertEquals("0.05", jsNumberToString(0.05))
        assertEquals("1e+23", jsNumberToString(1e23))
    }
}
