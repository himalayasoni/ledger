package com.himalayasoni.ledger.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.himalayasoni.ledger.ui.components.BackLink
import com.himalayasoni.ledger.ui.components.FieldLabel
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.IconTile
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.LedgerIcons
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.home.RowKind
import com.himalayasoni.ledger.ui.people.Avatar
import com.himalayasoni.ledger.ui.theme.Bricolage
import com.himalayasoni.ledger.ui.theme.BricolageDisplay
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import com.himalayasoni.ledger.ui.theme.categoryColor

/**
 * One entry, in full (web: the entry view). Stateless: [AppShell] passes the built [EntryDetail]
 * and handles the buttons. Tapping a row anywhere opens this; Edit opens the add sheet.
 */
@Composable
fun EntryPage(
    state: EntryDetail?,
    onBack: () -> Unit,
    onAction: (EntryAction) -> Unit,
    onCard: (String) -> Unit,
    backLabel: String = "Back",
) {
    val c = LocalLedgerColors.current
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackLink(backLabel, onBack) }
        if (state == null) return@LazyColumn
        item { Hero(state, onAction) }
        state.moneyBack?.let { m -> item { MoneyBackCard(m) } }
        item { FactsCard(state.facts, onCard) }
        if (state.shares.isNotEmpty()) item { SharesCard(state.sharesTitle.orEmpty(), state.shares) }
        if (state.note.isNotBlank()) item {
            LedgerCard(radius = 18.dp) {
                Column(Modifier.padding(16.dp)) {
                    FieldLabel("Comment")
                    Text("“${state.note}”", style = LedgerType.body, fontStyle = FontStyle.Italic, color = c.ink)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(s: EntryDetail, onAction: (EntryAction) -> Unit) {
    val c = LocalLedgerColors.current
    val tint = kindTint(s.kind, s.category)
    LedgerCard(radius = 22.dp) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (s.kind) {
                    RowKind.LOAN_OUT -> IconTile(tint, icon = LedgerIcons.Out, size = 42.dp)
                    RowKind.LOAN_IN -> IconTile(tint, icon = LedgerIcons.In, size = 42.dp)
                    RowKind.CARD_BILL -> IconTile(tint, icon = LedgerIcons.Card, size = 42.dp)
                    else -> IconTile(tint, letter = s.initial, size = 42.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(s.typeLabel.uppercase(), style = LedgerType.label, color = c.ink2)
                    Text(s.title, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, color = c.ink)
                }
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    s.amount, fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 46.sp,
                    letterSpacing = (-0.03).em, style = LedgerType.body.copy(fontFeatureSettings = "tnum"),
                    color = when (s.kind) { RowKind.IN -> c.income; RowKind.LOAN_IN, RowKind.LOAN_OUT -> c.loan; else -> c.ink },
                )
            }
            s.paidLine?.let { Text(it, style = LedgerType.body, color = c.ink2) }
            FlowRow(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                s.actions.forEach { a -> PageButton(a) { onAction(a.action) } }
            }
        }
    }
}

/** The page's buttons: marigold for the main one, grey for the rest, red text for Delete. */
@Composable
private fun PageButton(a: ActionUi, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    val (bg, fg) = when {
        a.primary -> c.accent to c.accentInk
        a.action == EntryAction.DELETE -> c.spent.copy(alpha = 0.12f) to c.spent
        else -> c.surface2 to c.ink
    }
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(bg).pressable(onClick = onClick).padding(horizontal = 16.dp),
        Alignment.Center,
    ) { Text(a.label, color = fg, fontWeight = FontWeight.Bold, style = LedgerType.body) }
}

@Composable
private fun MoneyBackCard(m: MoneyBackUi) {
    val c = LocalLedgerColors.current
    val (pillBg, pillFg) = if (m.awaited) c.accentSoft to c.ink else c.income.copy(alpha = 0.14f) to c.income
    LedgerCard(radius = 18.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink, modifier = Modifier.weight(1f))
                Text(
                    m.status.uppercase(), style = LedgerType.label.copy(fontSize = 11.sp), color = pillFg,
                    modifier = Modifier.clip(CircleShape).background(pillBg).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text(m.amount, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = c.ink,
                style = LedgerType.body.copy(fontFeatureSettings = "tnum"))
            m.lines.forEach { Text(it, style = LedgerType.small, color = c.ink2) }
        }
    }
}

@Composable
private fun FactsCard(facts: List<Fact>, onCard: (String) -> Unit) {
    val c = LocalLedgerColors.current
    LedgerCard(radius = 18.dp) {
        Column {
            facts.forEachIndexed { i, f ->
                if (i > 0) Hairline()
                Row(
                    Modifier.fillMaxWidth()
                        .then(f.card?.let { card -> Modifier.pressable(shrink = false) { onCard(card) } } ?: Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(f.label, style = LedgerType.body, color = c.ink2, modifier = Modifier.width(118.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        f.swatch?.let { Box(Modifier.size(9.dp).clip(CircleShape).background(categoryColor(it))); Spacer(Modifier.width(7.dp)) }
                        Text(f.value, style = LedgerType.body, fontWeight = FontWeight.SemiBold, color = c.ink, textAlign = TextAlign.End)
                        if (f.card != null) Icon(LedgerIcons.ChevronRight, "Open card", tint = c.ink2, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SharesCard(title: String, shares: List<ShareLine>) {
    val c = LocalLedgerColors.current
    LedgerCard(radius = 18.dp) {
        Column {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                FieldLabel("Shared")
                Text(title, style = LedgerType.body, fontWeight = FontWeight.SemiBold, color = c.ink)
            }
            shares.forEach { sh ->
                Hairline()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(sh.name, sh.name.take(1).uppercase(), size = 32)
                    Text(sh.name, style = LedgerType.body, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(sh.share, style = LedgerType.amount, color = c.ink)
                        Text(sh.status, style = LedgerType.small, fontWeight = FontWeight.SemiBold, color = if (sh.settled) c.income else c.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun kindTint(kind: RowKind, category: String): Color {
    val c = LocalLedgerColors.current
    return when (kind) {
        RowKind.LOAN_OUT, RowKind.LOAN_IN -> c.loan
        RowKind.IN -> c.income
        RowKind.OUT -> categoryColor(category)
        RowKind.CARD_BILL -> c.ink2
    }
}
