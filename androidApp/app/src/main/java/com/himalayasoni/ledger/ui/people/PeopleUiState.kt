package com.himalayasoni.ledger.ui.people

import com.himalayasoni.ledger.core.domain.OldLoanIssue
import com.himalayasoni.ledger.core.domain.involves
import com.himalayasoni.ledger.core.domain.oldLoanIssue
import com.himalayasoni.ledger.core.domain.peopleIndex
import com.himalayasoni.ledger.core.domain.sortedForList
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.ui.home.DayGroup
import com.himalayasoni.ledger.ui.home.dayGroups
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PillKind { OWES_YOU, YOU_OWE, NEUTRAL }

data class PersonRowUi(val name: String, val initial: String, val meta: String, val pill: String?, val pillKind: PillKind)

/** What the People tab shows. Same numbers and wording as the web app's viewPeople(). */
data class PeopleUiState(
    val owed: String, val owedCount: String,
    val owe: String, val oweCount: String,
    val spentOnOthers: String, val spentOnOthersNote: String,
    val issues: List<IssueUi>,
    val rows: List<PersonRowUi>,
)

/** A balance that doesn't add up (a repayment with no loan in Ledger). */
data class IssueUi(val label: String, val issue: OldLoanIssue)

enum class BalanceKind { OWES_YOU, YOU_OWE, SETTLED }

/** One person's page. */
data class PersonUiState(
    val name: String,
    val balanceKind: BalanceKind,
    val balanceText: String,        // "Rahul owes you" / "You owe Rahul" / "All settled. Nothing pending."
    val balanceAmount: String?,     // "₹1,500"
    val treatLine: String?,         // "Spent on Rahul: ₹800 across 1 shared expense · paid you back ₹200"
    val issue: IssueUi?,
    val canMarkSharesPaid: Boolean,
    val balance: Double,
    val historyCount: Int,
    val history: List<DayGroup>,
)

private fun people(n: Int) = "$n ${if (n == 1) "person" else "people"}"

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, d MMM" else "d MMM yyyy", Locale.ENGLISH))
}

fun buildPeopleState(entries: List<Entry>, all: List<Entry>, today: LocalDate): PeopleUiState {
    val index = entries.peopleIndex()
    val list = index.sortedForList()
    var owed = 0.0; var nOwed = 0; var owe = 0.0; var nOwe = 0; var treat = 0.0; var nTreat = 0
    for (p in list) {
        if (p.balance > 0) { owed += p.balance; nOwed++ } else if (p.balance < 0) { owe -= p.balance; nOwe++ }
        if (p.spentOnThem > 0) { treat += p.spentOnThem; nTreat++ }
    }
    val rows = list.map { p ->
        val (pill, kind) = when {
            p.balance > 0 -> "owes you ${formatRupees(p.balance)}" to PillKind.OWES_YOU
            p.balance < 0 -> "you owe ${formatRupees(p.balance)}" to PillKind.YOU_OWE
            p.spentOnThem > 0 -> "${formatRupees(p.spentOnThem)} on them" to PillKind.NEUTRAL
            p.hasLoans -> "settled" to PillKind.NEUTRAL
            else -> null to PillKind.NEUTRAL
        }
        val extra = if (p.balance != 0.0 && p.spentOnThem > 0) " · ${formatRupees(p.spentOnThem)} spent on them" else ""
        PersonRowUi(
            p.name, p.name.take(1).uppercase(),
            "${p.entryCount} entr${if (p.entryCount == 1) "y" else "ies"} · last ${dayLabel(p.lastDate, today)}$extra",
            pill, kind,
        )
    }
    // repayments are checked against all entries (including private ones), like the web app
    val issues = list.mapNotNull { all.oldLoanIssue(it) }.map(::issueUi)
    return PeopleUiState(
        formatRupees(owed), people(nOwed), formatRupees(owe), people(nOwe),
        formatRupees(treat), "Treats and shared bills with ${people(nTreat)}, not expected back",
        issues, rows,
    )
}

fun issueUi(i: OldLoanIssue) = IssueUi(
    if (i.type == EntryType.PAID_BACK) "${i.name} · you paid back ${formatRupees(i.extra)}" else "${i.name} · returned ${formatRupees(i.extra)} to you",
    i,
)

fun buildPersonState(name: String, entries: List<Entry>, all: List<Entry>, today: LocalDate): PersonUiState? {
    val p = entries.peopleIndex()[name.lowercase()] ?: return null
    val history = entries.filter { it.involves(name) }
    val (kind, text, amount) = when {
        p.balance > 0 -> Triple(BalanceKind.OWES_YOU, "${p.name} owes you", formatRupees(p.balance))
        p.balance < 0 -> Triple(BalanceKind.YOU_OWE, "You owe ${p.name}", formatRupees(p.balance))
        else -> Triple(BalanceKind.SETTLED, if (p.hasLoans) "All settled. Nothing pending." else "No lending or borrowing yet.", null)
    }
    val treat = if (p.spentOnThem > 0 || p.repaid > 0)
        "Spent on ${p.name}: ${formatRupees(p.spentOnThem)} across ${p.sharedCount} shared expense${if (p.sharedCount == 1) "" else "s"}" +
            (if (p.repaid > 0) " · paid you back ${formatRupees(p.repaid)}" else "")
    else null
    return PersonUiState(
        p.name, kind, text, amount, treat,
        all.oldLoanIssue(p)?.let(::issueUi),
        p.unsettled > 0, p.balance, history.size, dayGroups(history, today, who = p.name),
    )
}

/** The explanation shown on the person page above the Fix button. */
fun IssueUi.explain(): String = if (issue.type == EntryType.PAID_BACK)
    "${formatRupees(issue.extra)} of this comes from paying ${issue.name} back with no matching loan in Ledger. If you borrowed it before you started using Ledger, it shouldn't count."
else
    "${formatRupees(issue.extra)} of this comes from ${issue.name} returning money with no matching loan in Ledger. If you lent it before you started using Ledger, it shouldn't count."
