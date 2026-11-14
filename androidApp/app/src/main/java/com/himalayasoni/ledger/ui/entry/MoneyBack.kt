package com.himalayasoni.ledger.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.ui.add.DateChips
import com.himalayasoni.ledger.ui.add.NewCategory
import com.himalayasoni.ledger.ui.components.FieldLabel
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.components.LedgerIcons
import com.himalayasoni.ledger.ui.components.LedgerTextField
import com.himalayasoni.ledger.ui.components.PrimaryButton
import com.himalayasoni.ledger.ui.components.SquareIconButton
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import kotlinx.coroutines.launch

/** The bottom sheet around [MoneyBackContent]. Holds the form while it's open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyBackSheet(start: MoneyBackForm, onDismiss: () -> Unit, onSave: (MoneyBackForm) -> Unit, onRemove: () -> Unit) {
    val c = LocalLedgerColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var form by remember(start.entryId) { mutableStateOf(start) }
    fun close(then: () -> Unit) { then(); scope.launch { sheetState.hide(); onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = c.surface, scrimColor = c.scrim, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(4.dp)).background(c.line)) },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        MoneyBackContent(
            form, onChange = { form = it }, onClose = { close {} },
            onSave = { val p = form.problem(); if (p != null) form = form.copy(error = p) else close { onSave(form) } },
            onRemove = { close(onRemove) },
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).imePadding(),
        )
    }
}

/** The card's insides, top to bottom: From, How much, Status (and where it came back), what it counts as, Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyBackContent(
    f: MoneyBackForm,
    onChange: (MoneyBackForm) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalLedgerColors.current
    var addingName by remember { mutableStateOf(false) }
    val gaps = Arrangement.spacedBy(8.dp)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Money back", style = LedgerType.title, color = c.ink)
                Text("${f.title} · ${formatRupees(f.entryAmount)} paid", style = LedgerType.small, color = c.ink2, maxLines = 1)
            }
            SquareIconButton(LedgerIcons.Close, "Close", onClose)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                FieldLabel("From")
                FlowRow(horizontalArrangement = gaps, verticalArrangement = gaps) {
                    LedgerChip("Shop refund", f.from.isBlank()) { onChange(f.pickFrom("")) }
                    f.people.forEach { n -> LedgerChip(n, f.from.equals(n, true)) { onChange(f.pickFrom(n)) } }
                    val other = f.from.isNotBlank() && f.people.none { it.equals(f.from, true) }
                    if (other) LedgerChip(f.from, true) {}
                    if (!addingName) LedgerChip("+ Someone else") { addingName = true }
                }
                if (addingName) NewCategory(
                    onAdd = { n -> onChange(f.pickFrom(n)); addingName = false }, onCancel = { addingName = false },
                    onFocus = {}, hint = "Their name",
                )
                if (f.shared.isNotEmpty()) Text(
                    "Split this with ${f.shared.joinToString(", ")}? Use “paid their share” on their page instead.",
                    style = LedgerType.small, color = c.ink2, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column {
                FieldLabel("How much")
                FlowRow(horizontalArrangement = gaps, verticalArrangement = gaps) {
                    LedgerChip("Full", f.full, trailing = formatRupees(f.entryAmount)) { onChange(f.pickFull(true)) }
                    LedgerChip("Part", !f.full) { onChange(f.pickFull(false)) }
                }
                if (!f.full) LedgerTextField(
                    f.partText, { onChange(f.typePart(it)) }, placeholder = "How much, e.g. 1320",
                    keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column {
                FieldLabel("Status")
                FlowRow(horizontalArrangement = gaps, verticalArrangement = gaps) {
                    LedgerChip("Awaited", !f.arrived) { onChange(f.pickArrived(false)) }
                    LedgerChip("Arrived", f.arrived) { onChange(f.pickArrived(true)) }
                }
            }
            if (f.arrived) {
                Column {
                    FieldLabel("Arrived on")
                    DateChips(f.received) { onChange(f.pickReceived(it)) }
                }
                Column {
                    FieldLabel("Came back to")
                    FlowRow(horizontalArrangement = gaps, verticalArrangement = gaps) {
                        CAME_BACK_TO.forEach { t -> LedgerChip(t, f.to == t) { onChange(f.pickTo(t)) } }
                    }
                }
                if (f.to == "Card" && f.cards.isNotEmpty()) Column {
                    FieldLabel("Which card")
                    FlowRow(horizontalArrangement = gaps, verticalArrangement = gaps) {
                        f.cards.forEach { n -> LedgerChip(n, f.card.equals(n, true)) { onChange(f.pickCard(n)) } }
                    }
                }
            }
            Text(
                f.countsAs, style = LedgerType.small.copy(fontWeight = FontWeight.SemiBold), color = c.ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accentSoft).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        f.error?.let { Text(it, color = c.spent, fontWeight = FontWeight.SemiBold, style = LedgerType.small, modifier = Modifier.padding(bottom = 6.dp)) }
        PrimaryButton("Save", onClick = onSave)
        if (f.existing) Text(
            "Remove money back", color = c.spent, fontWeight = FontWeight.Bold, style = LedgerType.body, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).pressable(onClick = onRemove).padding(vertical = 10.dp),
        )
    }
}
