package com.himalayasoni.ledger.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Screens as on-phone text recognition reads them: one string per line, top to bottom, with the line's
 * height in pixels (the amount is always the tallest). Written from each app's success screen layout.
 */
class PaymentScreenTest {
    private val today = LocalDate.of(2026, 11, 26)
    private fun read(vararg l: Pair<String, Int>) = PaymentScreen.read(l.map { OcrLine(it.first, it.second) }, today)!!

    @Test fun googlePayPaid() {
        val p = read(
            "10:42" to 30, "To RAHUL SHARMA" to 44, "Banking name: RAHUL SHARMA" to 30, "₹250" to 120,
            "Completed" to 36, "26 Nov 2026, 10:41 am" to 30, "Split with friends" to 30,
            "UPI transaction ID" to 28, "433012345678" to 30, "To: RAHUL SHARMA" to 28, "rahul.s@okhdfcbank" to 28,
            "From: HDFC Bank 1234" to 28, "Google transaction ID" to 28, "CICAgKCjvL3hNQ" to 28, "G Pay" to 40,
        )
        assertEquals(250.0, p.amount!!, 0.0)
        assertFalse(p.received)
        assertEquals("Rahul Sharma", p.party)
        assertEquals("rahul.s@okhdfcbank", p.upiId)
        assertEquals(LocalDate.of(2026, 11, 26), p.date)
        assertEquals("433012345678", p.ref)
        assertEquals("Google Pay", p.app)
        assertFalse(p.failed)
    }

    @Test fun phonePePaidToShop() {
        val p = read(
            "Transaction Successful" to 44, "10:15 am on 24 Nov 2026" to 30, "Paid to" to 30, "Sharma Tea Stall" to 40,
            "Q123456789@ybl" to 28, "₹ 40" to 60, "Transfer Details" to 34, "Transaction ID" to 28, "T2411241015123456789" to 28,
            "Debited from" to 28, "XXXXXX1234" to 28, "₹40" to 30, "UTR: 432998765432" to 28, "Powered by UPI" to 24, "PhonePe" to 30,
        )
        assertEquals(40.0, p.amount!!, 0.0)
        assertEquals("Sharma Tea Stall", p.party)
        assertEquals(LocalDate.of(2026, 11, 24), p.date)
        assertEquals("432998765432", p.ref)
        assertEquals("PhonePe", p.app)
    }

    @Test fun paytmPaidWithCashbackNearby() {
        val p = read(
            "Paid Successfully to" to 34, "SWIGGY" to 44, "₹1,249" to 110, "You won ₹15 Cashback" to 34,
            "24 Nov, 08:12 PM" to 28, "UPI Ref No: 432811112222" to 28, "From: State Bank Of India - 5678" to 28,
            "paytm" to 40,
        )
        assertEquals(1249.0, p.amount!!, 0.0)
        assertEquals("Swiggy", p.party)
        assertEquals(LocalDate.of(2026, 11, 24), p.date)
        assertEquals("432811112222", p.ref)
        assertEquals("Paytm", p.app)
    }

    @Test fun googlePayReceived() {
        val p = read(
            "From Amit Verma" to 44, "₹1,500" to 120, "Received" to 36, "Received from Amit Verma" to 30,
            "23 Nov 2026, 9:03 pm" to 30, "UPI transaction ID" to 28, "432700001111" to 28,
            "To: HDFC Bank 1234" to 28, "Google Pay" to 30,
        )
        assertEquals(1500.0, p.amount!!, 0.0)
        assertTrue(p.received)
        assertEquals("Amit Verma", p.party)
        assertEquals(LocalDate.of(2026, 11, 23), p.date)
    }

    @Test fun failedAndCreditCard() {
        val p = read(
            "Payment Failed" to 44, "₹ 3,000.50" to 100, "Paid to" to 30, "Croma Retail" to 40,
            "Paid using HDFC RuPay Credit Card" to 28, "Yesterday, 6:20 pm" to 28,
        )
        assertEquals(3000.5, p.amount!!, 0.0)
        assertTrue(p.failed)
        assertTrue(p.viaCreditCard)
        assertEquals("Croma Retail", p.party)
        assertEquals(LocalDate.of(2026, 11, 25), p.date)
    }

    @Test fun noHeightsStillFindsMarkedAmount() {
        val p = PaymentScreen.read(listOf("Sent to Priya", "Rs. 600", "Ref no 431122223333", "12/11/2026").map { OcrLine(it) }, today)!!
        assertEquals(600.0, p.amount!!, 0.0)
        assertEquals("Priya", p.party)
        assertEquals("431122223333", p.ref)
        assertEquals(LocalDate.of(2026, 11, 12), p.date)
    }

    @Test fun lastDecemberWithoutAYear() {
        val p = PaymentScreen.read(listOf(OcrLine("₹99", 80), OcrLine("28 Dec, 1:00 pm")), LocalDate.of(2027, 1, 3))!!
        assertEquals(LocalDate.of(2026, 12, 28), p.date)
    }

    @Test fun nothingToRead() {
        assertNull(PaymentScreen.read(listOf(OcrLine("Hello"), OcrLine("A photo of a cat")), today))
    }
}
