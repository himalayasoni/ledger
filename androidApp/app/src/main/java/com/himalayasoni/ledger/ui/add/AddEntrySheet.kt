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
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val context = androidx.compose.ui.platform.LocalContext.current

    // Voice: Android's own speech screen (Google voice typing). No microphone permission needed,
    // and nothing is recorded by Ledger; we only receive the text.
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::applySaid)
    }
    val listen: () -> Unit = {
        focus.clearFocus(); keyboard?.hide()
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say it like: lent 200 to Ashish")
        try { speech.launch(intent) } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "Voice typing isn't available on this phone", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // start fresh, then wait for "saved" or "deleted" and close (one effect, so the order is guaranteed)
    LaunchedEffect(request) {
        viewModel.start(request)
        if (request.listen) listen()
        val msg = viewModel.state.mapNotNull { it.done }.first()
        sheetState.hide(); onSaved(msg); onDismiss()
    }

    fun close() { scope.launch { sheetState.hide(); onDismiss() } }
    fun showKeypad() { focus.clearFocus(); keyboard?.hide(); viewModel.setKeypad(true) }
    var keypadBeforeTyping by remember { mutableStateOf(true) }
    val onFocus: (Boolean) -> Unit = { f ->
        if (f && !typing) { keypadBeforeTyping = s.keypadOn; viewModel.setKeypad(false) }
        typing = f
    }
    // When the phone keyboard goes away (Back, or its own hide key), leave the text box and bring
    // Ledger's keypad back the way it was. Watches the keyboard closing, not just "not open",
    // because a box gets focus a moment before the keyboard appears.
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible && typing) {
            focus.clearFocus()
            typing = false
            viewModel.setKeypad(keypadBeforeTyping)
        }
        imeWasVisible = imeVisible
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(4.dp)).background(c.line)) },
        contentWindowInsets = { WindowInsets(0) },
        tonalElevation = 0.dp,
    ) {
        // Back lowers the keypad first (like a phone keyboard); a second Back closes the sheet
        androidx.activity.compose.BackHandler(enabled = s.keypadOn && !typing) { viewModel.setKeypad(false) }
        AddEntryContent(
            s, viewModel, typing,
            onFocus = onFocus, onClose = ::close, onShowKeypad = ::showKeypad, onMic = listen,
            modifier = Modifier.fillMaxHeight(0.94f).windowInsetsPadding(WindowInsets.navigationBars).imePadding()
                .keepSheetOpen(),
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
    onMic: () -> Unit = {},
) {
    val c = LocalLedgerColors.current
    val saveLabel = when {
        s.editing != null -> "Save changes"
        s.amount > 0 -> "Save ₹" + amountDisplay(s.amountText)
        else -> "Save"
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        // ---- fixed top: kept small so the form gets the room ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.editing != null) "Edit entry" else "New entry", style = LedgerType.title, color = c.ink, modifier = Modifier.weight(1f))
            if (s.editing == null) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.deep).pressable(onClick = onMic),
                    Alignment.Center,
                ) { Icon(LedgerIcons.Mic, "Speak an entry", tint = c.accent, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(8.dp))
            }
            SquareIconButton(LedgerIcons.Close, "Close") { onClose() }
        }
        s.heard?.let { Heard(it, s.heardNote) }
        // while the phone keyboard is up, the type buttons step aside and the amount shrinks to one line
        if (!typing) TypeGrid(s.type, actions::onType)
        AmountDisplay(s.amountText, s.type, compact = typing, onTap = onShowKeypad)

        // ---- scrolling middle ----
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s.editing == null) SayBar(actions, onFocus)
            Details(s, actions, onFocus)
        }

        // ---- fixed bottom: the keypad carries its own Save key; otherwise one Save button ----
        s.error?.let {
            Text(it, color = c.spent, fontWeight = FontWeight.SemiBold, style = LedgerType.small, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (s.keypadOn && !typing) {
            Keypad(actions::onKey, onHide = { actions.setKeypad(false) }, onSave = onSave)
        } else {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!typing) {
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).pressable(onClick = onShowKeypad),
                        Alignment.Center,
                    ) { Text("123", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink) }
                    Spacer(Modifier.width(8.dp))
                }
                PrimaryButton(saveLabel, Modifier.weight(1f)) { onSave() }
            }
        }
    }
}


/**
 * Like the web: scrolling or dragging inside the sheet never pulls it closed. Leftover scroll
 * from the details list is absorbed here instead of reaching the sheet, and drags on the fixed
 * parts (amount, keypad) are claimed here too. Only the grab handle at the top moves the sheet.
 */
private fun Modifier.keepSheetOpen(): Modifier = this
    .nestedScroll(object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) = available
        override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) = available
    })
    .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> change.consume() } }

/* ---------- say bar (web: .say) ---------- */

@Composable
private fun SayBar(actions: AddEntryActions, onFocus: (Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column {
        FieldLabel("Type it in one line")
        LedgerTextField(
            text, { text = it }, placeholder = "lent 200 to Ashish · chai 20 cash",
            imeAction = ImeAction.Go, onDone = { actions.applySaid(text); text = "" },
            modifier = Modifier.onFocusChanged { onFocus(it.isFocused) },
        )
    }
}

/** "Heard “lent 200 to Ashish”. Check it and tap Save." */
@Composable
private fun Heard(heard: String, note: String?) {
    val c = LocalLedgerColors.current
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append("Heard ")
            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append("“$heard”") }
            note?.let { append(if (it.startsWith("Check")) ". $it" else " · $it") }
        },
        style = LedgerType.small, color = c.ink2,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/* ---------- type buttons (web: .types / .tbtn) ---------- */

@Composable
private fun TypeGrid(selected: EntryType, onType: (EntryType) -> Unit) {
    // 7 types in two rows of four: compact labels only; the selected type's meaning shows under the amount
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EntryType.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t -> TypeButton(t, t == selected, Modifier.weight(1f)) { onType(t) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TypeButton(t: EntryType, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(11.dp))
            .background(if (on) c.ink else c.surface)
            .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(11.dp))
            .pressable(shrink = false, onClick = onClick),
        Alignment.Center,
    ) {
        Text(t.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) c.bg else c.ink, style = LedgerType.body, maxLines = 1)
    }
}

/* ---------- amount (web: .amount) ---------- */

@Composable
private fun AmountDisplay(text: String, type: EntryType, compact: Boolean, onTap: () -> Unit) {
    val c = LocalLedgerColors.current
    val zero = text.isEmpty()
    var bump by remember { mutableStateOf(false) }
    LaunchedEffect(text) { bump = true; kotlinx.coroutines.delay(90); bump = false }
    val scale by animateFloatAsState(if (bump && !zero) 1.04f else 1f, label = "bump")
    Column(
        Modifier.fillMaxWidth().pressable(onClick = onTap).padding(top = if (compact) 6.dp else 8.dp, bottom = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("₹", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 18.sp else 24.sp, color = c.ink2,
                modifier = Modifier.padding(bottom = if (compact) 2.dp else 5.dp, end = 4.dp))
            Text(
                amountDisplay(text), fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 26.sp else 44.sp, lineHeight = if (compact) 28.sp else 46.sp,
                letterSpacing = (-0.03).em, color = if (zero) c.ink2.copy(alpha = 0.45f) else c.ink,
                style = LedgerType.body.copy(fontFeatureSettings = "tnum"), modifier = Modifier.scale(scale),
            )
        }
        if (!compact) Text(type.caption, style = LedgerType.small, color = c.ink2)
    }
}

/* ---------- details ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(s: AddEntryState, vm: AddEntryActions, onFocus: (Boolean) -> Unit) {
    val c = LocalLedgerColors.current
    var addingCategory by remember { mutableStateOf(false) }

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

    if (s.type != EntryType.CARD_BILL) PersonField(s, vm, onFocus)
    if (s.type == EntryType.CARD_BILL) {
        CardPicker(s, vm, "Which card", onFocus)
        s.billHints[s.card.lowercase()]?.let { h ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accentSoft).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(h.text, style = LedgerType.small, color = c.ink, modifier = Modifier.weight(1f))
                LedgerChip("Use ₹" + groupIndian(h.amount.toLong())) { vm.useAmount(h.amount) }
            }
        }
    }

    if (s.type.hasCategory) Column {
        FieldLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.categories.forEach { cat -> LedgerChip(cat, s.category == cat, swatch = categoryColor(cat)) { vm.onCategory(cat) } }
            if (!addingCategory) LedgerChip("+ New") { addingCategory = true }
        }
        if (addingCategory) NewCategory(
            onAdd = { vm.addCategory(it); addingCategory = false },
            onCancel = { addingCategory = false },
            onFocus = onFocus,
        )
    }

    Column {
        FieldLabel("Date")
        DateChips(s.date, vm::onDate)
    }

    Column {
        FieldLabel(
            when {
                s.type == EntryType.CARD_BILL -> "Paid from"
                s.type.sign > 0 && s.type != EntryType.BORROWED -> "Received in"
                else -> "Paid with"
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PAYMENT_MODES.filter { s.type != EntryType.CARD_BILL || !it.equals("Card", true) }
                .forEach { m -> LedgerChip(m, s.mode == m) { vm.onMode(m) } }
        }
    }
    if (s.type == EntryType.SPENT && s.mode.equals("Card", true)) CardPicker(s, vm, "Which card (optional)", onFocus)

    if (s.type == EntryType.SPENT) RefundSection(s, vm, onFocus)

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

/** Your named cards as chips, plus "+ New card". Used for card spends, card bills and refunds to a card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardPicker(s: AddEntryState, vm: AddEntryActions, label: String, onFocus: (Boolean) -> Unit, selected: String = s.card, onPick: (String) -> Unit = vm::onCard) {
    var adding by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.cards.forEach { name -> LedgerChip(name, selected.equals(name, true)) { onPick(name) } }
            if (!adding) LedgerChip(if (s.cards.isEmpty()) "+ Name your card" else "+ New card") { adding = true }
        }
        if (adding) NewCategory(
            onAdd = { vm.onCard(it); onPick(it.trim()); adding = false }, onCancel = { adding = false }, onFocus = onFocus,
            hint = "Card name, e.g. HDFC",
        )
    }
}

/**
 * Refund on an expense (web: the same section). Off: one chip, "Add refund". On: full or partial,
 * then "Awaited" (confirmed, not here yet) or "Arrived" with the date and where it came back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefundSection(s: AddEntryState, vm: AddEntryActions, onFocus: (Boolean) -> Unit) {
    val c = LocalLedgerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FieldLabel("Refund")
        if (!s.refundOn) {
            LedgerChip("+ Add refund (cancelled or returned)") { vm.onRefund { it.copy(refundOn = true) } }
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerChip("Full", s.refundFull) { vm.onRefund { it.copy(refundFull = true) } }
            LedgerChip("Partial", !s.refundFull) { vm.onRefund { it.copy(refundFull = false) } }
            LedgerChip("Remove refund") { vm.onRefund { it.copy(refundOn = false, refundText = "", refundArrived = false) } }
        }
        if (!s.refundFull) LedgerTextField(
            s.refundText, { v -> vm.onRefund { it.copy(refundText = v.filter { ch -> ch.isDigit() || ch == '.' }.take(10)) } },
            placeholder = "How much is coming back, e.g. 1320",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = ImeAction.Done,
            modifier = Modifier.onFocusChanged { onFocus(it.isFocused) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerChip("Awaited", !s.refundArrived) { vm.onRefund { it.copy(refundArrived = false) } }
            LedgerChip("Arrived", s.refundArrived) { vm.onRefund { it.copy(refundArrived = true) } }
        }
        val back = if (s.refundFull) s.amount else s.refundText.toDoubleOrNull() ?: 0.0
        if (back > 0 && s.amount > 0) Text(
            "Counts as ${com.himalayasoni.ledger.core.money.formatRupees(maxOf(0.0, s.amount - back))} spent" +
                if (!s.refundArrived) " · ${com.himalayasoni.ledger.core.money.formatRupees(minOf(back, s.amount))} shows as awaited until it arrives" else "",
            style = LedgerType.small, color = c.ink2,
        )
        if (s.refundArrived) {
            Column {
                FieldLabel("Arrived on")
                DateChips(s.refundReceived) { d -> vm.onRefund { it.copy(refundReceived = d) } }
            }
            Column {
                FieldLabel("Came back to")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Card", "UPI", "Bank", "Cash").forEach { t -> LedgerChip(t, s.refundTo == t) { vm.onRefund { it.copy(refundTo = t) } } }
                }
            }
            if (s.refundTo == "Card") CardPicker(s, vm, "Which card", onFocus, selected = s.refundCard.ifBlank { s.card }) { name ->
                vm.onRefund { it.copy(refundCard = name) }
            }
        }
    }
}

/** "+ New" category: a small box that opens with the keyboard, like the web's new-category field. */
@Composable
private fun NewCategory(onAdd: (String) -> Unit, onCancel: () -> Unit, onFocus: (Boolean) -> Unit, hint: String = "New category, e.g. Pets") {
    var name by remember { mutableStateOf("") }
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LedgerTextField(
            name, { name = it.take(24) }, placeholder = hint,
            imeAction = ImeAction.Done, onDone = { if (name.isNotBlank()) onAdd(name) else onCancel() },
            modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { onFocus(it.isFocused) },
        )
        com.himalayasoni.ledger.ui.people.ActionButton("Add", primary = true) { if (name.isNotBlank()) onAdd(name) else onCancel() }
    }
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

/**
 * Ledger's keypad: digits on the left, and a column on the right with delete, hide, and a tall Save
 * key, so saving never needs a separate row (like a calculator's = key).
 */
@Composable
private fun Keypad(onKey: (String) -> Unit, onHide: () -> Unit, onSave: () -> Unit) {
    val c = LocalLedgerColors.current
    val keyH = 46.dp; val gap = 6.dp
    Row(Modifier.padding(top = 6.dp).height(keyH * 4 + gap * 3), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "00").chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { k -> Key(k, Modifier.weight(1f).height(keyH)) { onKey(k) } } }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
            Key("del", Modifier.fillMaxWidth().height(keyH)) { onKey("del") }
            Key("hide", Modifier.fillMaxWidth().height(keyH), onClick = onHide)
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(c.accent).pressable(onClick = onSave),
                Alignment.Center,
            ) { Text("Save", color = c.accentInk, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        }
    }
}

@Composable
private fun Key(k: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).pressable(onClick = onClick),
        Alignment.Center,
    ) {
        when (k) {
            "del" -> Icon(LedgerIcons.Backspace, "Delete digit", tint = c.ink, modifier = Modifier.size(22.dp))
            "hide" -> Icon(LedgerIcons.ChevronDown, "Hide keypad", tint = c.ink, modifier = Modifier.size(22.dp))
            else -> Text(k, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.ink)
        }
    }
}

/** "123456.5" → "1,23,456.5" while typing. */
private fun amountDisplay(text: String): String {
    if (text.isEmpty()) return "0"
    val whole = text.substringBefore('.').ifEmpty { "0" }.toLongOrNull() ?: 0
    return groupIndian(whole) + if (text.contains('.')) "." + text.substringAfter('.') else ""
}
