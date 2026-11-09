package com.himalayasoni.ledger.core.voice

import com.himalayasoni.ledger.core.domain.Usual
import com.himalayasoni.ledger.core.model.DEFAULT_CATEGORIES
import com.himalayasoni.ledger.core.model.EntryType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor

/*
 * Say or type an entry: turns a line like "lent 500 to Rahul yesterday by cash" into the fields
 * of a new entry. A line-by-line port of parseLine() in the web app (index.html), checked
 * against the web's own output by spec/voice-phrases.json, so both apps hear phrases the same way.
 *
 * How it works, in plain words:
 *  1. Clean the line: lowercase, "₹500" / "Rs500" / "rs. 500" / "500rs" / "500/-" all end up as
 *     "rs 500", "1,500" becomes "1500", "2k" becomes "2 k", punctuation goes but letters of any
 *     script stay ("café", "चाय"), and a few Hinglish words are rewritten ("rahul ko" -> "to rahul").
 *  2. Turn number words into digits ("two thousand five hundred" -> "2500", "2 k" -> "2000").
 *     A number over 100 crore is a mishearing and counts as no amount.
 *  3. Drop speech fillers ("um", "uh", a leading "so" / "okay", "like" before a number).
 *  4. Find the day ("yesterday", "on monday", "last friday", "on the 3rd", "3 nov", "on 03/11")
 *     before the amount, because a date has numbers in it too.
 *  5. Take out the first number (the amount), a credit card bill ("paid hdfc card bill"), the
 *     payment mode (UPI / Cash / Card, with the card's name if you've named it: "on hdfc card")
 *     and currency words (rs, rupees, bucks).
 *  6. Decide the type from the verbs (got back, paid back, borrowed, lent, received, else spent)
 *     and pull out the person ("to Rahul", "from Priya", "Rahul gave me ...", "repaid Rahul").
 *     For money in, a shop after "from" stays in the description ("refund from flipkart").
 *  7. For a spend, "with Rahul, Priya and Neha" becomes the people it was shared with.
 *  8. "50 on parking and 20 on toll" adds up to one entry of 70.
 *  9. What's left, minus filler words ("I", "spent", "on", "for", "ki", ...), is the description.
 * 10. A description you've used before is matched to that usual; then the category comes from
 *     that usual, a category name in the description, or a known word ("uber" -> Transport).
 *
 * The web regexes use `\b`, which in JavaScript means a boundary between [A-Za-z0-9_] and anything
 * else. Java (and Android's ICU regex) treat accented letters as word characters too, so every
 * pattern with `\b` goes through [js], which swaps it for an ASCII-only boundary. `\b` is only used
 * around English keywords; where letters of any script matter (names, "um", Hinglish words) the
 * patterns use `\p{L}` (any letter) and `\p{M}` (accents and vowel signs), as the web does.
 */

/**
 * What the parser heard. Fields mirror the web's result object (`type, amount, person, with,
 * desc, cat, mode, day`); empty strings mean "not said".
 *
 * @property amount 0.0 when no amount was heard
 * @property person who a loan or income was with (empty for spends)
 * @property withPeople people a spend was shared with ("dinner with Rahul and Priya")
 * @property mode "UPI", "Cash", "Card", or "" when not said (the form then keeps its default)
 * @property daysAgo days before [VoiceContext.today]: 0 = today, 1 = yesterday, 5 for "on monday"
 *   said on a Saturday. Negative only for a date said with a future year ("on 03/12/2027").
 * @property card which credit card: the bill's card for a [EntryType.CARD_BILL] ("hdfc card bill"),
 *   or a card you've named that paid for a spend ("on hdfc card"). Empty when not said.
 */
data class ParsedLine(
    val type: EntryType,
    val amount: Double,
    val person: String,
    val withPeople: List<String>,
    val description: String,
    val category: String,
    val mode: String,
    val daysAgo: Int,
    val card: String = "",
) {
    /** The entry's date, counting back from [today] (pass the same day as [VoiceContext.today]). */
    fun date(today: LocalDate): LocalDate = today.minusDays(daysAgo.toLong())
}

/**
 * The bits of your ledger the parser looks at. Same as what the web reads from app state.
 *
 * @property people names you've used, most recent first (web: allPeople()). Order matters: the
 *   first match wins, so "Rahul" beats an older "Rahul Verma" for "rahul".
 * @property categories categories used on entries, then private ones, then [DEFAULT_CATEGORIES],
 *   without duplicates. Only single-word names can match a description word.
 * @property usuals descriptions you've used before (see `List<Entry>.usuals()`); a description
 *   that matches one gets that usual's spelling and, if the type matches, its category.
 * @property today the day the line is said on: "on monday" and "on the 3rd" count back from it.
 * @property cards credit card names you've used, most recent first (web: cardNames()). Last, so
 *   existing calls that pass the first four positionally keep working.
 */
data class VoiceContext(
    val people: List<String> = emptyList(),
    val categories: List<String> = DEFAULT_CATEGORIES,
    val usuals: List<Usual> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val cards: List<String> = emptyList(),
)

/** Words that put a spend in a category. Order matters: the first category with a match wins. */
val CATEGORY_WORDS: Map<String, List<String>> = linkedMapOf(
    "Food" to listOf("chai", "tea", "coffee", "lunch", "dinner", "breakfast", "brunch", "snack", "snacks", "samosa", "pizza", "burger", "biryani", "swiggy", "zomato", "maggi", "momos", "juice", "food", "restaurant", "cafe", "dosa", "idli", "thali", "sweets", "ice cream", "icecream", "starbucks", "dominos", "kfc", "mcdonalds", "pani puri", "vada pav", "paratha", "canteen", "mess", "tiffin"),
    "Groceries" to listOf("grocery", "groceries", "dmart", "d mart", "blinkit", "zepto", "bigbasket", "big basket", "instamart", "vegetables", "veggies", "sabzi", "fruits", "milk", "ration", "kirana", "eggs", "bread"),
    "Transport" to listOf("uber", "ola", "rapido", "auto", "rickshaw", "cab", "taxi", "metro", "bus", "petrol", "diesel", "fuel", "parking", "toll", "fastag"),
    "Bills" to listOf("electricity", "bill", "bills", "wifi", "internet", "broadband", "recharge", "gas cylinder", "water bill", "dth", "emi", "insurance", "postpaid", "airtel", "jio", "vodafone", "bsnl"),
    "Rent" to listOf("rent", "pg", "maintenance"),
    "Health" to listOf("medicine", "medicines", "doctor", "pharmacy", "hospital", "gym", "chemist", "dentist", "clinic", "lab test"),
    "Fun" to listOf("movie", "movies", "netflix", "prime", "hotstar", "spotify", "youtube premium", "concert", "game", "games", "party", "bowling", "pvr", "inox", "bookmyshow"),
    "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "clothes", "shoes", "shirt", "tshirt", "t-shirt", "jeans", "shopping", "meesho", "nykaa", "watch", "headphones", "croma", "decathlon", "lenskart"),
    "Travel" to listOf("flight", "hotel", "trip", "train", "irctc", "airbnb", "makemytrip", "goibibo", "oyo", "bus ticket"),
    "Gifts" to listOf("gift", "gifts", "present"),
)

/** Single-word category words: they end a "with ..." list of names. */
private val CATEGORY_WORD_SET: Set<String> = CATEGORY_WORDS.values.flatten().filter { ' ' !in it }.toSet()

/** Shops, apps and brands. After "from" they're where the money came from, not a person ("refund from flipkart"). */
private val SHOPS = setOf(
    "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "swiggy", "zomato", "blinkit", "zepto", "bigbasket", "big basket",
    "instamart", "dmart", "d mart", "uber", "ola", "rapido", "paytm", "phonepe", "gpay", "google pay", "irctc", "makemytrip", "goibibo", "bookmyshow",
    "netflix", "spotify", "hotstar", "airtel", "jio", "vodafone", "bsnl", "starbucks", "dominos", "mcdonalds", "kfc", "croma", "decathlon", "lenskart",
    "ikea", "cred", "zerodha", "groww", "bank", "office", "company", "shop", "store",
)

/** Words that mean a group, not a name: "with friends" stays in the description. */
private val GROUP_WORDS = setOf("friends", "friend", "family", "colleagues", "colleague", "team", "office", "everyone", "gang", "roommates", "flatmates", "guys")

/** Words that can never start a name. */
private val NAME_STOP: Set<String> = ("for on by via using through yesterday today tomorrow rs rupee rupees and in at with cash upi " +
    "card as back me the i we my to from of a an is was it this that some money him her them his loan debt dues").split(" ").toSet()

/** A name: one or two words of letters in any script, apostrophes or hyphens ("rahul", "neha gupta", "o'brien", "josé"). */
private const val NAME = """(\p{L}[\p{L}\p{M}'-]*(?: \p{L}[\p{L}\p{M}'-]*)?)"""

private val SMALL_NUMBERS = mapOf(
    "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
    "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
    "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
    "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
)
private val MULTIPLIERS = mapOf(
    "hundred" to 100.0, "thousand" to 1000.0, "k" to 1000.0, "lakh" to 100000.0, "lakhs" to 100000.0,
    "lac" to 100000.0, "lacs" to 100000.0, "l" to 100000.0, "crore" to 10000000.0,
)

/** 100 crore. A bigger number is a mishearing, not an amount. */
private const val MAX_AMOUNT = 1e9

private val MONTH_NUMBERS = mapOf(
    "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3, "apr" to 4, "april" to 4, "may" to 5,
    "jun" to 6, "june" to 6, "jul" to 7, "july" to 7, "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9,
    "oct" to 10, "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
)
/** Sunday = 0 ... Saturday = 6, like JavaScript's getDay(). */
private val WEEKDAY_NUMBERS = mapOf(
    "sun" to 0, "sunday" to 0, "mon" to 1, "monday" to 1, "tue" to 2, "tues" to 2, "tuesday" to 2, "wed" to 3, "wednesday" to 3,
    "thu" to 4, "thur" to 4, "thurs" to 4, "thursday" to 4, "fri" to 5, "friday" to 5, "sat" to 6, "saturday" to 6,
)
private const val MONTH = "(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)"

// ---------- JavaScript-compatible helpers ----------

/** JavaScript's `\b`: a boundary between an ASCII word character and anything else. */
private const val JS_WORD = "[A-Za-z0-9_]"
private const val JS_BOUNDARY = "(?:(?<=$JS_WORD)(?!$JS_WORD)|(?<!$JS_WORD)(?=$JS_WORD))"

/** Compiles a pattern written in JavaScript style, with `\b` meaning JavaScript's boundary. */
private fun js(pattern: String): Regex = Regex(pattern.replace("\\b", JS_BOUNDARY))

/** JavaScript's Math.round: halves go up (Kotlin's round() sends halves to the even number). */
private fun jsRound(x: Double): Double {
    val down = floor(x)
    return if (x - down >= 0.5) down + 1 else down
}

/** The decimal with the fewest significant digits that converts back to exactly [v]. */
private fun shortestDecimal(v: Double): BigDecimal {
    val full = BigDecimal(v)
    for (p in 1..17) {
        val r = full.round(java.math.MathContext(p, java.math.RoundingMode.HALF_EVEN))
        if (r.toDouble() == v) return r.stripTrailingZeros()
    }
    return full.stripTrailingZeros()
}

/**
 * JavaScript's String(number): "200" not "200.0", "1.5", and "1e+23" for very big numbers.
 * The result goes back into the text, so it has to look exactly like the web's.
 */
internal fun jsNumberToString(v: Double): String {
    if (v == 0.0) return "0"
    if (v < 0) return "-" + jsNumberToString(-v)
    // Shortest digits that still read back as the same number, like JS. Worked out here rather than
    // with Double.toString, whose output changed in Java 19 (and Android follows the older rules).
    val exact = shortestDecimal(v)
    val digits = exact.unscaledValue().toString()
    val n = digits.length - exact.scale() // where the decimal point goes
    return when {
        n in digits.length..21 -> digits + "0".repeat(n - digits.length)
        n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
        n in -5..0 -> "0." + "0".repeat(-n) + digits
        else -> {
            val e = n - 1
            val mantissa = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
            mantissa + "e" + (if (e >= 0) "+" else "-") + kotlin.math.abs(e)
        }
    }
}

/** A capital letter at the start of each word: "neha gupta" -> "Neha Gupta", "o'brien" -> "O'Brien", "josé" -> "José". */
private val WORD_START = Regex("""(^|[^\p{L}\p{M}\p{N}_])(\p{L})""")
private fun titleCase(s: String): String = WORD_START.replace(s) { it.groupValues[1] + it.groupValues[2].uppercase() }

/** Edit distance: how many letters to add, remove or change to turn [a] into [b]. */
private fun levenshtein(a: String, b: String): Int {
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
    }
    return d[a.length][b.length]
}

/**
 * A name you've used before that [heard] refers to: the full name, else the first name, else
 * (for one word of 4+ letters) a first name one letter off, so "kanikaa" finds "Kanika".
 * Empty when nobody matches.
 */
private fun matchPerson(heard: String, people: List<String>): String {
    val l = heard.lowercase().trim()
    if (l.isEmpty()) return ""
    fun first(p: String) = p.lowercase().split(" ")[0]
    return people.firstOrNull { it.lowercase() == l }
        ?: people.firstOrNull { first(it) == l }
        ?: (if (l.length >= 4 && ' ' !in l) people.firstOrNull { levenshtein(first(it), l) <= 1 } else null)
        ?: ""
}

/** Exactly someone you've used before, by full or first name (no spelling slack): "ola" is a cab unless you know an Ola. */
private fun isKnown(word: String, people: List<String>): Boolean =
    people.any { it.lowercase() == word || it.lowercase().split(" ")[0] == word }

private val DECIMAL = Regex("""\d+(\.\d+)?""")

/** Step 2: number words to digits. "two thousand five hundred" -> "2500", "1.5 k" -> "1500". */
private fun numberize(text: String): String {
    val toks = text.trim().split(" ")
    val out = mutableListOf<String>()
    var total = 0.0
    var cur = 0.0
    var any = false // inside a number?
    var lastDigit = false // was the last token written in digits?
    fun flush() {
        if (any) {
            val n = jsRound((total + cur) * 100) / 100
            out += if (n > MAX_AMOUNT) "0" else jsNumberToString(n) // too big counts as no amount
        }
        total = 0.0; cur = 0.0; any = false; lastDigit = false
    }
    for (i in toks.indices) {
        val w = toks[i]
        val next = toks.getOrElse(i + 1) { "" }
        val nextIsDigits = next.firstOrNull()?.let { it in '0'..'9' } == true
        when {
            DECIMAL.matches(w) -> {
                if (any && lastDigit) flush() // "2 500" is two numbers
                cur += w.toDouble(); any = true; lastDigit = true
            }
            w in SMALL_NUMBERS -> { cur += SMALL_NUMBERS.getValue(w); any = true; lastDigit = false }
            w == "a" && (next == "hundred" || next == "thousand" || next == "lakh") -> { cur += 1; any = true }
            w in MULTIPLIERS && any -> {
                if (w == "hundred") cur *= 100 else { total += cur * MULTIPLIERS.getValue(w); cur = 0.0 }
                lastDigit = false
            }
            // "two hundred and fifty" is one number; "100 and 50" stays two
            w == "and" && any && (next in SMALL_NUMBERS || (!lastDigit && nextIsDigits)) -> Unit
            else -> { flush(); out += w }
        }
    }
    flush()
    return " " + out.joinToString(" ") + " "
}

// ---------- dates ----------

/** A date found in the line: how many days before today, and where its words are. */
private class FoundDate(val daysAgo: Int, val at: Int, val length: Int)

private val DAY_BEFORE_YESTERDAY = js("""\bday before yesterday\b""")
private val YESTERDAY = js("""\byesterday\b""")
private val DAYS_AGO = js("""\b(\d{1,3}) days? ago\b""")
private val WEEKDAY_LONG = js("""\b(on |last |this |on last |on this )?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b""")
private val WEEKDAY_SHORT = js("""\b(on |last |this |on last |on this )(sun|mon|tues|tue|wed|thurs|thur|thu|fri|sat)\b""") // short names need "on"/"last"
private val SLASH_WITH_YEAR = js("""\b(?:on )?(\d{1,2})/(\d{1,2})/(\d{4}|\d{2})\b""") // 03/11/2026, day first
private val SLASH = js("""\bon (\d{1,2})/(\d{1,2})\b(?!/)""") // on 03/11
private val DAY_MONTH = js("""\b(?:on )?(?:the )?(\d{1,2})(?:st|nd|rd|th)? (?:of )?$MONTH\b""") // 3rd nov, 3 november
private val MONTH_DAY = js("""\b(?:on )?$MONTH (?:the )?(\d{1,2})(?:st|nd|rd|th)?\b""") // nov 3
private val DAY_ONLY = js("""\b(?:on (?:the )?|the )(\d{1,2})(?:st|nd|rd|th)\b""") // on the 3rd ("2nd hand" alone isn't a date)

private fun monthDays(y: Int, m: Int): Int = YearMonth.of(y, m).lengthOfMonth()

/**
 * Step 4: the first date said in [t], counted back from [today]. Each rule turns a match into
 * "days ago", or null when it isn't a real date (31 feb), and then the next rule gets a turn.
 */
private fun readDate(t: String, today: LocalDate): FoundDate? {
    val ty = today.year
    val tm = today.monthValue
    val td = today.dayOfMonth
    val todayNo = today.toEpochDay()
    fun ago(y: Int, m: Int, d: Int): Int? =
        if (m in 1..12 && d >= 1 && d <= monthDays(y, m)) (todayNo - LocalDate.of(y, m, d).toEpochDay()).toInt() else null
    // a day and month without a year: this year's, or last year's if this year's hasn't come yet
    // (29 feb goes back to the last leap year)
    fun dayMonth(d: Int, m: Int): Int? {
        var y = if (m > tm || (m == tm && d > td)) ty - 1 else ty
        repeat(8) { ago(y, m, d)?.let { return it }; y-- }
        return null
    }
    // a weekday: the latest one up to today ("on monday" said on a Monday is today); "last monday" is never today
    fun weekday(name: String, last: Boolean): Int {
        val back = (today.dayOfWeek.value % 7 - WEEKDAY_NUMBERS.getValue(name) + 7) % 7
        return if (back == 0 && last) 7 else back
    }
    // a day of the month alone ("on the 3rd"): this month's if it has come, else the month before (or the one before that)
    fun dayOnly(d: Int): Int? {
        for (k in 0 until 3) {
            val y = if (tm - k < 1) ty - 1 else ty
            val m = (tm - k + 11) % 12 + 1
            if (d >= 1 && d <= monthDays(y, m) && (k > 0 || d <= td)) return (todayNo - LocalDate.of(y, m, d).toEpochDay()).toInt()
        }
        return null
    }
    fun num(m: MatchResult, group: Int) = m.groupValues[group].toInt()
    val rules: List<Pair<Regex, (MatchResult) -> Int?>> = listOf(
        DAY_BEFORE_YESTERDAY to { _ -> 2 },
        YESTERDAY to { _ -> 1 },
        DAYS_AGO to { m -> num(m, 1) },
        WEEKDAY_LONG to { m -> weekday(m.groupValues[2], "last" in m.groupValues[1]) },
        WEEKDAY_SHORT to { m -> weekday(m.groupValues[2], "last" in m.groupValues[1]) },
        SLASH_WITH_YEAR to { m -> ago(num(m, 3).let { if (it < 100) 2000 + it else it }, num(m, 2), num(m, 1)) },
        SLASH to { m -> dayMonth(num(m, 1), num(m, 2)) },
        DAY_MONTH to { m -> dayMonth(num(m, 1), MONTH_NUMBERS.getValue(m.groupValues[2])) },
        MONTH_DAY to { m -> dayMonth(num(m, 2), MONTH_NUMBERS.getValue(m.groupValues[1])) },
        DAY_ONLY to { m -> dayOnly(num(m, 1)) },
    )
    for ((re, toDays) in rules) {
        val m = re.find(t) ?: continue
        val days = toDays(m) ?: continue
        return FoundDate(days, m.range.first, m.value.length)
    }
    return null
}

// ---------- the patterns, compiled once ----------

private val SPACES = Regex("\\s+")
private val DIGIT_COMMA = Regex("""(\d),(?=\d)""")
private val CURRENCY_BEFORE_DIGITS = js("""\b(rs|inr)(?=\d)""") // Rs500 -> rs 500
private val DIGIT_SUFFIX = js("""(\d)(rs|inr|rupees?|k|l)\b""") // 500rs -> 500 rs, 2k -> 2 k
private val RS = js("""\brs\b\.?""")
private val DOT_NOT_DECIMAL = Regex("""\.(?!\d)""")
private val NOT_ALLOWED = Regex("""[^\p{L}\p{M}0-9.&,'/ -]""") // letters of any script stay
private val SLASH_DASH = Regex("""(\d) ?/-""") // 500/- -> 500

private val HINGLISH_KO = Regex(""" (\p{L}[\p{L}\p{M}'-]*) ko(?= )""")
private val HINGLISH_SE = Regex(""" (\p{L}[\p{L}\p{M}'-]*) se(?= )""")
private val HINGLISH_NE = Regex(""" (\p{L}[\p{L}\p{M}'-]*) ne(?= )""")
private val MAINE = js("""\b(?:maine|mene)\b""")
private val MUJHE = js("""\bmujhe\b""")
private val KE_LIYE = js("""\bke liye\b""")

private val FILLER_ANYWHERE = Regex(""" (?:u+m+|u+h+|h+m+|basically|actually)(?= )""")
private val FILLER_AT_START = Regex("""^(?: (?:so|ok|okay|like))+(?= )""")
private val LIKE_BEFORE_NUMBER = Regex(""" like(?= (?:\d|i |we ))""")

private val TODAY = js("""\btoday\b""")
private val SLASH_LEFT = Regex("/")
private val GOT_MONEY = js("""\b(?:got|get|getting) (?:paid\b|\d)""") // "got 15000 for the gig": money came in
private val AMOUNT = Regex(""" (\d+(?:\.\d+)?) """)
private val MODES = listOf(
    js("""\b(?:by |via |through |using |with |in |on )?(?:upi|gpay|g pay|google pay|phonepe|phone pe|paytm|bhim)\b""") to "UPI",
    js("""\b(?:by |via |using |with |in )?cash\b""") to "Cash",
    js("""\b(?:by |via |using |with |through |on )?(?:credit |debit )?card\b""") to "Card",
)
private val CURRENCY = js("""\b(?:rs|rupees?|rupay|inr|bucks)\b""")

private val GOT_BACK = js("""\b(?:got|get|received|recieved|took|taken|getting) (?:my money )?back\b|\bwapas (?:mile|aaye|liye|liya|mil gaye)\b""")
private const val THEY_RETURNED = "returned|repaid|paid me back|paid back|gave me back|gave back|has returned|has paid|wapas diye|wapas diya|wapas kiye|wapas kar diye|lauta diye|lauta diya"
private val PAID_BACK = js("""\b(?:paid|gave|given|returned|repaid|pay) back\b|\b(?:returned|repaid)\b|\b(?:wapas|lauta) (?:diye|diya|kiye|kar diye|de diye)\b""")
private const val PAID_BACK_VERBS = "repaid|returned|paid back|gave back|given back|pay back"
private const val THEY_LENT_ME = "gave me|lent me|loaned me|lend me|me diye|me diya|me de diye"
private val BORROWED = js("""\b(?:borrowed|borrow|borrowing|udhaar liya|udhar liya|udhaar li|udhar li)\b""")
private val TOOK = js("""\b(?:took|taken|liye|liya|le liye)\b""")
private val FROM = js("""\bfrom\b""")
private val LENT = js("""\b(?:lent|lend|lended|lented|lending|loaned|udhaar diya|udhar diya|udhaar di|udhar di)\b""")
private const val LENT_VERBS = "lent|lend|lended|lented|loaned"
private val DIYE = js("""\b(?:diye|diya|de diye)\b""")
private val TO = js("""\bto\b""")
private val GAVE = js("""\bgave\b""")
private val RECEIVED = js("""\b(?:received|recieved|credited|salary|income|earned|refund|cashback|stipend)\b""")
private val GOT = js("""\bgot\b""")

private val AND_AMOUNT = Regex(""" and (\d+(?:\.\d+)?)(?= )""")

/** Words that are never part of a description. */
private val FILLER: Set<String> = ("i we me my have has had just did spent spend spending paid pay paying bought buy purchased purchase lent lend lended lented lending loaned loan " +
    "borrowed borrow borrowing took taken got get received recieved returned repaid gave given give back credited earned earn for on of the a an to from worth amount " +
    "udhaar udhar diya diye liya liye kiye di li wapas lauta mile de le kar ki ka ke ko wala wali via by using through in at some money").split(" ").toSet()
// "credit card bill", "sbi card bill", "card ka bill": the optional word before "card" may be the card's name ("credit" never is).
private val CARD_BILL = js("""\b(?:(?!credit\b)(\p{L}[\p{L}\p{M}'-]*) )?(?:credit )?card (?:ka )?bill\b""")
/** Words before "card bill" that aren't a card's name ("paid card bill", "my credit card bill", "full card bill"). */
private val NOT_CARD_NAME: Set<String> = "debit card bill this last next month monthly pending full total due minimum whole entire outstanding".split(" ").toSet()
/** A card name the parser can look for: what's left of it after step 1 cleans the line. */
private val CARD_NAME_CHARS = Regex("""^[\p{L}\p{M}0-9&' -]+$""")
private val COMMA_AMP = Regex("[,&]")
private val HAS_LETTER_OR_DIGIT = Regex("""[\p{L}0-9]""") // a lone "-" or "'" isn't a word
private val SALARY_WORD = js("""\b(?:salary|stipend)\b""")

/** A name heard in the line, and where it starts. */
private class Heard(val name: String, val at: Int) {
    val firstWord: String get() = name.split(" ")[0]
}

/** Parses one said or typed line. See the top of this file for the steps. */
fun parseLine(raw: String, context: VoiceContext = VoiceContext()): ParsedLine {
    val people = context.people
    val cats = context.categories
    // Cards you've named, as they appear in the cleaned line, longest first (so "hdfc millennia" beats "hdfc").
    val cards = context.cards.filter { CARD_NAME_CHARS.matches(it) }
    fun cardKey(c: String) = c.lowercase().trim().replace(SPACES, " ")
    val cardAlt = cards.map(::cardKey).sortedByDescending { it.length }.joinToString("|")
    fun knownCard(w: String) = cards.firstOrNull { cardKey(it) == w } ?: ""

    // 1. Clean the line.
    var t = raw.lowercase()
        .replace("₹", " rs ")
        .replace(DIGIT_COMMA, "$1") // 1,50,000 -> 150000
        .replace(CURRENCY_BEFORE_DIGITS, "$1 ")
        .replace(DIGIT_SUFFIX, "$1 $2")
        .replace(RS, " rs ")
        .replace(",", " , ")
        .replace(DOT_NOT_DECIMAL, " ")
        .replace(NOT_ALLOWED, " ")
        .replace(SPACES, " ")
        .replace(SLASH_DASH, "$1 ")
        .trim()
    t = " $t "
    // Hinglish: "rahul ko" = to Rahul, "rahul se" = from Rahul, "rahul ne" = Rahul (did)
    t = t.replace(HINGLISH_KO, " to $1").replace(HINGLISH_SE, " from $1").replace(HINGLISH_NE, " $1")
        .replace(MAINE, "i").replace(MUJHE, "me").replace(KE_LIYE, "for")
    t = " " + t.replace(SPACES, " ").trim() + " "

    // 2. Number words to digits.
    t = numberize(t)

    // 3. Speech fillers: "um", "uh", "hmm", "basically", "actually" anywhere; "so", "ok", "okay", "like" at the
    //    start; "like" before a number or "I"/"we". Whole words only: "likes", "Uber", "kiwi" stay.
    t = t.replace(FILLER_ANYWHERE, "").replace(FILLER_AT_START, "").replace(LIKE_BEFORE_NUMBER, "")

    var type = EntryType.SPENT
    var amount = 0.0
    var person = ""
    var withPeople = emptyList<String>()
    var category = ""
    var mode = ""
    var daysAgo = 0
    var card = ""

    // Removes a recognised piece. Like the web, a pattern without /g only removes its first match.
    fun cut(re: Regex, all: Boolean = false) {
        val removed = if (all) re.replace(t, " ") else re.replaceFirst(t, " ")
        t = " $removed ".replace(SPACES, " ")
    }

    // 4. The day, before the amount: "on 3 nov" has a number that isn't the amount.
    readDate(t, context.today)?.let { d ->
        daysAgo = d.daysAgo
        t = t.substring(0, d.at) + " " + t.substring(d.at + d.length)
    }
    cut(TODAY)
    cut(SLASH_LEFT, all = true) // slashes that weren't part of a date

    // 5. Amount (the first number), payment mode, currency words.
    val gotMoney = GOT_MONEY.containsMatchIn(t)
    AMOUNT.find(t)?.let { m ->
        amount = m.groupValues[1].toDouble()
        t = t.replaceFirst(m.value, " ") // plain text, not a pattern (JS string replace)
    }

    //    A credit card bill ("paid hdfc card bill", "credit card bill 12000", "paid sbi bill" for a card you've named) isn't
    //    spending. Found before the payment mode, which would otherwise take "card" as how it was paid.
    val namedBill = if (cardAlt.isEmpty()) null else js("\\b(?:my )?($cardAlt) (?:credit )?(?:card )?(?:ka )?bill\\b")
    val bill = namedBill?.find(t) ?: CARD_BILL.find(t)
    if (bill != null) {
        type = EntryType.CARD_BILL
        val w = bill.groups[1]?.value ?: ""
        val isName = w.isNotEmpty() && w !in FILLER && w !in NAME_STOP && w !in NOT_CARD_NAME
        if (isName) card = knownCard(w).ifEmpty { titleCase(w) }
        val from = if (isName || w.isEmpty()) bill.range.first else bill.range.first + w.length + 1 // a word that isn't the card's name stays
        t = t.substring(0, from) + " " + t.substring(bill.range.last + 1)
    }
    // A card you've named, right before "card", says which card paid: "shoes 2499 on hdfc card".
    val namedCard = if (cardAlt.isEmpty()) null else js("\\b(?:by |via |using |with |through |on )?(?:my )?($cardAlt) (?:credit |debit )?card\\b")
    for ((re, m) in MODES) {
        if (!re.containsMatchIn(t)) continue
        if (m == "Card" && type == EntryType.CARD_BILL) break // a card bill is paid to the card, never with it
        val named = if (m == "Card") namedCard?.find(t) else null
        if (named != null) { card = knownCard(named.groupValues[1]); cut(namedCard!!) } else cut(re)
        mode = m
        break
    }
    cut(CURRENCY, all = true)

    // 6. Type and person. A name is heard after a word ("to rahul") or at the start ("rahul returned").
    fun after(words: String): Heard? = js("\\b(?:$words) $NAME").find(t)?.let { m ->
        val name = m.groupValues[1]
        Heard(name, m.range.first + m.value.length - name.length)
    }
    fun subject(verbs: String): Heard? {
        val name = js("^ $NAME (?:$verbs)\\b").find(t)?.groupValues?.get(1) ?: return null
        return if (name.split(" ")[0] in NAME_STOP) null else Heard(name, 1)
    }
    // a shop, a category word or a category name ("from flipkart", "from freelance") rather than someone
    fun isPlace(h: Heard): Boolean = matchPerson(h.firstWord, people).isEmpty() &&
        listOf(h.firstWord, h.name).any { n -> n in SHOPS || n in CATEGORY_WORD_SET || cats.any { it.lowercase() == n } }
    fun canBePerson(h: Heard?): Boolean = h != null && h.firstWord !in NAME_STOP && !isPlace(h)

    var who: Heard? = null
    val returnedBy = subject(THEY_RETURNED) // "Rahul returned 300"
    val lentMeBy = subject(THEY_LENT_ME) // "Rahul gave me 2000"
    // The order matters: the first rule that fits decides the type.
    if (type == EntryType.CARD_BILL) { /* already decided in step 5 */ }
    else if (GOT_BACK.containsMatchIn(t)) { type = EntryType.GOT_BACK; who = after("from") }
    else if (returnedBy != null) { type = EntryType.GOT_BACK; who = returnedBy }
    else if (PAID_BACK.containsMatchIn(t)) { type = EntryType.PAID_BACK; who = after("to") ?: after(PAID_BACK_VERBS) } // "repaid rahul 5000"
    else if (lentMeBy != null) { type = EntryType.BORROWED; who = lentMeBy }
    else if (BORROWED.containsMatchIn(t) || (TOOK.containsMatchIn(t) && FROM.containsMatchIn(t))) { type = EntryType.BORROWED; who = after("from") }
    else if (LENT.containsMatchIn(t) || (DIYE.containsMatchIn(t) && TO.containsMatchIn(t))) { type = EntryType.LENT; who = after("to") ?: after(LENT_VERBS) }
    else if (GAVE.containsMatchIn(t) && (canBePerson(after("to")) || matchPerson(after("gave")?.firstWord ?: "", people).isNotEmpty())) {
        type = EntryType.LENT // "gave 2000 to the plumber" is a spend
        who = after("to") ?: after("gave")
    }
    else if (RECEIVED.containsMatchIn(t) || gotMoney || (GOT.containsMatchIn(t) && canBePerson(after("from")))) {
        type = EntryType.RECEIVED // "got shoes from amazon" is a spend
        who = after("from")
    }

    // A new name is still a person for a loan; for money in, a shop stays in the description ("refund from flipkart").
    val heard = who
    if (heard != null && heard.firstWord !in NAME_STOP && !(type.hasCategory && isPlace(heard))) {
        // Keep two words only if they're someone you know ("neha gupta"); else just the first.
        val used = if (' ' in heard.name && matchPerson(heard.name, people).isNotEmpty()) heard.name else heard.firstWord
        person = matchPerson(used, people).ifEmpty { titleCase(used) }
        t = t.substring(0, heard.at) + " " + t.substring(heard.at + used.length)
    }

    // 7. A spend's "with Rahul, Priya and Neha" (or "with rahul with priya"): the names up to the first word that isn't one.
    if (type == EntryType.SPENT) {
        val toks = t.trim().split(" ").toMutableList()
        val names = mutableListOf<String>()
        fun nextWith(from: Int) = (from until toks.size).firstOrNull { toks[it] == "with" } ?: -1
        var i = nextWith(0)
        while (i >= 0) {
            val found = mutableListOf<String>()
            var j = i + 1
            while (j < toks.size) {
                val w = toks[j]
                val two = w + " " + toks.getOrElse(j + 1) { "" }
                if (w == "," || w == "and" || w == "&" || w == "with") { j++; continue }
                if (toks.getOrElse(j + 1) { "" }.isNotEmpty() && isKnown(two, people)) { found += two; j += 2; continue } // "neha gupta" is one person
                if (w in NAME_STOP || w.any { it in '0'..'9' } ||
                    (!isKnown(w, people) && (w in CATEGORY_WORD_SET || w in GROUP_WORDS || w in SHOPS))) break
                found += w
                j++
            }
            if (found.isNotEmpty()) { names += found; toks.subList(i, j).clear() } else i++
            i = nextWith(i)
        }
        if (names.isNotEmpty()) {
            withPeople = names.map { n -> matchPerson(n, people).ifEmpty { titleCase(n) } }.distinct()
            t = " " + toks.joinToString(" ") + " "
        }
    }

    // 8. "spent 50 on parking and 20 on toll" is one entry for the total (70), described as "Parking and toll".
    if (type.hasCategory && person.isEmpty()) {
        t = AND_AMOUNT.replace(t) { m -> amount = jsRound((amount + m.groupValues[1].toDouble()) * 100) / 100; " and" }
    }
    if (amount > MAX_AMOUNT) amount = 0.0

    // 9. What's left, minus filler words, is the description.
    val words = t.replace(COMMA_AMP, " ").split(" ").filter { it.isNotEmpty() && it !in FILLER && HAS_LETTER_OR_DIGIT.containsMatchIn(it) }.toMutableList()
    while (words.firstOrNull() == "and") words.removeAt(0)
    while (words.lastOrNull() == "and") words.removeAt(words.size - 1)
    var desc = words.joinToString(" ")
    if (type == EntryType.RECEIVED && desc.isEmpty() && "salary" in raw.lowercase()) desc = "salary"
    val usual = if (desc.isNotEmpty()) context.usuals.firstOrNull { it.key == desc.lowercase() } else null
    val description = usual?.description ?: (if (desc.isNotEmpty()) desc.substring(0, 1).uppercase() + desc.substring(1) else "")

    // 10. Category: from a usual with the same description, a category name in it, or a known word ("uber" -> Transport).
    if (type.hasCategory && description.isNotEmpty()) {
        if (usual != null && usual.category.isNotEmpty() && usual.type == type) category = usual.category
        val dl = " " + description.lowercase() + " "
        val descWords = dl.trim().split(" ")
        if (category.isEmpty()) category = cats.firstOrNull { it.lowercase() in descWords } ?: ""
        if (category.isEmpty() && type == EntryType.SPENT) {
            category = CATEGORY_WORDS.entries.firstOrNull { (_, list) -> list.any { " $it " in dl } }?.key ?: ""
        }
        if (category.isEmpty() && type == EntryType.RECEIVED && SALARY_WORD.containsMatchIn(dl)) category = "Salary"
    }

    return ParsedLine(type, amount, person, withPeople, description, category, mode, daysAgo, card)
}
