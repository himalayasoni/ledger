package com.himalayasoni.ledger.ui.entry

import com.himalayasoni.ledger.core.domain.balances
import com.himalayasoni.ledger.core.domain.net
import com.himalayasoni.ledger.core.domain.people
import com.himalayasoni.ledger.core.domain.repaid
import com.himalayasoni.ledger.core.domain.share
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.RefundStatus
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.ui.home.RowKind
import com.himalayasoni.ledger.ui.home.entryRow
import com.himalayasoni.ledger.ui.home.personLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What you can do from an entry's page. The page draws one button per action, in this order. */
enum class EntryAction { MARK_ARRIVED, GOT_IT_BACK, PAID_BACK, EDIT, MONEY_BACK, DUPLICATE, DELETE }

/** One button: [primary] is the marigold one (at most one per page). */
data class ActionUi(val action: EntryAction, val label: String, val primary: Boolean = false)

/** A "label · value" line in the details card. [swatch] = category colour dot; [card] makes it a link to that card's page. */
data class Fact(val label: String, val value: String, val swatch: String? = null, val card: String? = null)

/** One person on a shared spend: their share and what they've paid back. */
data class ShareLine(val name: String, val share: String, val status: String, val settled: Boolean)

/** The money-back block. */
data class MoneyBackUi(
    val title: String,          // "Refund" or "Money back from Dad"
    val amount: String,         // "₹1,320"
    val awaited: Boolean,
    val status: String,         // "Awaited" / "Arrived"
    val lines: List<String>,    // "Confirmed Fri, 6 Nov", "Arrived Sun, 8 Nov · to HDFC card"
)

/** Everything an entry's page shows, already formatted (built by [buildEntryDetail]). */
data class EntryDetail(
    val id: String,
    val kind: RowKind,
    val category: String,
    val initial: String,
    val typeLabel: String,          // "Spent", "Card bill", "Lent"…
    val title: String,
    val amount: String,             // "₹180": what it came to (after money back)
    val paidLine: String?,          // "₹1,500 paid" when money back changed it
    val facts: List<Fact>,
    val sharesTitle: String?,       // "Split 3 ways · ₹1,067 each"
    val shares: List<ShareLine>,
    val note: String,
    val moneyBack: MoneyBackUi?,
    val actions: List<ActionUi>,
    /** For loans: the person and their open balance (+ they owe you, − you owe them), for "Got it back" / "I paid back". */
    val person: String = "",
    val balance: Double = 0.0,
)

private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

private fun short(d: LocalDate, today: LocalDate) = when (d) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    else -> d.format(SHORT_DATE) + if (d.year != today.year) " ${d.year}" else ""
}

/** Pure: an entry (plus all entries, for a person's balance) → its page. No Android, so it's unit-testable. */
fun buildEntryDetail(e: Entry, all: List<Entry>, today: LocalDate): EntryDetail {
    val row = entryRow(e)
    val refund = e.refund?.takeIf { e.type == EntryType.SPENT }
    val isCardPay = e.mode.equals("Card", true)

    val facts = buildList {
        add(Fact("Date", e.date.format(LONG_DATE)))
        if (e.type.hasCategory && e.category.isNotBlank()) add(Fact("Category", e.category, swatch = e.category))
        if (e.type.isDebt && e.person.isNotBlank()) add(Fact(e.type.personLabel, e.person))
        if (e.type == EntryType.RECEIVED && e.person.isNotBlank()) add(Fact("Received from", e.person))
        when {
            e.type == EntryType.CARD_BILL -> {
                if (e.card.isNotBlank()) add(Fact("Card", "${e.card} card", card = e.card))
                if (e.mode.isNotBlank()) add(Fact("Paid from", e.mode))
                add(Fact("Counts as", "Not spending (the card's spends were logged already)"))
            }
            isCardPay -> add(Fact("Paid with", if (e.card.isNotBlank()) "${e.card} card" else "Card", card = e.card.takeIf { it.isNotBlank() }))
            e.mode.isNotBlank() -> add(Fact(if (e.type.sign > 0 && e.type != EntryType.BORROWED) "Received in" else "Paid with", e.mode))
        }
        if (e.prior > 0) add(Fact("From before Ledger", if (e.prior >= e.amount) "All of it" else formatRupees(e.prior)))
    }

    // shared spend: everyone's share and who has paid theirs back
    val with = if (e.type == EntryType.SPENT) e.people() else emptyList()
    val ways = with.size + if (e.includesMe) 1 else 0
    val shares = with.map { n ->
        val share = e.share()
        val back = e.repaid(n)
        ShareLine(
            n, formatRupees(share),
            when {
                back >= share - 0.005 -> "Paid back"
                back > 0 -> "${formatRupees(back)} paid back"
                else -> "Not paid back"
            },
            settled = back >= share - 0.005,
        )
    }

    val moneyBack = refund?.let { r ->
        val awaited = r.status == RefundStatus.AWAITED
        val where = when {
            r.to.equals("Card", true) -> if (r.card.isNotBlank()) "${r.card} card" else "card"
            r.to.isNotBlank() -> r.to
            else -> ""
        }
        MoneyBackUi(
            title = if (r.from.isNotBlank()) "Money back from ${r.from}" else "Refund",
            amount = formatRupees(r.amount),
            awaited = awaited,
            status = if (awaited) "Awaited" else "Arrived",
            lines = buildList {
                add((if (awaited) "Confirmed " else "Logged ") + short(r.confirmed, today))
                if (!awaited) add("Arrived " + short(r.received ?: r.confirmed, today) + if (where.isNotBlank()) " · to $where" else "")
                if (r.amount < e.amount) add("Part: ${formatRupees(r.amount)} of ${formatRupees(e.amount)}") else add("The full amount")
            },
        )
    }

    // a loan with someone you still owe or who still owes you
    val bal = if (e.type.isDebt && e.person.isNotBlank()) all.balances()[e.person.lowercase()]?.balance ?: 0.0 else 0.0

    val awaited = refund?.status == RefundStatus.AWAITED
    val actions = buildList {
        if (awaited) add(ActionUi(EntryAction.MARK_ARRIVED, "Mark arrived", primary = true))
        if (bal > 0) add(ActionUi(EntryAction.GOT_IT_BACK, "Got it back", primary = !awaited))
        if (bal < 0) add(ActionUi(EntryAction.PAID_BACK, "I paid back", primary = !awaited))
        add(ActionUi(EntryAction.EDIT, "Edit", primary = isEmpty()))
        if (e.type == EntryType.SPENT) add(ActionUi(EntryAction.MONEY_BACK, if (refund == null) "Got money back" else "Edit money back"))
        add(ActionUi(EntryAction.DUPLICATE, "Duplicate"))
        add(ActionUi(EntryAction.DELETE, "Delete"))
    }

    return EntryDetail(
        id = e.id,
        kind = row.kind,
        category = e.category,
        initial = row.initial,
        typeLabel = e.type.label,
        title = row.title,
        amount = formatRupees(if (refund != null) e.net() else e.amount),
        paidLine = refund?.let { "${formatRupees(e.amount)} paid" },
        facts = facts,
        sharesTitle = if (with.isEmpty()) null else
            "Split $ways ways · ${formatRupees(e.share())} each" + if (!e.includesMe) " (not you)" else "",
        shares = shares,
        note = e.note.trim(),
        moneyBack = moneyBack,
        actions = actions,
        person = if (e.type.isDebt) e.person else "",
        balance = bal,
    )
}
