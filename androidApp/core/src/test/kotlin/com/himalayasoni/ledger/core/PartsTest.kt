package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.domain.byCategory
import com.himalayasoni.ledger.core.domain.cleanParts
import com.himalayasoni.ledger.core.domain.costIn
import com.himalayasoni.ledger.core.domain.mainCategory
import com.himalayasoni.ledger.core.domain.search
import com.himalayasoni.ledger.core.domain.Filters
import com.himalayasoni.ledger.core.domain.withoutPrivate
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PartsTest {
    private val day = LocalDate.of(2026, 11, 26)
    private val parts = listOf(Part(350.0, "Cigarettes"), Part(150.0, "Munchies"))
    private val order = Entry(
        id = "b1", type = EntryType.SPENT, amount = 500.0, date = day, description = "Blinkit",
        category = mainCategory(parts), parts = parts, createdAt = 1, updatedAt = 1,
    )
    private val lunch = Entry(id = "l1", type = EntryType.SPENT, amount = 200.0, date = day, category = "Munchies", createdAt = 1, updatedAt = 1)

    @Test fun `each rupee counts once, in its own category`() {
        val by = listOf(order, lunch).byCategory().toMap()
        assertEquals(350.0, by["Cigarettes"]!!, 0.001)
        assertEquals(350.0, by["Munchies"]!!, 0.001)
        assertEquals(700.0, by.values.sum(), 0.001)
    }

    @Test fun `a shared split order shares every part the same way`() {
        val shared = order.copy(withPeople = listOf("Rahul"), includesMe = true, paidBack = mapOf("rahul" to 250.0)) // Rahul paid his half back
        assertEquals(175.0, shared.costIn("Cigarettes"), 0.001)
        assertEquals(75.0, shared.costIn("munchies"), 0.001)
    }

    @Test fun `parts must add up, and one part is no split`() {
        assertNull(cleanParts(listOf(Part(350.0, "Cigarettes"), Part(100.0, "Munchies")), 500.0))
        assertEquals(emptyList<Part>(), cleanParts(listOf(Part(500.0, "Cigarettes"), Part(0.0, "Munchies")), 500.0))
        assertEquals(parts, cleanParts(parts, 500.0))
        assertEquals("Cigarettes", order.category)
    }

    @Test fun `a private part hides the whole payment`() {
        assertTrue(listOf(order).withoutPrivate(setOf("munchies")).isEmpty())
    }

    @Test fun `filtering by a part's category finds it`() {
        assertEquals(listOf("b1", "l1"), listOf(order, lunch).search(Filters(category = "Munchies"), day).map { it.id }.sorted())
    }
}
