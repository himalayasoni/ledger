package com.himalayasoni.ledger.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.PAYMENT_MODES
import com.himalayasoni.ledger.core.money.groupIndian
import com.himalayasoni.ledger.ui.components.CheckRow
import com.himalayasoni.ledger.ui.components.FieldLabel
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.components.LedgerIcons
import com.himalayasoni.ledger.ui.components.LedgerTextField
import com.himalayasoni.ledger.ui.components.PrimaryButton
import com.himalayasoni.ledger.ui.components.SquareIconButton
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.home.caption
import com.himalayasoni.ledger.ui.home.personLabel
import com.himalayasoni.ledger.ui.theme.Bricolage
import com.himalayasoni.ledger.ui.theme.BricolageDisplay
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import com.himalayasoni.ledger.ui.theme.categoryColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The add / edit sheet. Same layout as the web app:
 * fixed top (title, type buttons, amount), scrolling middle (details), fixed bottom (keypad, Save).
 * The keypad hides itself while you type in a text box, and comes back when you tap the amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(
    request: AddRequest,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: AddEntryViewModel = koinViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val c = LocalLedgerColors.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var typing by remember { mutableStateOf(false) }

    // start fresh, then wait for "saved" or "deleted" and close (one effect, so the order is guaranteed)
    LaunchedEffect(request) {
        viewModel.start(request)
        val msg = viewModel.state.mapNotNull { it.done }.first()
        sheetState.hide(); onSaved(msg); onDismiss()
    }

    fun close() { scope.launch { sheetState.hide(); onDismiss() } }
    fun showKeypad() { focus.clearFocus(); keyboard?.hide(); viewModel.setKeypad(true) }
    val onFocus: (Boolean) -> Unit = { f -> typing = f; if (f) viewModel.setKeypad(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(4.dp)).background(c.line)) },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        AddEntryContent(
            s, viewModel, typing,
            onFocus = onFocus, onClose = ::close, onShowKeypad = ::showKeypad,
            modifier = Modifier.fillMaxHeight(0.94f).windowInsetsPadding(WindowInsets.navigationBars).imePadding(),
            onSave = { focus.clearFocus(); viewModel.save() },
        )
    }
}

/** The sheet's insides, with no ViewModel or window: what screenshot tests render. */
@Composable
internal fun AddEntryContent(
    s: AddEntryState,
    actions: AddEntryActions,
    typing: Boolean,
    onFocus: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShowKeypad: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalLedgerColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        // ---- fixed top ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.editing != null) "Edit entry" else "New entry", style = LedgerType.title, color = c.ink, modifier = Modifier.weight(1f))
            SquareIconButton(LedgerIcons.Close, "Close") { onClose() }
        }
        TypeGrid(s.type, actions::onType)
        AmountDisplay(s.amountText, onTap = onShowKeypad)

        // ---- scrolling middle ----
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Details(s, actions, onFocus)
        }

        // ---- fixed bottom ----
        if (!typing) PadBar(s.keypadOn) { if (s.keypadOn) actions.setKeypad(false) else onShowKeypad() }
        AnimatedVisibility(s.keypadOn && !typing, enter = expandVertically(), exit = shrinkVertically()) {
            Keypad(actions::onKey)
        }
        s.error?.let {
            Text(it, color = c.spent, fontWeight = FontWeight.SemiBold, style = LedgerType.small, modifier = Modifier.padding(top = 8.dp))
        }
        PrimaryButton(
            when {
                s.editing != null -> "Save changes"
                s.amount > 0 -> "Save ₹" + amountDisplay(s.amountText)
                else -> "Save"
            },
            Modifier.padding(top = 8.dp),
        ) { onSave() }
    }
}


/* ---------- type buttons (web: .types / .tbtn) ---------- */

@Composable
private fun TypeGrid(selected: EntryType, onType: (EntryType) -> Unit) {
    Column(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EntryType.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t -> TypeButton(t, t == selected, Modifier.weight(1f)) { onType(t) } }
            }
        }
    }
}

@Composable
private fun TypeButton(t: EntryType, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (on) c.ink else c.surface)
            .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(t.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) c.bg else c.ink, style = LedgerType.body, maxLines = 1)
        Text(
            t.caption, fontSize = 11.sp, color = if (on) c.bg.copy(alpha = 0.7f) else c.ink2, style = LedgerType.small,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ---------- amount (web: .amount) ---------- */

@Composable
private fun AmountDisplay(text: String, onTap: () -> Unit) {
    val c = LocalLedgerColors.current
    val zero = text.isEmpty()
    var bump by remember { mutableStateOf(false) }
    LaunchedEffect(text) { bump = true; kotlinx.coroutines.delay(90); bump = false }
    val scale by animateFloatAsState(if (bump && !zero) 1.04f else 1f, label = "bump")
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onTap).padding(top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("₹", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = c.ink2, modifier = Modifier.padding(bottom = 6.dp, end = 4.dp))
        Text(
            amountDisplay(text), fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 54.sp,
            letterSpacing = (-0.03).em, color = if (zero) c.ink2.copy(alpha = 0.45f) else c.ink,
            style = LedgerType.body.copy(fontFeatureSettings = "tnum"), modifier = Modifier.scale(scale),
        )
    }
}

/* ---------- details ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(s: AddEntryState, vm: AddEntryActions, onFocus: (Boolean) -> Unit) {
    val c = LocalLedgerColors.current

    if (s.usuals.isNotEmpty()) Column {
        FieldLabel("Your usuals · one tap")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.usuals.forEach { u -> LedgerChip(u.description, soft = true, trailing = "₹" + groupIndian(u.amount.toLong())) { vm.pickUsual(u) } }
        }
    }

    Column {
        FieldLabel("What was it?")
        LedgerTextField(
            s.description, vm::onDescription,
            placeholder = when (s.type) {
                EntryType.SPENT -> "Chai, Uber, Rent, Movie tickets"
                EntryType.RECEIVED -> "Salary, Refund, Freelance"
                else -> "Optional, e.g. Goa trip"
            },
            modifier = Modifier.onFocusChanged { onFocus(it.isFocused) },
        )
        if (s.descriptionMatches.isNotEmpty()) Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.descriptionMatches.forEach { u ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface2)
                        .pressable { vm.pickUsual(u) }.padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(u.description, style = LedgerType.body.copy(fontSize = 14.sp), color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("₹" + groupIndian(u.amount.toLong()) + (if (u.category.isNotBlank()) " · ${u.category}" else ""), style = LedgerType.body.copy(fontSize = 14.sp), color = c.ink2)
                }
            }
        }
    }

    PersonField(s, vm, onFocus)

    if (!s.type.isDebt) Column {
        FieldLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.categories.forEach { cat -> LedgerChip(cat, s.category == cat, swatch = categoryColor(cat)) { vm.onCategory(cat) } }
        }
    }

    Column {
        FieldLabel("Date")
        DateChips(s.date, vm::onDate)
    }

    Column {
        FieldLabel(if (s.type.sign > 0 && s.type != EntryType.BORROWED) "Received in" else "Paid with")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PAYMENT_MODES.forEach { m -> LedgerChip(m, s.mode == m) { vm.onMode(m) } }
        }
    }

    Column {
        FieldLabel("Comment")
        LedgerTextField(
            s.note, vm::onNote, placeholder = "Anything you want to remember", singleLine = false, minLines = 2,
            imeAction = ImeAction.Default, modifier = Modifier.onFocusChanged { onFocus(it.isFocused) },
        )
    }

    if (s.editing != null) Text(
        "Delete this entry", color = c.spent, fontWeight = FontWeight.Bold, style = LedgerType.body,
        modifier = Modifier.pressable(onClick = vm::delete).padding(vertical = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonField(s: AddEntryState, vm: AddEntryActions, onFocus: (Boolean) -> Unit) {
    val c = LocalLedgerColors.current
    val shared = s.type == EntryType.SPENT
    Column {
        FieldLabel(
            when (s.type) {
                EntryType.SPENT -> "Spent with (optional)"
                EntryType.RECEIVED -> "Received from (optional)"
                else -> s.type.personLabel
            },
        )
        if (shared && s.withPeople.isNotEmpty()) FlowRow(
            Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            s.withPeople.forEach { n -> LedgerChip(n, selected = true, trailing = "×") { vm.removePerson(n) } }
        }
        LedgerTextField(
            s.person, vm::onPerson,
            placeholder = if (shared) (if (s.withPeople.isEmpty()) "Add names, e.g. Rahul" else "Add another name") else "Name",
            imeAction = ImeAction.Done, onDone = { vm.addPerson(s.person) },
            modifier = Modifier.onFocusChanged { onFocus(it.isFocused); if (!it.isFocused && shared) vm.addPerson(s.person) },
        )
        if (shared && s.withPeople.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            CheckRow("I had a share too", s.includesMe, vm::onIncludesMe)
            s.splitHint?.let { Text(it, style = LedgerType.small, color = c.ink2, modifier = Modifier.padding(top = 2.dp)) }
        }
        val sugg = s.personSuggestions
        if (sugg.isNotEmpty() && (shared || s.person.isBlank())) FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sugg.forEach { n -> LedgerChip("+ $n") { vm.addPerson(n) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateChips(date: LocalDate, onDate: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var picking by remember { mutableStateOf(false) }
    val other = date != today && date != today.minusDays(1)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LedgerChip("Today", date == today) { onDate(today) }
        LedgerChip("Yesterday", date == today.minusDays(1)) { onDate(today.minusDays(1)) }
        LedgerChip(if (other) date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) else "Pick a date", other) { picking = true }
    }
    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Done", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(pickerState) }
    }
}

/* ---------- keypad (web: .padbar, .pad) ---------- */

@Composable
private fun PadBar(on: Boolean, onToggle: () -> Unit) {
    val c = LocalLedgerColors.current
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.pressable(onClick = onToggle).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (on) "Hide keypad" else "Show keypad", fontWeight = FontWeight.Bold, style = LedgerType.small, color = c.ink)
                Spacer(Modifier.width(6.dp))
                Icon(if (on) LedgerIcons.ChevronDown else LedgerIcons.ChevronUp, null, tint = c.ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "del")
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k -> Key(k, Modifier.weight(1f)) { onKey(k) } }
            }
        }
    }
}

@Composable
private fun Key(k: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2).pressable(onClick = onClick),
        Alignment.Center,
    ) {
        if (k == "del") Icon(LedgerIcons.Backspace, "Delete digit", tint = c.ink, modifier = Modifier.size(22.dp))
        else Text(k, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.ink)
    }
}

/** "123456.5" → "1,23,456.5" while typing. */
private fun amountDisplay(text: String): String {
    if (text.isEmpty()) return "0"
    val whole = text.substringBefore('.').ifEmpty { "0" }.toLongOrNull() ?: 0
    return groupIndian(whole) + if (text.contains('.')) "." + text.substringAfter('.') else ""
}
