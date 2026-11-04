package com.himalayasoni.ledger.core

import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.core.money.formatShort
import com.himalayasoni.ledger.core.money.groupIndian
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun `groups the Indian way`() {
        assertEquals("999", groupIndian(999))
        assertEquals("1,000", groupIndian(1000))
        assertEquals("1,00,000", groupIndian(100000))
        assertEquals("12,34,567", groupIndian(1234567))
    }

    @Test fun `shows paise only when there are any`() {
        assertEquals("₹1,200", formatRupees(1200.0))
        assertEquals("₹49.5", formatRupees(49.5))
        assertEquals("₹49.05", formatRupees(49.05))
        assertEquals("₹0", formatRupees(0.0))
    }

    @Test fun `short form for charts`() {
        assertEquals("₹950", formatShort(950.0))
        assertEquals("₹4.2k", formatShort(4200.0))
        assertEquals("₹1.5L", formatShort(150000.0))
        assertEquals("₹2Cr", formatShort(2e7))
    }
}
