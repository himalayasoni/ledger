package com.himalayasoni.ledger.ui.home

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeStateTest {
    private val today = LocalDate.of(2026, 11, 4)
    private fun e(id: String, type: EntryType, amount: Double, date: LocalDate, desc: String = "", person: String = "", cat: String = "") =
        Entry(id = id, type = type, amount = amount, date = date, description = desc, person = person, category = cat, createdAt = 0, updatedAt = 0)

    @Test fun `month totals and day groups`() {
        val s = buildHomeState(listOf(
            e("1", EntryType.SPENT, 20.0, today, "Chai", cat = "Food"),
            e("2", EntryType.LENT, 500.0, today, person = "Ravi"),
            e("3", EntryType.SPENT, 1450.0, today.minusDays(1), "Groceries", cat = "Groceries"),
        ), today)
        assertEquals("₹1,470", s.spent)
        assertEquals("₹500", s.owedToYou)
        assertEquals(listOf("Today", "Yesterday"), s.days.map { it.label })
        assertEquals("Lent · Ravi", s.days[0].rows[1].title)
        assertEquals("−₹20", s.days[0].rows[0].amount)
    }
}
