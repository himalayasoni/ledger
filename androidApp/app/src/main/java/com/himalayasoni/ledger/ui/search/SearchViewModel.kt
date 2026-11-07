package com.himalayasoni.ledger.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.core.domain.Filters
import com.himalayasoni.ledger.core.domain.outAndIn
import com.himalayasoni.ledger.core.domain.peopleIndex
import com.himalayasoni.ledger.core.domain.search
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.data.VisibleEntries
import com.himalayasoni.ledger.ui.home.DayGroup
import com.himalayasoni.ledger.ui.home.EntryRow
import com.himalayasoni.ledger.ui.home.dayGroups
import com.himalayasoni.ledger.ui.home.entryRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SearchUiState(
    val filters: Filters = Filters(),
    val count: String = "",
    val totals: String = "",
    val days: List<DayGroup> = emptyList(),     // newest/oldest: grouped by day
    val flat: List<EntryRow> = emptyList(),     // amount/name sorts: one list, with dates
    val empty: String? = null,
    val people: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)

private const val MAX_SHOWN = 300

/** Pure: entries + filters → what Search shows. */
fun buildSearchState(entries: List<com.himalayasoni.ledger.core.model.Entry>, f: Filters, today: LocalDate): SearchUiState {
    val r = entries.search(f, today)
    val (out, inn) = r.outAndIn()
    val byDate = f.sort.name == "NEWEST" || f.sort.name == "OLDEST"
    val shown = r.take(MAX_SHOWN)
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    return SearchUiState(
        filters = f,
        count = "${r.size} entr${if (r.size == 1) "y" else "ies"}",
        totals = "Out ${formatRupees(out)} · In ${formatRupees(inn)}",
        days = if (byDate) dayGroups(shown, today) else emptyList(),
        flat = if (byDate) emptyList() else shown.map { e ->
            entryRow(e).let { it.copy(subtitle = listOf(it.subtitle, e.date.format(dateFmt)).filter(String::isNotBlank).joinToString(" · ")) }
        },
        empty = when {
            r.isNotEmpty() -> null
            entries.isEmpty() -> "No entries yet. Tap + to add one."
            else -> "Nothing matches these filters."
        },
        people = entries.peopleIndex().values.map { it.name }.sortedBy { it.lowercase() },
        categories = entries.map { it.category }.filter { it.isNotBlank() }.distinct().sorted(),
    )
}

class SearchViewModel(visible: VisibleEntries) : ViewModel() {
    private val filters = MutableStateFlow(Filters())

    val state: StateFlow<SearchUiState> = combine(visible.entries, filters) { entries, f -> buildSearchState(entries, f, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun update(change: (Filters) -> Filters) = filters.update(change)
    fun clear() { filters.value = Filters() }
    /** "See all" on a person or category elsewhere can land here pre-filtered. */
    fun show(f: Filters) { filters.value = f }
}
