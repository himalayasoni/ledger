package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.money.formatRupees
import java.time.LocalDate
import java.time.YearMonth

/*
 * What the home-screen widget shows. Worked out here, as plain text, so it can be unit-tested
 * and the widget itself only lays it out. Private categories are ALWAYS left out: a widget sits
 * on the home screen for anyone to see, whatever the "show private" switch says in the app.
 */

/** Shown instead of an amount when "Hide amounts on widgets" is on. */
const val HIDDEN_AMOUNT = "••••"

/** One one-tap usual on the widget: "Chai" "₹20". [key] finds the usual again when it's tapped. */
data class WidgetChip(val key: String, val label: String, val amount: String)

data class WidgetSummary(
    /** Spent this month so far, e.g. "₹12,340" (or [HIDDEN_AMOUNT]). */
    val month: String,
    /** Spent today, e.g. "₹240". */
    val today: String,
    val chips: List<WidgetChip>,
)

/**
 * The usuals worth a one-tap button: ones you've logged at least twice, that need nothing more
 * (a card bill needs a fresh amount every time, so it's left out). Most used first.
 */
fun List<Entry>.widgetUsuals(privateCategories: Set<String>, limit: Int = 4): List<Usual> =
    filter { !it.deleted }.withoutPrivate(privateCategories).usuals()
        .filter { it.count >= 2 && it.amount > 0 && it.type != EntryType.CARD_BILL && (!it.type.isDebt || it.person.isNotBlank()) }
        .top(limit = limit)

fun widgetSummary(
    entries: List<Entry>,
    privateCategories: Set<String>,
    today: LocalDate,
    hideAmounts: Boolean,
    chips: Int = 4,
): WidgetSummary {
    val visible = entries.filter { !it.deleted }.withoutPrivate(privateCategories)
    fun money(v: Double) = if (hideAmounts) HIDDEN_AMOUNT else formatRupees(v)
    val spentToday = visible.filter { it.type == EntryType.SPENT && it.date == today }.sumOf { it.cost() }
    return WidgetSummary(
        month = money(visible.spentIn(YearMonth.from(today))),
        today = money(spentToday),
        chips = entries.widgetUsuals(privateCategories, chips).map {
            WidgetChip(it.key, it.description, if (hideAmounts) "" else formatRupees(it.amount))
        },
    )
}

/** The widget's brief confirmation after a one-tap log: "Logged Chai ₹20" (no amount when hidden). */
fun Usual.widgetLogged(hideAmounts: Boolean): String =
    "Logged $description" + if (hideAmounts) "" else " ${formatRupees(amount)}"
