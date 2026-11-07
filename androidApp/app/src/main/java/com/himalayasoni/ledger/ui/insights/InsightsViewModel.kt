package com.himalayasoni.ledger.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.data.VisibleEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

/** Holds what you've picked on Insights; the numbers are rebuilt from entries whenever either changes. */
class InsightsViewModel(visible: VisibleEntries) : ViewModel(), InsightsActions {
    private val sel = MutableStateFlow(InsightsSelection())

    val state: StateFlow<InsightsUiState?> = combine(visible.entries, sel) { entries, s -> buildInsights(entries, s, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override fun yearView(on: Boolean) = sel.update { it.copy(yearView = on, openCategory = null, trendIndex = null) }
    override fun step(by: Int) = sel.update {
        if (it.yearView) it.copy(year = it.year + by, openCategory = null, trendIndex = null)
        else it.copy(month = it.month.plusMonths(by.toLong()), day = null, openCategory = null, trendIndex = null)
    }
    override fun openMonth(m: YearMonth) = sel.update { it.copy(yearView = false, month = m, day = null, openCategory = null, trendIndex = null) }
    override fun pickDay(d: LocalDate) = sel.update { it.copy(day = d) }
    override fun toggleCategory(c: String) = sel.update { it.copy(openCategory = if (it.openCategory == c) null else c) }
    override fun trendCategory(c: String) = sel.update { it.copy(trendCategory = c, trendIndex = null) }
    override fun trendYears(on: Boolean) = sel.update { it.copy(trendYears = on, trendIndex = null) }
    override fun trendBar(i: Int) = sel.update { it.copy(trendIndex = i) }
}

/** Everything the Insights screen can ask for (so the screen stays testable without a ViewModel). */
interface InsightsActions {
    fun yearView(on: Boolean); fun step(by: Int); fun openMonth(m: YearMonth); fun pickDay(d: LocalDate)
    fun toggleCategory(c: String); fun trendCategory(c: String); fun trendYears(on: Boolean); fun trendBar(i: Int)
}
