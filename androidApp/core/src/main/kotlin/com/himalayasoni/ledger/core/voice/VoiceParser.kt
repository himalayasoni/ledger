package com.himalayasoni.ledger.core.voice

import com.himalayasoni.ledger.core.domain.Usual
import com.himalayasoni.ledger.core.model.DEFAULT_CATEGORIES
import com.himalayasoni.ledger.core.model.EntryType
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.floor

/*
 * Say or type an entry: turns a line like "lent 500 to Rahul yesterday by cash" into the fields
 * of a new entry. A line-by-line port of parseLine() in the web app (index.html), checked
 * against the web's own output by spec/voice-phrases.json, so both apps hear phrases the same way.
 *
 * How it works, in plain words:
 *  1. Clean the line: lowercase, "₹" and "rs." become " rs ", "1,500" becomes "1500", "2k" becomes
 *     "2 k", punctuation goes, and a few Hinglish words are rewritten ("rahul ko" -> "to rahul").
 *  2. Turn number words into digits ("two thousand five hundred" -> "2500", "2 k" -> "2000").
 *  3. Take out, one by one, the pieces we recognise: the first number (the amount), the day word
 *     (today / yesterday / day before yesterday), the payment mode (UPI / Cash / Card) and
 *     currency words (rs, rupees, bucks).
 *  4. Decide the type from the verbs (got back, paid back, borrowed, lent, received, else spent)
 *     and pull out the person ("to Rahul", "from Priya", "Rahul gave me ...").
 *  5. For a spend, "with Rahul and Priya" becomes the people it was shared with.
 *  6. What's left, minus filler words ("I", "spent", "on", "for", ...), is the description.
 *     A description you've used before is matched to that usual; then the category comes from
 *     that usual, a category name in the description, or a known word ("uber" -> Transport).
 *
 * The web regexes use `\b`, which in JavaScript means a boundary between [A-Za-z0-9_] and anything
 * else. Java (and Android's ICU regex) treat accented letters as word characters too, so every
 * pattern here goes through [js], which swaps `\b` for an ASCII-only boundary.
 */

/**
 * What the parser heard. Fields mirror the web's result object (`type, amount, person, with,
 * desc, cat, mode, day`); empty strings mean "not said".
 *
 * @property amount 0.0 when no amount was heard
 * @property person who a loan or income was with (empty for spends)
 * @property withPeople people a spend was shared with ("dinner with Rahul and Priya")
 * @property mode "UPI", "Cash", "Card", or "" when not said (the form then keeps its default)
 * @property daysAgo 0 = today, 1 = yesterday, 2 = day before yesterday
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
) {
    /** The entry's date, counting back from [today]. */
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
 */
data class VoiceContext(
    val people: List<String> = emptyList(),
    val categories: List<String> = DEFAULT_CATEGORIES,
    val usuals: List<Usual> = emptyList(),
)

/** Words that put a spend in a category. Order matters: the first category with a match wins. */
val CATEGORY_WORDS: Map<String, List<String>> = linkedMapOf(
    "Food" to listOf("chai", "tea", "coffee", "lunch", "dinner", "breakfast", "brunch", "snack", "snacks", "samosa", "pizza", "burger", "biryani", "swiggy", "zomato", "maggi", "momos", "juice", "food", "restaurant", "cafe", "dosa", "idli", "thali", "sweets", "ice cream", "icecream", "starbucks", "dominos", "kfc", "mcdonalds", "pani puri", "vada pav", "paratha", "canteen", "mess", "tiffin"),
    "Groceries" to listOf("grocery", "groceries", "dmart", "d mart", "blinkit", "zepto", "bigbasket", "big basket", "instamart", "vegetables", "veggies", "sabzi", "fruits", "milk", "ration", "kirana", "eggs", "bread"),
    "Transport" to listOf("uber", "ola", "rapido", "auto", "rickshaw", "cab", "taxi", "metro", "bus", "petrol", "diesel", "fuel", "parking", "toll", "fastag"),
    "Bills" to listOf("electricity", "bill", "bills", "wifi", "internet", "broadband", "recharge", "gas cylinder", "water bill", "dth", "emi", "insurance", "postpaid"),
    "Rent" to listOf("rent", "pg", "maintenance"),
    "Health" to listOf("medicine", "medicines", "doctor", "pharmacy", "hospital", "gym", "chemist", "dentist", "clinic", "lab test"),
    "Fun" to listOf("movie", "movies", "netflix", "prime", "hotstar", "spotify", "youtube premium", "concert", "game", "games", "party", "bowling", "pvr", "inox"),
    "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "clothes", "shoes", "shirt", "tshirt", "t-shirt", "jeans", "shopping", "meesho", "nykaa", "watch", "headphones"),
    "Travel" to listOf("flight", "hotel", "trip", "train", "irctc", "airbnb", "makemytrip", "goibibo", "oyo", "bus ticket"),
    "Gifts" to listOf("gift", "gifts", "present"),
)

/** Single-word category words: they end a "with ..." list of names. */
private val CATEGORY_WORD_SET: Set<String> = CATEGORY_WORDS.values.flatten().filter { ' ' !in it }.toSet()

/** Words that mean a group, not a name: "with friends" stays in the description. */
private val GROUP_WORDS = setOf("friends", "friend", "family", "colleagues", "colleague", "team", "office", "everyone", "gang", "roommates", "flatmates", "guys")

/** Words that can never start a name. */
private val NAME_STOP: Set<String> = ("for on by via using through yesterday today tomorrow rs rupee rupees and in at with cash upi " +
    "card as back me the i we my to from of a an is was it this that some money him her them his").split(" ").toSet()

/** A name: one or two words of letters, apostrophes or hyphens ("rahul", "neha gupta", "o'brien"). */
private const val NAME = "([a-z][a-z'-]*(?: [a-z][a-z'-]*)?)"

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

/**
 * JavaScript's String(number): "200" not "200.0", "1.5", and "1e+23" for very big numbers.
 * The result goes back into the text, so it has to look exactly like the web's.
 */
internal fun jsNumberToString(v: Double): String {
    if (v == 0.0) return "0"
    if (v < 0) return "-" + jsNumberToString(-v)
    val exact = BigDecimal.valueOf(v).stripTrailingZeros() // shortest digits, like JS
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

private fun titleCase(s: String): String = js("\\b[a-z]").replace(s) { it.value.uppercase() }

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
        if (any) out += jsNumberToString(jsRound((total + cur) * 100) / 100)
        total = 0.0; cur = 0.0; any = false; lastDigit = false
    }
    for (i in toks.indices) {
        val w = toks[i]
        val next = toks.getOrElse(i + 1) { "" }
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
            w == "and" && any && (next in SMALL_NUMBERS || next.firstOrNull()?.let { it in '0'..'9' } == true) -> Unit // "two hundred and fifty"
            else -> { flush(); out += w }
        }
    }
    flush()
    return " " + out.joinToString(" ") + " "
}

// ---------- the patterns, compiled once ----------

private val SPACES = Regex("\\s+")
private val DIGIT_COMMA = Regex("""(\d),(?=\d)""")
private val DIGIT_SUFFIX = js("""(\d)(rs|k|l)\b""")
private val RS = js("""\brs\b\.?""")
private val DOT_NOT_DECIMAL = Regex("""\.(?!\d)""")
private val NOT_ALLOWED = Regex("[^a-z0-9.&,' -]")

private val HINGLISH_KO = js("""\b([a-z]+) ko\b""")
private val HINGLISH_SE = js("""\b([a-z]+) se\b""")
private val HINGLISH_NE = js("""\b([a-z]+) ne\b""")
private val MAINE = js("""\bmaine\b""")
private val MUJHE = js("""\bmujhe\b""")
private val KE_LIYE = js("""\bke liye\b""")

private val AMOUNT = Regex(""" (\d+(?:\.\d+)?) """)
private val DAY_BEFORE_YESTERDAY = js("""\bday before yesterday\b""")
private val YESTERDAY = js("""\byesterday\b""")
private val TODAY = js("""\btoday\b""")
private val MODES = listOf(
    js("""\b(?:by |via |through |using |with |in |on )?(?:upi|gpay|g pay|google pay|phonepe|phone pe|paytm|bhim)\b""") to "UPI",
    js("""\b(?:by |via |using |with |in )?cash\b""") to "Cash",
    js("""\b(?:by |via |using |with |through |on )?(?:credit |debit )?card\b""") to "Card",
)
private val CURRENCY = js("""\b(?:rs|rupees?|rupay|inr|bucks)\b""")

private val GOT_BACK = js("""\b(?:got|get|received|recieved|took|taken|getting) (?:my money )?back\b|\bwapas (?:mile|aaye|liye|liya|mil gaye)\b""")
private const val THEY_RETURNED = "returned|repaid|paid me back|paid back|gave me back|gave back|has returned|has paid|wapas diye|wapas diya|wapas kiye|wapas kar diye|lauta diye|lauta diya"
private val PAID_BACK = js("""\b(?:paid|gave|given|returned|repaid|pay) back\b|\b(?:returned|repaid)\b|\b(?:wapas|lauta) (?:diye|diya|kiye|kar diye|de diye)\b""")
private const val THEY_LENT_ME = "gave me|lent me|loaned me|lend me|me diye|me diya|me de diye"
private val BORROWED = js("""\b(?:borrowed|borrow|borrowing|udhaar liya|udhar liya|udhaar li|udhar li)\b""")
private val TOOK = js("""\b(?:took|taken|liye|liya|le liye)\b""")
private val FROM = js("""\bfrom\b""")
private val LENT = js("""\b(?:lent|lend|lended|lented|lending|loaned|udhaar diya|udhar diya|udhaar di|udhar di)\b""")
private val DIYE = js("""\b(?:diye|diya|de diye)\b""")
private val TO = js("""\bto\b""")
private val GAVE = js("""\bgave\b""")
private val RECEIVED = js("""\b(?:received|recieved|credited|salary|income|earned|refund|cashback|stipend)\b""")
private val GOT = js("""\bgot\b""")

private val FILLER = js(
    """\b(?:i|we|me|my|have|has|had|just|did|spent|spend|spending|paid|pay|paying|bought|buy|purchased|purchase|lent|lend|lended|lented|lending|loaned|loan|borrowed|borrow|borrowing|took|taken|got|get|received|recieved|returned|repaid|gave|given|give|back|credited|for|on|of|the|a|an|to|from|worth|amount|udhaar|udhar|diya|diye|liya|liye|kiye|di|li|wapas|lauta|mile|de|le|kar|via|by|using|through|in|at|some|money)\b""",
)
private val COMMA_AMP = Regex("[,&]")
private val LEADING_TRAILING_AND = js("""^and\b|\band$""")
private val SALARY_WORD = js("""\b(?:salary|stipend)\b""")

/** Parses one said or typed line. See the top of this file for the steps. */
fun parseLine(raw: String, context: VoiceContext = VoiceContext()): ParsedLine {
    val people = context.people

    // 1. Clean the line.
    var t = raw.lowercase()
        .replace("₹", " rs ")
        .replace(DIGIT_COMMA, "$1") // 1,50,000 -> 150000
        .replace(DIGIT_SUFFIX, "$1 $2") // 2k -> 2 k, 500rs -> 500 rs
        .replace(RS, " rs ")
        .replace(",", " , ")
        .replace(DOT_NOT_DECIMAL, " ")
        .replace(NOT_ALLOWED, " ")
        .replace(SPACES, " ")
        .trim()
    t = " $t "
    // Hinglish: "rahul ko" = to Rahul, "rahul se" = from Rahul, "rahul ne" = Rahul (did)
    t = t.replace(HINGLISH_KO, "to $1").replace(HINGLISH_SE, "from $1").replace(HINGLISH_NE, "$1")
        .replace(MAINE, "i").replace(MUJHE, "me").replace(KE_LIYE, "for")
    t = " " + t.replace(SPACES, " ").trim() + " "

    // 2. Number words to digits.
    t = numberize(t)

    var type = EntryType.SPENT
    var amount = 0.0
    var person = ""
    var withPeople = emptyList<String>()
    var category = ""
    var mode = ""
    var daysAgo = 0

    // Removes a recognised piece. Like the web, a pattern without /g only removes its first match.
    fun cut(re: Regex, all: Boolean = false) {
        val removed = if (all) re.replace(t, " ") else re.replaceFirst(t, " ")
        t = " $removed ".replace(SPACES, " ")
    }

    // 3. Amount, day, payment mode, currency words.
    AMOUNT.find(t)?.let { m ->
        amount = m.groupValues[1].toDouble()
        t = t.replaceFirst(m.value, " ") // plain text, not a pattern (JS string replace)
    }
    if (DAY_BEFORE_YESTERDAY.containsMatchIn(t)) { daysAgo = 2; cut(DAY_BEFORE_YESTERDAY) }
    else if (YESTERDAY.containsMatchIn(t)) { daysAgo = 1; cut(YESTERDAY) }
    cut(TODAY)
    for ((re, m) in MODES) if (re.containsMatchIn(t)) { mode = m; cut(re); break }
    cut(CURRENCY, all = true)

    // 4. Type and person.
    fun after(word: String): String = js("\\b$word $NAME").find(t)?.groupValues?.get(1) ?: ""
    fun subject(verbs: String): String {
        val name = js("^ $NAME (?:$verbs)\\b").find(t)?.groupValues?.get(1) ?: return ""
        return if (name.split(" ")[0] in NAME_STOP) "" else name
    }
    var who: String
    if (GOT_BACK.containsMatchIn(t)) { type = EntryType.GOT_BACK; person = after("from") }
    else if (subject(THEY_RETURNED).also { who = it }.isNotEmpty()) { type = EntryType.GOT_BACK; person = who }
    else if (PAID_BACK.containsMatchIn(t)) { type = EntryType.PAID_BACK; person = after("to") }
    else if (subject(THEY_LENT_ME).also { who = it }.isNotEmpty()) { type = EntryType.BORROWED; person = who }
    else if (BORROWED.containsMatchIn(t) || (TOOK.containsMatchIn(t) && FROM.containsMatchIn(t))) { type = EntryType.BORROWED; person = after("from") }
    else if (LENT.containsMatchIn(t) || (DIYE.containsMatchIn(t) && TO.containsMatchIn(t))) {
        type = EntryType.LENT
        person = after("to").ifEmpty { after("(?:lent|lend|lended|lented|loaned)") }
    }
    else if (GAVE.containsMatchIn(t) && (after("to").isNotEmpty() || matchPerson(after("gave").split(" ")[0], people).isNotEmpty())) {
        type = EntryType.LENT
        person = after("to").ifEmpty { after("gave") }
    }
    else if (RECEIVED.containsMatchIn(t) || (GOT.containsMatchIn(t) && FROM.containsMatchIn(t))) { type = EntryType.RECEIVED; person = after("from") }

    if (person.isNotEmpty()) {
        val words = person.trim().split(" ")
        if (words[0] in NAME_STOP) person = "" // "to the shop" names nobody
        else {
            // Keep two words only if they're someone you know ("neha gupta"); else just the first.
            val two = if (words.size > 1 && matchPerson(words.joinToString(" "), people).isNotEmpty()) words.joinToString(" ") else ""
            val used = two.ifEmpty { words[0] }
            person = matchPerson(used, people).ifEmpty { titleCase(used) }
            cut(js("\\b(?:to |from )?" + Regex.escape(used) + "\\b"))
        }
    }

    // 5. "with Rahul, Priya and Kanika" on a spend: the names up to the first word that isn't one.
    if (type == EntryType.SPENT) {
        val toks = t.trim().split(" ").toMutableList()
        val wi = toks.indexOf("with")
        if (wi >= 0) {
            val names = mutableListOf<String>()
            var j = wi + 1
            while (j < toks.size) {
                val w = toks[j]
                if (w == "," || w == "and" || w == "&") { j++; continue }
                if (w in NAME_STOP || w in CATEGORY_WORD_SET || w in GROUP_WORDS || w.any { it in '0'..'9' }) break
                names += w
                j++
            }
            if (names.isNotEmpty()) {
                withPeople = names.map { n -> matchPerson(n, people).ifEmpty { titleCase(n) } }.distinct()
                toks.subList(wi, j).clear()
                t = " " + toks.joinToString(" ") + " "
            }
        }
    }

    // 6. Description and category.
    var desc = t.replace(FILLER, " ").replace(COMMA_AMP, " ").replace(SPACES, " ").trim()
        .replace(LEADING_TRAILING_AND, "").trim()
    if (type == EntryType.RECEIVED && desc.isEmpty() && "salary" in raw.lowercase()) desc = "salary"
    val usual = if (desc.isNotEmpty()) context.usuals.firstOrNull { it.key == desc.lowercase() } else null
    val description = usual?.description ?: (if (desc.isNotEmpty()) desc.substring(0, 1).uppercase() + desc.substring(1) else "")

    if (!type.isDebt && description.isNotEmpty()) {
        if (usual != null && usual.category.isNotEmpty() && usual.type == type) category = usual.category
        val dl = " " + description.lowercase() + " "
        val words = dl.trim().split(" ")
        if (category.isEmpty()) category = context.categories.firstOrNull { it.lowercase() in words } ?: ""
        if (category.isEmpty() && type == EntryType.SPENT) {
            category = CATEGORY_WORDS.entries.firstOrNull { (_, list) -> list.any { " $it " in dl } }?.key ?: ""
        }
        if (category.isEmpty() && type == EntryType.RECEIVED && SALARY_WORD.containsMatchIn(dl)) category = "Salary"
    }

    return ParsedLine(type, amount, person, withPeople, description, category, mode, daysAgo)
}
