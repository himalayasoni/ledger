package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.DateRange
import com.himalayasoni.ledger.core.domain.Filters
import com.himalayasoni.ledger.core.domain.SortBy
import com.himalayasoni.ledger.core.domain.outAndIn
import com.himalayasoni.ledger.core.domain.search
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SearchTest {
    private val today = LocalDate.parse("2026-11-07")
    private var t = 0L
    private fun e(desc: String, amount: Double, date: String, type: EntryType = EntryType.SPENT, cat: String = "Food", person: String = "",
                  with: List<String> = emptyList(), note: String = "", mode: String = "UPI") =
        Entry(id = "e${++t}", type = type, amount = amount, date = LocalDate.parse(date), description = desc, category = cat,
            person = person, withPeople = with, note = note, mode = mode, createdAt = t, updatedAt = t)

    private val all = listOf(
        e("Chai", 20.0, "2026-11-07", mode = "Cash"),
        e("Dinner at Toit", 3200.0, "2026-11-04", with = listOf("Rahul", "Amit"), note = "Birthday treat", mode = "Card"),
        e("Rent", 18000.0, "2026-10-14", cat = "Rent"),
        e("", 2000.0, "2026-09-27", type = EntryType.LENT, cat = "", person = "Rahul"),
        e("Salary", 50000.0, "2026-10-31", type = EntryType.RECEIVED, cat = "Salary"),
    )

    private fun names(f: Filters) = all.search(f, today).map { it.description.ifBlank { it.person } }

    @Test fun `every typed word must match somewhere`() {
        assertEquals(listOf("Dinner at Toit"), names(Filters(query = "rahul treat")))
        assertEquals(listOf("Rent"), names(Filters(query = "18000")))
        assertEquals(listOf("Rent"), names(Filters(query = "14 oct")))
    }

    @Test fun `ranges, people and types`() {
        assertEquals(listOf("Chai", "Dinner at Toit"), names(Filters(range = DateRange.LAST_7_DAYS)))
        assertEquals(listOf("Salary", "Rent"), names(Filters(range = DateRange.LAST_MONTH)))
        assertEquals(listOf("Dinner at Toit", "Rahul"), names(Filters(person = "rahul")))
        assertEquals(listOf("Rahul"), names(Filters(types = setOf(EntryType.LENT))))
    }

    @Test fun `amount limits, comments and sorting`() {
        assertEquals(listOf("Salary", "Rent"), names(Filters(min = 10000.0, sort = SortBy.BIGGEST)))
        assertEquals(listOf("Dinner at Toit"), names(Filters(onlyWithNote = true)))
        assertEquals("Rahul", names(Filters(sort = SortBy.OLDEST)).first())
    }

    @Test fun `out and in`() {
        val (out, inn) = all.outAndIn()
        assertEquals(20 + 3200 + 18000 + 2000.0, out, 0.0)
        assertEquals(50000.0, inn, 0.0)
    }
}
