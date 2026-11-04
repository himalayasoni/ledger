package com.himalayasoni.ledger.ui.home

import com.himalayasoni.ledger.core.domain.balances
import com.himalayasoni.ledger.core.domain.cost
import com.himalayasoni.ledger.core.domain.people
import com.himalayasoni.ledger.core.domain.receivedIn
import com.himalayasoni.ledger.core.domain.spentIn
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.money.formatRupees
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Home screen shows, already formatted. The screen just draws it. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val monthName: String,
        val spent: String,
        val received: String,
        val owedToYou: String,
        val youOwe: String,
        val days: List<DayGroup>,
    ) : HomeUiState
}

data class DayGroup(val label: String, val spent: String?, val rows: List<EntryRow>)

enum class RowKind { OUT, IN, LOAN }

data class EntryRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val kind: RowKind,
    val badge: String?,      // e.g. "Lent", "Received"
    val category: String,
    val initial: String,
)

/** Pure function: entries + today → screen state. Easy to unit-test, no Android needed. */
fun buildHomeState(entries: List<Entry>, today: LocalDate): HomeUiState.Ready {
    val month = YearMonth.from(today)
    val people = entries.balances().values
    val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    val days = entries.take(200).groupBy { it.date }.map { (date, list) ->
        val label = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayFmt)
        }
        val spent = list.filter { it.type == EntryType.SPENT }.sumOf { it.cost() }
        DayGroup(label, if (spent > 0) "Spent ${formatRupees(spent)}" else null, list.map(::toRow))
    }
    return HomeUiState.Ready(
        monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
        spent = formatRupees(entries.spentIn(month)),
        received = formatRupees(entries.receivedIn(month)),
        owedToYou = formatRupees(people.filter { it.balance > 0 }.sumOf { it.balance }),
        youOwe = formatRupees(people.filter { it.balance < 0 }.sumOf { -it.balance }),
        days = days,
    )
}

private fun toRow(e: Entry): EntryRow {
    val title = e.description.ifBlank {
        if (e.type.isDebt) "${e.type.label} ${if (e.person.isNotBlank()) "· ${e.person}" else ""}".trim()
        else e.category.ifBlank { e.type.label }
    }
    val parts = buildList {
        val with = e.people()
        if (with.isNotEmpty()) add("with " + with.take(2).joinToString(", ") + if (with.size > 2) " +${with.size - 2}" else "")
        else if (e.person.isNotBlank() && e.description.isNotBlank()) add(e.person)
        if (e.category.isNotBlank() && !e.type.isDebt) add(e.category)
        add(e.mode)
    }
    val kind = when {
        e.type.isDebt -> RowKind.LOAN
        e.type == EntryType.RECEIVED -> RowKind.IN
        else -> RowKind.OUT
    }
    return EntryRow(
        id = e.id, title = title, subtitle = parts.joinToString(" · "),
        amount = (if (e.type.sign < 0) "−" else "+") + formatRupees(e.amount),
        kind = kind,
        badge = if (kind == RowKind.OUT) null else e.type.label,
        category = e.category,
        initial = (e.category.ifBlank { e.description }.ifBlank { "?" }).take(1).uppercase(),
    )
}
