package com.himalayasoni.ledger.core.scan

import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** One line of text read off a screenshot. [height] is its height in pixels (bigger text, taller line), 0 if unknown. */
data class OcrLine(val text: String, val height: Int = 0)

/** What a payment app's "done" screen says. Anything it couldn't find is null. */
data class ScannedPayment(
    val amount: Double?,
    val received: Boolean,        // money came in ("Received from"), not went out
    val party: String?,           // who was paid / who paid you, e.g. "Rahul Sharma"
    val upiId: String?,           // e.g. rahul@okhdfcbank
    val date: LocalDate?,
    val ref: String?,             // the 12-digit UPI reference (UTR / RRN): the same on every app and your bank statement
    val app: String?,             // "Paytm", "Google Pay"…
    val failed: Boolean = false,  // "Payment failed", "Declined"
    val pending: Boolean = false, // "Pending", "Processing"
    val viaCreditCard: Boolean = false,
)

/**
 * Reads a payment screenshot's text (from on-phone text recognition) into a [ScannedPayment].
 *
 * It's plain rules, no AI and no network, so it's fast, private and testable. The rules, in order:
 * - **Amount**: numbers marked ₹ / Rs / INR, or a line that's only a number; the tallest one wins, since
 *   every app shows the amount biggest. Lines about cashback, offers or balances are ignored.
 * - **Who**: the text after "Paid to", "Sent to", "To", "Received from" (on the same line or the next).
 *   For money out, a "From" line is your own bank, so it's skipped.
 * - **Date**, **UPI reference** (12 digits after "UPI Ref", "UTR"…), **app** and **status**: by keywords.
 * Test cases: PaymentScreenTest, one per app layout.
 */
object PaymentScreen {
    private val currency = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val bareNumber = Regex("""^[₹]?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]{1,6}(?:\.[0-9]{1,2})?)$""")
    private val ignoreForAmount = Regex("""cashback|reward|offer|won|scratch|balance|limit|coins?|points|save up|get up to|fee|charges""", RegexOption.IGNORE_CASE)
    private val upi = Regex("""\b[a-zA-Z0-9.\-_]{2,}@[a-zA-Z][a-zA-Z0-9]{1,}\b""")
    private val twelveDigits = Regex("""(?<![0-9])([0-9]{12})(?![0-9])""")
    private val refLabel = Regex("""upi\s*ref|utr|rrn|upi\s*transaction\s*id|upi\s*txn\s*id|reference\s*(?:no|number|id)|ref\.?\s*no""", RegexOption.IGNORE_CASE)
    private val receivedWords = Regex("""received\s+from|money\s+received|received\s+successfully|you\s+received|credited\s+to\s+your|has\s+sent\s+you|paid\s+you""", RegexOption.IGNORE_CASE)
    private val failedWords = Regex("""\bfailed\b|declined|unsuccessful|could\s*n['o]?t\s+be\s+completed""", RegexOption.IGNORE_CASE)
    private val pendingWords = Regex("""\bpending\b|processing|in\s+progress|awaiting""", RegexOption.IGNORE_CASE)
    private val cardWords = Regex("""credit\s*card|rupay\s+credit|\bcc\b""", RegexOption.IGNORE_CASE)

    // "Paid to Rahul", "Paid successfully to Rahul", "Sent to", "Payment to", "To: Rahul", "Received from Rahul"
    private val outLabel = Regex("""^(?:paid\s+(?:successfully\s+)?to|payment\s+(?:successful\s+)?to|sent\s+(?:successfully\s+)?to|transferred\s+to|money\s+sent\s+to|to)\b\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val inLabel = Regex("""^(?:received\s+(?:successfully\s+)?from|money\s+received\s+from|from)\b\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val notAName = Regex("""^(?:[0-9 :,./\-+xX*]+|.*@.*|.*\b(?:bank|a/c|account|upi\s*id|upi|wallet|transaction|txn|ref|utr|completed|successful(?:ly)?|done|paid|received|share|view|details|split|today|yesterday|am|pm)\b.*)$""", RegexOption.IGNORE_CASE)

    private val apps = listOf(
        "Google Pay" to Regex("""google\s*pay|\bg\s?pay\b""", RegexOption.IGNORE_CASE),
        "PhonePe" to Regex("""phone\s?pe""", RegexOption.IGNORE_CASE),
        "Paytm" to Regex("""paytm""", RegexOption.IGNORE_CASE),
        "BHIM" to Regex("""\bbhim\b""", RegexOption.IGNORE_CASE),
        "Amazon Pay" to Regex("""amazon\s*pay""", RegexOption.IGNORE_CASE),
        "CRED" to Regex("""\bcred\b""", RegexOption.IGNORE_CASE),
        "Navi" to Regex("""\bnavi\b""", RegexOption.IGNORE_CASE),
        "MobiKwik" to Regex("""mobikwik""", RegexOption.IGNORE_CASE),
        "super.money" to Regex("""super\.money""", RegexOption.IGNORE_CASE),
        "WhatsApp" to Regex("""whatsapp""", RegexOption.IGNORE_CASE),
    )

    fun read(lines: List<OcrLine>, today: LocalDate): ScannedPayment? {
        val ls = lines.map { it.copy(text = it.text.replace(' ', ' ').trim()) }.filter { it.text.isNotEmpty() }
        val all = ls.joinToString("\n") { it.text }
        val amount = amount(ls) ?: return null
        val received = receivedWords.containsMatchIn(all)
        return ScannedPayment(
            amount = amount,
            received = received,
            party = party(ls, received),
            upiId = upi.findAll(all).map { it.value }.firstOrNull { !it.contains(".com", true) },
            date = date(ls, today),
            ref = ref(ls),
            app = apps.firstOrNull { it.second.containsMatchIn(all) }?.first,
            failed = failedWords.containsMatchIn(all),
            pending = pendingWords.containsMatchIn(all),
            viaCreditCard = cardWords.containsMatchIn(all),
        )
    }

    private fun amount(ls: List<OcrLine>): Double? {
        data class Cand(val value: Double, val height: Int, val marked: Boolean, val index: Int)
        val cands = ls.flatMapIndexed { i, l ->
            if (ignoreForAmount.containsMatchIn(l.text)) return@flatMapIndexed emptyList()
            val marked = currency.findAll(l.text).map { Cand(num(it.groupValues[1]), l.height, true, i) }.toList()
            marked.ifEmpty {
                bareNumber.find(l.text)?.let { listOf(Cand(num(it.groupValues[1]), l.height, false, i)) } ?: emptyList()
            }
        }.filter { it.value > 0 && it.value < 10_000_000 }
        if (cands.isEmpty()) return null
        val anyHeights = cands.any { it.height > 0 }
        return cands.sortedWith(
            if (anyHeights) compareByDescending<Cand> { it.height }.thenByDescending { it.marked }.thenBy { it.index }
            else compareByDescending<Cand> { it.marked }.thenBy { it.index },
        ).first().value
    }

    private fun num(s: String) = s.replace(",", "").toDoubleOrNull() ?: 0.0

    private fun party(ls: List<OcrLine>, received: Boolean): String? {
        val label = if (received) inLabel else outLabel
        for ((i, l) in ls.withIndex()) {
            val m = label.find(l.text) ?: continue
            val rest = m.groupValues[1].trim()
            val pick = (listOf(rest) + ls.drop(i + 1).take(3).map { it.text }).firstOrNull { it.isNotBlank() && looksLikeName(it) }
            if (pick != null) return tidy(pick)
        }
        return null
    }

    private fun looksLikeName(s: String): Boolean {
        val t = s.trim()
        if (t.length < 2 || t.length > 40 || notAName.matches(t)) return false
        if (currency.containsMatchIn(t)) return false
        return t.count { it.isLetter() } >= 2
    }

    /** "RAHUL SHARMA ✓" → "Rahul Sharma"; names already in mixed case stay as they are. */
    private fun tidy(s: String): String {
        val t = s.replace(Regex("""[✓✔•>›]|\bverified\b""", RegexOption.IGNORE_CASE), "").trim().trimEnd(',', '.', ':')
        return if (t.any { it.isLowerCase() }) t
        else t.lowercase().split(' ').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
    }

    private fun ref(ls: List<OcrLine>): String? {
        for ((i, l) in ls.withIndex()) {
            if (!refLabel.containsMatchIn(l.text)) continue
            (listOf(l) + ls.drop(i + 1).take(2)).forEach { c -> twelveDigits.find(c.text.replace(" ", ""))?.let { return it.groupValues[1] } }
        }
        return ls.firstNotNullOfOrNull { l -> twelveDigits.find(l.text)?.groupValues?.get(1) }
    }

    private val months = Month.entries.associateBy { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() } +
        Month.entries.associateBy { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase() } + mapOf("sept" to Month.SEPTEMBER)
    private const val MON = """(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"""
    private val dayMonth = Regex("""\b([0-3]?[0-9])(?:st|nd|rd|th)?\s+$MON[a-z]*,?(?:\s*([0-9]{4}|'[0-9]{2})(?![0-9:]))?""", RegexOption.IGNORE_CASE)
    private val monthDay = Regex("""\b$MON[a-z]*\s+([0-3]?[0-9]),?\s*([0-9]{4})?\b""", RegexOption.IGNORE_CASE)
    private val numeric = Regex("""\b([0-3]?[0-9])[/\-.]([01]?[0-9])[/\-.]([0-9]{2,4})\b""")

    private fun date(ls: List<OcrLine>, today: LocalDate): LocalDate? {
        for (l in ls) {
            val t = l.text
            if (Regex("""\btoday\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) return today
            if (Regex("""\byesterday\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) return today.minusDays(1)
            dayMonth.find(t)?.let { m -> make(m.groupValues[1].toInt(), month(m.groupValues[2]), m.groupValues[3], today)?.let { return it } }
            monthDay.find(t)?.let { m -> make(m.groupValues[2].toInt(), month(m.groupValues[1]), m.groupValues[3], today)?.let { return it } }
            numeric.find(t)?.let { m -> make(m.groupValues[1].toInt(), Month.entries.getOrNull(m.groupValues[2].toInt() - 1), m.groupValues[3], today)?.let { return it } }
        }
        return null
    }

    private fun month(s: String): Month? = months[s.lowercase()] ?: months[s.lowercase().take(3)]

    /** No year shown: this year, unless that's in the future (a screenshot from last December, read in January). */
    private fun make(day: Int, month: Month?, year: String, today: LocalDate): LocalDate? {
        month ?: return null
        val y = year.trimStart('\'').toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        return runCatching {
            if (y != null) LocalDate.of(y, month, day)
            else LocalDate.of(today.year, month, day).let { if (it.isAfter(today)) it.minusYears(1) else it }
        }.getOrNull()?.takeIf { !it.isAfter(today.plusDays(1)) && it.year > 2015 }
    }
}
