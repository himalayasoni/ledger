package com.himalayasoni.ledger.ui.home

import com.himalayasoni.ledger.core.domain.balances
import com.himalayasoni.ledger.core.domain.cost
import com.himalayasoni.ledger.core.domain.loggedDays
import com.himalayasoni.ledger.core.domain.people
import com.himalayasoni.ledger.core.domain.receivedIn
import com.himalayasoni.ledger.core.domain.repaid
import com.himalayasoni.ledger.core.domain.share
import com.himalayasoni.ledger.core.domain.spentIn
import com.himalayasoni.ledger.core.domain.spentInUntil
import com.himalayasoni.ledger.core.domain.streak
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.RefundStatus
import com.himalayasoni.ledger.core.domain.net
import com.himalayasoni.ledger.core.domain.cardDues
import com.himalayasoni.ledger.core.domain.refundsAwaited
import com.himalayasoni.ledger.core.domain.REFUND_NUDGE_DAYS
import com.himalayasoni.ledger.core.money.formatRupees
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the Home screen shows, already formatted. The screen just draws it. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val dateLine: String,           // "Saturday, 7 November"
        val isEmpty: Boolean,
        val monthName: String,
        val spent: String,
        val comparisonBold: String?,    // "₹5,170 more"  (null: no last-month data)
        val comparisonRest: String,     // " than this point last month (24% higher)" or "November so far"
        val received: String,
        val owedToYou: String,
        val youOwe: String,
        val streak: Int,
        val loggedToday: Boolean,
        val week: List<DayDot>,
        val totalCount: Int,
        val days: List<DayGroup>,
        val inFlight: InFlight? = null,
    ) : HomeUiState
}

/** One of the seven dots in the streak card. */
data class DayDot(val letter: String, val logged: Boolean, val isToday: Boolean)

data class DayGroup(val label: String, val spent: String?, val rows: List<EntryRow>)

enum class RowKind { OUT, IN, LOAN_OUT, LOAN_IN }

data class EntryRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val note: String,
    val amount: String,
    val kind: RowKind,
    val badge: String?,      // small caps under the amount: "LENT", "RECEIVED"
    val category: String,
    val initial: String,
)

private val ENGLISH = Locale.ENGLISH

/** Pure function: entries + today → screen state. Easy to unit-test, no Android needed. */
fun buildHomeState(entries: List<Entry>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): HomeUiState.Ready {
    val month = YearMonth.from(today)
    val people = entries.balances().values

    // "₹5,170 more than this point last month (24% higher)", same wording as the web app
    val spent = entries.spentIn(month)
    val prev = entries.spentInUntil(month.minusMonths(1), today.dayOfMonth)
    val monthName = month.month.getDisplayName(TextStyle.FULL, ENGLISH)
    val (bold, rest) = if (prev > 0) {
        val diff = spent - prev
        val pct = (abs(diff) / prev * 100).roundToInt()
        if (diff <= 0) "${formatRupees(diff)} less" to " than this point last month ($pct% lower)"
        else "${formatRupees(diff)} more" to " than this point last month ($pct% higher)"
    } else null to "$monthName so far"

    val logged = entries.loggedDays(zone)
    val week = (6 downTo 0).map { i ->
        val d = today.minusDays(i.toLong())
        DayDot(d.dayOfWeek.getDisplayName(TextStyle.NARROW, ENGLISH), d in logged, i == 0)
    }

    val days = dayGroups(entries.take(30), today)

    return HomeUiState.Ready(
        dateLine = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ENGLISH)),
        isEmpty = entries.isEmpty(),
        monthName = monthName,
        spent = formatRupees(spent),
        comparisonBold = bold,
        comparisonRest = rest,
        received = formatRupees(entries.receivedIn(month)),
        owedToYou = formatRupees(people.filter { it.balance > 0 }.sumOf { it.balance }),
        youOwe = formatRupees(people.filter { it.balance < 0 }.sumOf { -it.balance }),
        streak = streak(logged, today),
        loggedToday = today in logged,
        week = week,
        totalCount = entries.size,
        days = days,
        inFlight = inFlight(entries, today),
    )
}

/** Entries grouped under "Today", "Yesterday", "Wed, 4 Nov"… with each day's spending. [who] shows that person's share on shared rows. */
fun dayGroups(entries: List<Entry>, today: LocalDate, who: String? = null): List<DayGroup> {
    val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", ENGLISH)
    val yearFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", ENGLISH)
    return entries.groupBy { it.date }.map { (date, list) ->
        val label = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(if (date.year == today.year) dayFmt else yearFmt)
        }
        val daySpent = list.filter { it.type == EntryType.SPENT }.sumOf { it.cost() }
        DayGroup(label, if (daySpent > 0) "Spent ${formatRupees(daySpent)}" else null, list.map { entryRow(it, who) })
    }
}

/** Same title and details line as the web app's rowHtml(). */
fun entryRow(e: Entry, who: String? = null): EntryRow {
    val title = e.description.ifBlank {
        when {
            e.type == EntryType.CARD_BILL -> if (e.card.isNotBlank()) "${e.card} card bill" else "Card bill"
            e.type.isDebt -> if (e.person.isNotBlank()) "${e.type.personLabel} ${e.person}" else e.type.label
            else -> e.category.ifBlank { e.type.label }
        }
    }
    val refund = e.refund?.takeIf { e.type == EntryType.SPENT }
    val parts = buildList {
        val with = e.people()
        if (with.isNotEmpty()) add("with " + with.take(2).joinToString(", ") + if (with.size > 2) " +${with.size - 2}" else "")
        else if (e.person.isNotBlank() && !(e.type.isDebt && e.description.isBlank())) add(e.person)
        if (e.prior > 0) add(if (e.prior >= e.amount) "loan from before Ledger" else "${formatRupees(e.prior)} from before Ledger")
        if (e.repaid() > 0) add("${formatRupees(e.repaid())} paid back")
        // a refund shows what was paid and what's coming back, so the smaller amount on the right makes sense
        if (refund != null) add(
            "${formatRupees(e.amount)} paid · " +
                if (refund.status == RefundStatus.AWAITED) "${formatRupees(refund.amount)} refund awaited" else "${formatRupees(refund.amount)} refunded",
        )
        if (e.category.isNotBlank() && e.type.hasCategory) add(e.category)
        if (e.type == EntryType.CARD_BILL) add("not spending")
        val payment = if (e.mode.equals("Card", true) && e.card.isNotBlank()) "${e.card} card" else e.mode
        if (payment.isNotBlank()) add(payment)
    }
    val kind = when {
        e.type.isDebt -> if (e.type.sign < 0) RowKind.LOAN_OUT else RowKind.LOAN_IN
        e.type == EntryType.RECEIVED -> RowKind.IN
        else -> RowKind.OUT
    }
    return EntryRow(
        id = e.id, title = title, subtitle = parts.joinToString(" · "), note = e.note.trim(),
        amount = (if (e.type.sign < 0) "−" else "+") + formatRupees(if (refund != null) e.net() else e.amount),
        kind = kind,
        badge = when {
            who != null && e.type == EntryType.SPENT && e.people().any { it.equals(who, true) } ->
                formatRupees(maxOf(0.0, e.share() - e.repaid(who))) + " theirs"
            refund?.status == RefundStatus.AWAITED -> "Refund awaited"
            refund != null -> "Refunded"
            e.type == EntryType.CARD_BILL -> "Card bill"
            kind == RowKind.OUT -> null
            else -> e.type.label
        },
        category = e.category,
        initial = (e.category.ifBlank { e.description }.trim().ifBlank { "?" }).take(1).uppercase(),
    )
}

/** "Lent to", "Borrowed from"…, the web app's TYPES[type].person. */
val EntryType.personLabel: String get() = when (this) {
    EntryType.SPENT -> "Spent with"
    EntryType.RECEIVED -> "Received from"
    EntryType.LENT -> "Lent to"
    EntryType.BORROWED -> "Borrowed from"
    EntryType.GOT_BACK -> "Got back from"
    EntryType.PAID_BACK -> "Paid back to"
    EntryType.CARD_BILL -> "Card"
}

/** "Money out", "They owe you"…, the web app's TYPES[type].cap. */
val EntryType.caption: String get() = when (this) {
    EntryType.SPENT -> "Money out"
    EntryType.RECEIVED -> "Money in"
    EntryType.LENT -> "They owe you"
    EntryType.BORROWED -> "You owe them"
    EntryType.GOT_BACK -> "They repaid you"
    EntryType.PAID_BACK -> "You repaid them"
    EntryType.CARD_BILL -> "Not spending"
}

/** Money on its way: refunds awaited and what's due on each card. Shown on Home only when there is some. */
data class InFlight(val refunds: String?, val refundsNote: String?, val cards: List<Pair<String, String>>, val cardNote: String?)

fun inFlight(entries: List<Entry>, today: LocalDate): InFlight? {
    val awaited = entries.refundsAwaited(today)
    val cards = entries.cardDues().filter { it.due > 0 || it.notLogged > 0 }
    if (awaited.isEmpty() && cards.isEmpty()) return null
    val oldest = awaited.firstOrNull { it.days >= REFUND_NUDGE_DAYS }
    return InFlight(
        refunds = if (awaited.isEmpty()) null else "${formatRupees(awaited.sumOf { it.amount })} · ${awaited.size} refund${if (awaited.size == 1) "" else "s"} awaited",
        refundsNote = oldest?.let { "${it.entry.description.ifBlank { it.entry.category }}: waiting ${it.days} days. Worth checking?" },
        cards = cards.map { it.name to formatRupees(it.due) },
        cardNote = cards.filter { it.notLogged > 0 }.takeIf { it.isNotEmpty() }?.joinToString(" · ") {
            "${it.name}: ${formatRupees(it.notLogged)} paid that isn't logged in Ledger (fees, interest or older spends)"
        },
    )
}
