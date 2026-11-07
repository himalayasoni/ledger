package com.himalayasoni.ledger.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.himalayasoni.ledger.core.domain.OldLoanIssue
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.ui.add.AddRequest
import com.himalayasoni.ledger.ui.components.BackLink
import com.himalayasoni.ledger.ui.components.DaySection
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.PageHeader
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.theme.Bricolage
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import com.himalayasoni.ledger.ui.theme.categoryColor

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 140.dp)

/** The People tab (web: viewPeople). */
@Composable
fun PeopleTab(
    state: PeopleUiState?,
    onSettings: () -> Unit,
    onPerson: (String) -> Unit,
    onFix: (OldLoanIssue) -> Unit,
    onAdd: (AddRequest) -> Unit,
) {
    val c = LocalLedgerColors.current
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = listPadding) {
        item { PageHeader("People", onSettings) }
        if (state == null) return@LazyColumn
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Owed to you", state.owed, state.owedCount, c.income, Modifier.weight(1f))
                    Tile("You owe", state.owe, state.oweCount, c.spent, Modifier.weight(1f))
                }
                Tile("Spent on others", state.spentOnOthers, state.spentOnOthersNote, c.ink, Modifier.fillMaxWidth())
            }
        }
        if (state.issues.isNotEmpty()) item { IssuesCard(state.issues, onFix) }
        if (state.rows.isEmpty()) item { NoPeople(onAdd) }
        else item {
            LedgerCard(Modifier.padding(top = 14.dp)) {
                Column {
                    state.rows.forEachIndexed { i, r ->
                        if (i > 0) Hairline()
                        PersonRow(r) { onPerson(r.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, note: String, color: Color, modifier: Modifier) {
    val c = LocalLedgerColors.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = LedgerType.body, color = c.ink2)
        Text(value, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.02).em, color = color,
            style = LedgerType.amount, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
        Text(note, style = LedgerType.small, color = c.ink2)
    }
}

@Composable
private fun PersonRow(r: PersonRowUi, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(r.name, r.initial)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(r.name, fontWeight = FontWeight.SemiBold, style = LedgerType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.meta, style = LedgerType.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        r.pill?.let { Pill(it, r.pillKind) }
    }
}

@Composable
fun Avatar(name: String, initial: String, size: Int = 40) {
    val c = LocalLedgerColors.current
    Box(Modifier.size(size.dp).clip(CircleShape).background(categoryColor(name.lowercase()).copy(alpha = 0.22f)), Alignment.Center) {
        Text(initial, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.42).sp, color = c.ink)
    }
}

@Composable
private fun Pill(text: String, kind: PillKind) {
    val c = LocalLedgerColors.current
    val (fg, bg) = when (kind) {
        PillKind.OWES_YOU -> c.income to c.income.copy(alpha = 0.14f)
        PillKind.YOU_OWE -> c.spent to c.spent.copy(alpha = 0.14f)
        PillKind.NEUTRAL -> c.ink2 to c.surface2
    }
    Text(
        text, color = fg, fontWeight = FontWeight.Bold, style = LedgerType.small.copy(fontFeatureSettings = "tnum"),
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** web: .ask. A soft marigold note with a question and buttons. */
@Composable
fun AskCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalLedgerColors.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun IssuesCard(issues: List<IssueUi>, onFix: (OldLoanIssue) -> Unit) {
    val c = LocalLedgerColors.current
    AskCard(Modifier.padding(top = 14.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(if (issues.size == 1) "Check this balance. " else "Check these balances. ") }
                append("A repayment here has no matching loan in Ledger. If the loan was from before you started using Ledger, tap Fix so it doesn't count.")
            },
            style = LedgerType.body.copy(fontSize = 14.sp), color = c.ink,
        )
        issues.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.label, style = LedgerType.body.copy(fontSize = 14.sp), color = c.ink, modifier = Modifier.weight(1f))
                ActionButton("Fix", primary = true) { onFix(i.issue) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoPeople(onAdd: (AddRequest) -> Unit) {
    val c = LocalLedgerColors.current
    LedgerCard(Modifier.padding(top = 14.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No one here yet", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink)
            Text(
                "Add names to an expense you shared, or log money you lent or borrowed. Each person shows up here with what's pending and what you've spent on them.",
                style = LedgerType.body, color = c.ink2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Log money I lent", primary = true) { onAdd(AddRequest(type = EntryType.LENT)) }
                ActionButton("Add a shared expense") { onAdd(AddRequest(type = EntryType.SPENT, withPeople = true)) }
            }
        }
    }
}

/** web: .btn / .btn.primary (inline, sized to its text). */
@Composable
fun ActionButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (primary) c.accent else c.surface2)
            .pressable(onClick = onClick).padding(horizontal = 16.dp),
        Alignment.Center,
    ) { Text(text, color = if (primary) c.accentInk else c.ink, fontWeight = FontWeight.Bold, style = LedgerType.body) }
}

/** One person's page (web: viewPerson). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonPage(
    state: PersonUiState?,
    onBack: () -> Unit,
    onAdd: (AddRequest) -> Unit,
    onOpen: (String) -> Unit,
    onSharesPaid: (String) -> Unit,
    onFix: (OldLoanIssue) -> Unit,
) {
    val c = LocalLedgerColors.current
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = listPadding) {
        item { BackLink("People", onBack) }
        if (state == null) return@LazyColumn
        item {
            LedgerCard(radius = 22.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.name, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.02).em, color = c.ink)
                    Text(
                        buildAnnotatedString {
                            append(state.balanceText)
                            state.balanceAmount?.let {
                                append(" ")
                                withStyle(SpanStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                                    color = if (state.balanceKind == BalanceKind.OWES_YOU) c.income else c.spent)) { append(it) }
                            }
                        },
                        style = LedgerType.body.copy(fontSize = 17.sp), color = c.ink,
                    )
                    state.treatLine?.let { Text(it, style = LedgerType.body, color = c.ink2) }
                    state.issue?.let { i ->
                        AskCard(Modifier.padding(top = 4.dp)) {
                            Text(i.explain(), style = LedgerType.body.copy(fontSize = 14.sp), color = c.ink)
                            ActionButton("It was from before. Fix it", primary = true) { onFix(i.issue) }
                        }
                    }
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.canMarkSharesPaid) ActionButton("${state.name} paid their share", primary = true) { onSharesPaid(state.name) }
                        if (state.balance > 0) ActionButton("Got it back", primary = true) {
                            onAdd(AddRequest(type = EntryType.GOT_BACK, person = state.name, amount = state.balance))
                        }
                        if (state.balance < 0) ActionButton("I paid back", primary = true) {
                            onAdd(AddRequest(type = EntryType.PAID_BACK, person = state.name, amount = -state.balance))
                        }
                        ActionButton("Spent with") { onAdd(AddRequest(type = EntryType.SPENT, person = state.name)) }
                        ActionButton("Lend") { onAdd(AddRequest(type = EntryType.LENT, person = state.name)) }
                        ActionButton("Borrow") { onAdd(AddRequest(type = EntryType.BORROWED, person = state.name)) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text("History", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.ink, modifier = Modifier.weight(1f))
                Text("${state.historyCount} entries", style = LedgerType.small, color = c.ink2)
            }
        }
        items(state.history, key = { it.label }) { DaySection(it, onOpen) }
    }
}
