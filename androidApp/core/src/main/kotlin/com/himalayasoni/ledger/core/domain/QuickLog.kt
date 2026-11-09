package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.DEFAULT_CATEGORIES
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.core.voice.ParsedLine
import com.himalayasoni.ledger.core.voice.VoiceContext
import com.himalayasoni.ledger.core.voice.parseLine
import java.time.LocalDate

/*
 * Logging without opening the app: a one-tap usual on the home-screen widget, or a line typed
 * straight into a notification ("chai 20 cash"). Both end up as an ordinary new entry, built
 * here with the same rules as the add sheet's Save, so the app can't tell the difference.
 */

private fun round2(v: Double) = Math.round(v * 100) / 100.0

/** A usual logged again, dated [today]: same type, amount, category, mode and people as last time. */
fun Usual.toEntry(id: String, today: LocalDate, now: Long): Entry = Entry(
    id = id, type = type, amount = round2(amount), date = today,
    description = description,
    person = if (type == EntryType.SPENT) "" else person,
    category = if (type.hasCategory) category.ifBlank { if (type == EntryType.SPENT) "Other" else "" } else "",
    mode = mode.ifBlank { "UPI" },
    card = if (type == EntryType.SPENT && mode.equals("Card", true)) card else "",
    withPeople = if (type == EntryType.SPENT) withPeople else emptyList(),
    includesMe = if (type == EntryType.SPENT && withPeople.isNotEmpty()) includesMe else true,
    createdAt = now, updatedAt = now,
)

/** What came of a line typed into a notification. */
sealed interface ReplyResult {
    /** Everything needed was there: save this. */
    data class Ready(val entry: Entry) : ReplyResult
    /** Something required is missing ("the amount", "who it was with"): finish it in the add pop-up. */
    data class Incomplete(val missing: String) : ReplyResult
    /** Nothing was typed. */
    data object Empty : ReplyResult
}

/**
 * What the voice parser should know about you, built the same way the add sheet builds it
 * (AddEntryViewModel): names and usuals from entries that aren't private, and categories used
 * on them, then the private ones (so a line can still be filed there), then the built-in ones.
 */
fun List<Entry>.voiceContext(privateCategories: Set<String>, today: LocalDate, savedCards: Collection<String> = emptyList()): VoiceContext {
    val visible = filter { !it.deleted }.withoutPrivate(privateCategories)
    val used = visible.filter { !it.type.isDebt && it.category.isNotBlank() }.map { it.category }
    val names = visible.peopleIndex().values.sortedByDescending { it.lastDate }.map { it.name }
    val cats = (used + privateCategories + DEFAULT_CATEGORIES).distinctBy { it.lowercase() }
    return VoiceContext(names, cats, visible.usuals(), today, visible.cardNames(savedCards))
}

/** Like the add sheet's Save after "Say it": the parsed line as an entry, or what's missing. */
fun ParsedLine.toEntry(id: String, today: LocalDate, now: Long): ReplyResult {
    val missing = when {
        amount <= 0 -> "the amount"
        type.isDebt && person.isBlank() -> "who it was with"
        type == EntryType.CARD_BILL && card.isBlank() -> "which card"
        else -> null
    }
    if (missing != null) return ReplyResult.Incomplete(missing)
    val m = mode.ifBlank { "UPI" }
    return ReplyResult.Ready(Entry(
        id = id, type = type, amount = round2(amount), date = date(today),
        description = description.trim(),
        person = if (type == EntryType.SPENT) "" else person.trim(),
        category = if (!type.hasCategory) "" else category.ifBlank { if (type == EntryType.SPENT) "Other" else "" },
        mode = if (type == EntryType.CARD_BILL && m.equals("Card", true)) "UPI" else m,
        card = when {
            type == EntryType.CARD_BILL -> card
            type == EntryType.SPENT && m.equals("Card", true) -> card
            else -> ""
        },
        withPeople = if (type == EntryType.SPENT) withPeople else emptyList(),
        createdAt = now, updatedAt = now,
    ))
}

/** A line typed into the daily reminder ("chai 20 cash") turned into an entry, if it's complete. */
fun parseReply(text: String, context: VoiceContext, id: String, now: Long): ReplyResult {
    val line = text.trim()
    if (line.isEmpty()) return ReplyResult.Empty
    return parseLine(line, context).toEntry(id, context.today, now)
}

/**
 * A short name for an entry, safe to show on the lock screen: "Chai", "Lent to Ashish", "HDFC bill".
 * A private category is never used as the name.
 */
fun Entry.shortTitle(privateCategories: Set<String> = emptySet()): String {
    if (description.isNotBlank()) return description
    val hidden = privateCategories.any { it.equals(category, true) }
    return when {
        type == EntryType.CARD_BILL -> if (card.isNotBlank()) "$card bill" else "Card bill"
        type == EntryType.LENT || type == EntryType.PAID_BACK -> "${type.label} to $person"
        type.isDebt -> "${type.label} from $person"
        category.isNotBlank() && !hidden -> category
        else -> type.label
    }
}

/** "Logged ₹20 · Chai": what the notification says after a reply was saved. */
fun Entry.loggedLine(privateCategories: Set<String> = emptySet()): String =
    "Logged ${formatRupees(amount)} · ${shortTitle(privateCategories)}"
