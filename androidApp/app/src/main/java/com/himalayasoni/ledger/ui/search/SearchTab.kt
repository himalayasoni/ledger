package com.himalayasoni.ledger.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.himalayasoni.ledger.core.domain.DateRange
import com.himalayasoni.ledger.core.domain.Filters
import com.himalayasoni.ledger.core.domain.SortBy
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.PAYMENT_MODES
import com.himalayasoni.ledger.ui.components.CheckRow
import com.himalayasoni.ledger.ui.components.DaySection
import com.himalayasoni.ledger.ui.components.EntryRowItem
import com.himalayasoni.ledger.ui.components.FieldLabel
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.components.LedgerIcons
import com.himalayasoni.ledger.ui.components.LedgerTextField
import com.himalayasoni.ledger.ui.components.PageHeader
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import com.himalayasoni.ledger.ui.theme.categoryColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Search tab (web: searchShell + refreshResults). Stateless: filters in, changes out. */
@Composable
fun SearchTab(
    state: SearchUiState,
    onSettings: () -> Unit,
    onFilters: ((Filters) -> Filters) -> Unit,
    onClear: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val c = LocalLedgerColors.current
    val f = state.filters
    var more by remember { mutableStateOf(f.hasMore) }
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 140.dp),
    ) {
        item { PageHeader("Search", onSettings) }
        item {
            LedgerTextField(
                f.query, { q -> onFilters { it.copy(query = q) } }, placeholder = "Name, note, category or amount",
                imeAction = ImeAction.Search,
                leading = { Icon(LedgerIcons.Search, null, tint = c.ink2, modifier = Modifier.size(18.dp)) },
            )
        }
        item {
            ChipRow(Modifier.padding(top = 10.dp)) {
                LedgerChip("All", f.types.isEmpty()) { onFilters { it.copy(types = emptySet()) } }
                EntryType.entries.forEach { t ->
                    LedgerChip(t.label, t in f.types) { onFilters { it.copy(types = if (t in it.types) it.types - t else it.types + t) } }
                }
            }
        }
        item {
            ChipRow(Modifier.padding(top = 8.dp)) {
                DateRange.entries.forEach { r -> LedgerChip(r.label, f.range == r) { onFilters { it.copy(range = r) } } }
            }
        }
        if (f.range == DateRange.CUSTOM) item {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton("From", f.from, Modifier.weight(1f)) { d -> onFilters { it.copy(from = d) } }
                DateButton("To", f.to, Modifier.weight(1f)) { d -> onFilters { it.copy(to = d) } }
            }
        }
        item {
            MoreFilters(open = more, onToggle = { more = !more }, f = f, people = state.people, categories = state.categories, onFilters = onFilters)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.count, style = LedgerType.title, color = c.ink)
                Spacer(Modifier.weight(1f))
                Text(state.totals, style = LedgerType.small.copy(fontFeatureSettings = "tnum"), color = c.ink2)
            }
            if (!f.isDefault) Text(
                "Clear filters", style = LedgerType.small, fontWeight = FontWeight.Bold, color = c.ink,
                textDecoration = TextDecoration.Underline, modifier = Modifier.padding(start = 2.dp, top = 4.dp).pressable(onClick = onClear),
            )
        }
        state.empty?.let { msg ->
            item { Text(msg, style = LedgerType.body, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp)) }
        }
        items(state.days, key = { it.label }) { DaySection(it, onOpen) }
        if (state.flat.isNotEmpty()) item {
            LedgerCard(Modifier.padding(top = 12.dp)) {
                Column { state.flat.forEachIndexed { i, r -> if (i > 0) Hairline(); EntryRowItem(r) { onOpen(r.id) } } }
            }
        }
    }
}

@Composable
private fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun MoreFilters(
    open: Boolean, onToggle: () -> Unit, f: Filters, people: List<String>, categories: List<String>,
    onFilters: ((Filters) -> Filters) -> Unit,
) {
    val c = LocalLedgerColors.current
    LedgerCard(Modifier.padding(top = 12.dp), radius = 14.dp) {
        Column {
            Row(Modifier.fillMaxWidth().pressable(shrink = false, onClick = onToggle).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("More filters", fontWeight = FontWeight.Bold, style = LedgerType.body, color = c.ink, modifier = Modifier.weight(1f))
                Icon(if (open) LedgerIcons.ChevronUp else LedgerIcons.Plus, null, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (people.isNotEmpty()) Column {
                        FieldLabel("Person")
                        ChipRow {
                            LedgerChip("Anyone", f.person.isEmpty()) { onFilters { it.copy(person = "") } }
                            people.forEach { p -> LedgerChip(p, f.person.equals(p, true)) { onFilters { it.copy(person = if (it.person.equals(p, true)) "" else p) } } }
                        }
                    }
                    if (categories.isNotEmpty()) Column {
                        FieldLabel("Category")
                        ChipRow {
                            LedgerChip("Any", f.category.isEmpty()) { onFilters { it.copy(category = "") } }
                            categories.forEach { k -> LedgerChip(k, f.category == k, swatch = categoryColor(k)) { onFilters { it.copy(category = if (it.category == k) "" else k) } } }
                        }
                    }
                    // the boxes keep what you type ("1." while typing 1.5); the filter gets the number
                    var minText by remember(f.min == null) { mutableStateOf(f.min?.let(::plain).orEmpty()) }
                    var maxText by remember(f.max == null) { mutableStateOf(f.max?.let(::plain).orEmpty()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            FieldLabel("Min ₹")
                            LedgerTextField(minText, { v -> minText = v; onFilters { it.copy(min = v.toDoubleOrNull()) } }, "0", keyboardType = KeyboardType.Decimal)
                        }
                        Column(Modifier.weight(1f)) {
                            FieldLabel("Max ₹")
                            LedgerTextField(maxText, { v -> maxText = v; onFilters { it.copy(max = v.toDoubleOrNull()) } }, "Any", keyboardType = KeyboardType.Decimal)
                        }
                    }
                    Column {
                        FieldLabel("Paid with")
                        ChipRow {
                            LedgerChip("Any", f.mode.isEmpty()) { onFilters { it.copy(mode = "") } }
                            PAYMENT_MODES.forEach { m -> LedgerChip(m, f.mode == m) { onFilters { it.copy(mode = if (it.mode == m) "" else m) } } }
                        }
                    }
                    Column {
                        FieldLabel("Sort")
                        ChipRow { SortBy.entries.forEach { s -> LedgerChip(s.label, f.sort == s) { onFilters { it.copy(sort = s) } } } }
                    }
                    CheckRow("Only entries with a comment", f.onlyWithNote) { v -> onFilters { it.copy(onlyWithNote = v) } }
                }
            }
        }
    }
}

private fun plain(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(label: String, date: LocalDate?, modifier: Modifier, onDate: (LocalDate?) -> Unit) {
    val c = LocalLedgerColors.current
    var picking by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        Text(
            date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) ?: "Any date",
            style = LedgerType.body, color = if (date == null) c.ink2 else c.ink,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(12.dp))
                .pressable(shrink = false) { picking = true }.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
    if (picking) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { onDate(st.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }); picking = false }) { Text("Done", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { onDate(null); picking = false }) { Text("Any date") } },
        ) { DatePicker(st) }
    }
}
