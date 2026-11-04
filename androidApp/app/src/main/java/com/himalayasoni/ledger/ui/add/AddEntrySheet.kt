package com.himalayasoni.ledger.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himalayasoni.ledger.core.model.DEFAULT_CATEGORIES
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.PAYMENT_MODES
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.core.money.groupIndian
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

/** The bottom sheet for adding an entry: type, amount keypad, details, save. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEntrySheet(onDismiss: () -> Unit, viewModel: AddEntryViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val c = LocalLedgerColors.current

    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(s.saved) { if (s.saved) { sheetState.hide(); onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New entry", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EntryType.entries.forEach { t ->
                    FilterChip(selected = s.type == t, onClick = { viewModel.onType(t) }, label = { Text(t.label) })
                }
            }

            Text(
                "₹" + amountDisplay(s.amountText),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (s.amount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = s.description, onValueChange = viewModel::onDescription,
                label = { Text("What was it?") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            if (s.type != EntryType.SPENT) {
                OutlinedTextField(
                    value = s.person, onValueChange = viewModel::onPerson,
                    label = { Text(personLabel(s.type)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!s.type.isDebt) {
                Label("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DEFAULT_CATEGORIES.forEach { cat ->
                        FilterChip(selected = s.category == cat, onClick = { viewModel.onCategory(cat) }, label = { Text(cat) })
                    }
                }
            }

            Label("Date")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val today = LocalDate.now()
                FilterChip(selected = s.date == today, onClick = { viewModel.onDate(today) }, label = { Text("Today") })
                FilterChip(selected = s.date == today.minusDays(1), onClick = { viewModel.onDate(today.minusDays(1)) }, label = { Text("Yesterday") })
            }

            Label("Paid with")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PAYMENT_MODES.forEach { m -> FilterChip(selected = s.mode == m, onClick = { viewModel.onMode(m) }, label = { Text(m) }) }
            }

            s.error?.let { Text(it, color = c.spent, fontWeight = FontWeight.SemiBold) }

            Keypad(onKey = viewModel::onKey)

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) {
                Text(if (s.amount > 0) "Save ${formatRupees(s.amount)}" else "Save", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "del"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    FilledTonalButton(
                        onClick = { onKey(k) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(if (k == "del") "⌫" else k, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun personLabel(t: EntryType) = when (t) {
    EntryType.RECEIVED -> "Received from (optional)"
    EntryType.LENT -> "Lent to"
    EntryType.BORROWED -> "Borrowed from"
    EntryType.GOT_BACK -> "Got back from"
    EntryType.PAID_BACK -> "Paid back to"
    EntryType.SPENT -> "Spent with"
}

/** "123456.5" → "1,23,456.5" while typing. */
private fun amountDisplay(text: String): String {
    if (text.isEmpty()) return "0"
    val whole = text.substringBefore('.').ifEmpty { "0" }.toLongOrNull() ?: 0
    return groupIndian(whole) + if (text.contains('.')) "." + text.substringAfter('.') else ""
}
