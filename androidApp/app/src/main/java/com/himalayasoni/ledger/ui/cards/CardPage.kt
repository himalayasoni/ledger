package com.himalayasoni.ledger.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.himalayasoni.ledger.ui.components.BackLink
import com.himalayasoni.ledger.ui.components.EntryRowItem
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.components.LedgerIcons
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.theme.Bricolage
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors

/** One credit card: what's due, its statement day, and each period's bill against what you logged. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardPage(
    state: CardUiState?,
    onBack: () -> Unit,
    onStatementDay: (Int?) -> Unit,
    onOpen: (String) -> Unit,
    onPayBill: () -> Unit,
) {
    val c = LocalLedgerColors.current
    var picking by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackLink("Home", onBack) }
        if (state == null) return@LazyColumn
        item {
            LedgerCard(radius = 22.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LedgerIcons.Card, null, tint = c.ink2, modifier = Modifier.size(22.dp))
                        Text("  ${state.name}", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.02).em, color = c.ink)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Due now ", style = LedgerType.body.copy(fontSize = 17.sp), color = c.ink)
                        Text(state.due, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c.ink)
                    }
                    state.notLogged?.let { Text(it, style = LedgerType.small, color = c.ink2) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.statementDay?.let { "Statement closes on the ${ordinal(it)}" } ?: "No statement date set",
                            style = LedgerType.body, color = c.ink2, modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (picking) "Done" else if (state.statementDay == null) "Set it" else "Change",
                            style = LedgerType.body, fontWeight = FontWeight.Bold, color = c.ink, textDecoration = TextDecoration.Underline,
                            modifier = Modifier.pressable { picking = !picking }.padding(4.dp),
                        )
                    }
                    if (picking) {
                        Text("Which day does this card's statement close? It's on your bill.", style = LedgerType.small, color = c.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..31).forEach { d -> LedgerChip("$d", state.statementDay == d) { onStatementDay(d); picking = false } }
                            LedgerChip("Not set", state.statementDay == null) { onStatementDay(null); picking = false }
                        }
                    } else if (state.statementDay == null) Text(
                        "Bills are compared with everything since the previous bill. Set the statement date to compare each bill with the exact statement.",
                        style = LedgerType.small, color = c.ink2,
                    )
                    Box(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.accent).pressable(onClick = onPayBill)
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    ) { Text("Log a bill payment", color = c.accentInk, fontWeight = FontWeight.Bold, style = LedgerType.body) }
                }
            }
        }
        if (state.cycles.isEmpty()) item { Text("Nothing on this card yet.", style = LedgerType.body, color = c.ink2) }
        itemsIndexed(state.cycles, key = { i, it -> "$i-${it.title}" }) { i, cy -> CycleCard(cy, startOpen = i == 0, onOpen) }
    }
}

@Composable
private fun CycleCard(cy: CycleUi, startOpen: Boolean, onOpen: (String) -> Unit) {
    val c = LocalLedgerColors.current
    var open by remember { mutableStateOf(startOpen) }
    val (fg, bg) = when (cy.tone) {
        VerdictTone.GOOD -> c.income to c.income.copy(alpha = 0.12f)
        VerdictTone.MORE -> c.spent to c.spent.copy(alpha = 0.12f)
        VerdictTone.LESS -> c.loan to c.loan.copy(alpha = 0.12f)
        VerdictTone.NEUTRAL -> c.ink2 to c.surface2
    }
    LedgerCard(radius = 18.dp) {
        Column {
            Column(Modifier.pressable(shrink = false) { open = !open }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cy.title, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink, modifier = Modifier.weight(1f))
                    Icon(if (open) LedgerIcons.ChevronUp else LedgerIcons.ChevronDown, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                }
                Text(cy.logged, style = LedgerType.body, color = c.ink)
                cy.refunds?.let { Text(it, style = LedgerType.body, color = c.ink) }
                cy.paid?.let { Text(it, style = LedgerType.body, fontWeight = FontWeight.SemiBold, color = c.ink) }
                Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
                    Text("  ${cy.verdict}", style = LedgerType.small.copy(fontWeight = FontWeight.SemiBold), color = fg)
                }
            }
            if (open && cy.rows.isNotEmpty()) {
                Hairline()
                cy.rows.forEachIndexed { i, r -> if (i > 0) Hairline(); EntryRowItem(r) { onOpen(r.id) } }
            }
        }
    }
}
