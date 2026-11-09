package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.money.formatRupees
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Reminders: when they go off and what they say. The app's alarm code asks these functions
 * "is anything due?" and only posts what comes back, so all the rules are tested here.
 * Every reminder leaves private categories out: notifications show on the lock screen.
 */

/** The next time a daily alarm at [minuteOfDay] (e.g. 21 * 60 for 9 pm) should go off after [now]. */
fun nextDaily(now: LocalDateTime, minuteOfDay: Int): LocalDateTime {
    val today = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/** The next [day] at [minuteOfDay] after [now] (the weekly people summary: Sunday evening). */
fun nextWeekly(now: LocalDateTime, day: DayOfWeek, minuteOfDay: Int): LocalDateTime {
    val sameDay = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atStartOfDay().plusMinutes(minuteOfDay.toLong())
    return if (sameDay.isAfter(now)) sameDay else sameDay.plusWeeks(1)
}

/** "Anything to log today?" only goes out if nothing at all was logged today (by when it was logged). */
fun List<Entry>.needsDailyNudge(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    today !in filter { !it.deleted }.loggedDays(zone)

/**
 * One reminder to post. [key] is remembered once posted, so the same thing never nags twice.
 * [entryId] / [card] say what tapping it should open.
 */
data class Nudge(
    val key: String,
    val title: String,
    val text: String,
    val entryId: String? = null,
    val card: String? = null,
)

/** Refunds awaited this many days or more get one reminder each. */
const val REFUND_REMIND_DAYS = REFUND_NUDGE_DAYS

/** "Train to Jaipur: ₹1,320 refund still awaited after 8 days", once per refund. */
fun List<Entry>.refundNudges(privateCategories: Set<String>, today: LocalDate, reminded: Set<String>): List<Nudge> =
    withoutPrivate(privateCategories).refundsAwaited(today)
        .filter { it.days >= REFUND_REMIND_DAYS && "refund:${it.entry.id}" !in reminded }
        .map { r ->
            val what = r.entry.description.ifBlank { r.entry.category }.ifBlank { "An expense" }
            Nudge(
                key = "refund:${r.entry.id}",
                title = "Refund still awaited",
                text = "$what: ${formatRupees(r.amount)} refund still awaited after ${r.days} days",
                entryId = r.entry.id,
            )
        }

/** Card bills get a reminder this many days before they're due (or on a later day, if the phone missed it). */
const val CARD_REMIND_DAYS = 3

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * "HDFC bill due 3 Dec · Ledger shows ₹4,300": for each card with a due day set, once per bill,
 * from [CARD_REMIND_DAYS] days before it's due, and only if Ledger shows something owed on it.
 */
fun List<Entry>.cardBillNudges(
    privateCategories: Set<String>,
    cards: List<CardSettings>,
    today: LocalDate,
    reminded: Set<String>,
): List<Nudge> {
    val dues = withoutPrivate(privateCategories).cardDues(cards.map { it.name })
    return cards.mapNotNull { c ->
        val day = c.dueDay ?: return@mapNotNull null
        val due = nextDueDate(day, today)
        val left = ChronoUnit.DAYS.between(today, due)
        val key = "card:${c.name.lowercase()}:$due"
        val owed = dues.firstOrNull { it.name.equals(c.name, true) }?.due ?: 0.0
        if (left > CARD_REMIND_DAYS || key in reminded || owed <= 0.5) return@mapNotNull null
        val whenText = when (left) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $left days" }
        Nudge(
            key = key,
            title = "${c.name} bill due $whenText",
            text = "${c.name} bill due ${due.format(dayMonth)} · Ledger shows ${formatRupees(owed)}",
            card = c.name,
        )
    }
}

/** "Owed to you: Rahul ₹1,500 · Priya ₹300" (loans only), or null when nobody owes you anything. */
fun List<Entry>.owedNudge(privateCategories: Set<String>, max: Int = 4): Nudge? {
    val owing = filter { !it.deleted }.withoutPrivate(privateCategories).peopleIndex().values
        .filter { it.balance >= 1.0 }
        .sortedByDescending { it.balance }
    if (owing.isEmpty()) return null
    val shown = owing.take(max).joinToString(" · ") { "${it.name} ${formatRupees(it.balance)}" }
    val more = if (owing.size > max) " · and ${owing.size - max} more" else ""
    val total = owing.sumOf { it.balance }
    return Nudge(
        key = "people",
        title = "${formatRupees(total)} owed to you",
        text = "Owed to you: $shown$more",
    )
}

/**
 * Forgets reminders that can't come up again (refunds no longer awaited, bills already past),
 * so the remembered list stays small.
 */
fun List<Entry>.stillRelevant(reminded: Set<String>, today: LocalDate): Set<String> {
    val awaited = refundsAwaited(today).map { "refund:${it.entry.id}" }.toSet()
    return reminded.filter { k ->
        when {
            k.startsWith("refund:") -> k in awaited
            k.startsWith("card:") -> runCatching { !LocalDate.parse(k.substringAfterLast(':')).isBefore(today) }.getOrDefault(false)
            else -> false
        }
    }.toSet()
}
