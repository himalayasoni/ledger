package com.himalayasoni.ledger.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.data.sync.SyncStatus
import com.himalayasoni.ledger.ui.account.AccountSheet
import com.himalayasoni.ledger.ui.account.AccountUiState
import com.himalayasoni.ledger.ui.account.AccountViewModel
import com.himalayasoni.ledger.ui.add.AddEntrySheet
import com.himalayasoni.ledger.ui.add.AddRequest
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.SectionHeader
import com.himalayasoni.ledger.ui.components.DaySection
import com.himalayasoni.ledger.ui.components.IconTile
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.LedgerIcons
import com.himalayasoni.ledger.ui.components.PrimaryButton
import com.himalayasoni.ledger.ui.components.SquareIconButton
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.theme.Bricolage
import com.himalayasoni.ledger.ui.theme.BricolageDisplay
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import com.himalayasoni.ledger.ui.theme.categoryColor
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/** The Home tab. Stateless: the app shell passes the state and what to do on taps. */
@Composable
fun HomeTab(
    state: HomeUiState,
    account: AccountUiState,
    onSettings: () -> Unit,
    onAdd: (AddRequest) -> Unit,
    onOpen: (String) -> Unit,
    onSoon: (String) -> Unit,
    onSeeAll: () -> Unit,
    onCard: (String) -> Unit = {},
) {
    val s = state as? HomeUiState.Ready ?: return Box(Modifier.fillMaxSize()) {
        Text("Opening Ledger…", color = LocalLedgerColors.current.ink2, style = LedgerType.body, modifier = Modifier.align(Alignment.Center))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 140.dp),
    ) {
        item { TopBar(s.dateLine, account, onSettings) }
        if (s.isEmpty) {
            item { EmptyState(onAdd = { onAdd(AddRequest()) }) }
            return@LazyColumn
        }
        item { Hero(s) }
        s.inFlight?.let { f -> item { InFlightCard(f, onSeeAll, onCard) } }
        item { StreakCard(s) }
        item {
            QuickActions(
                onSay = { onAdd(AddRequest(listen = true)) },
                onWith = { onAdd(AddRequest(type = EntryType.SPENT, withPeople = true)) },
                onLent = { onAdd(AddRequest(type = EntryType.LENT)) },
                onBorrowed = { onAdd(AddRequest(type = EntryType.BORROWED)) },
            )
        }
        item { SectionHeader("Recent", "See all ${s.totalCount}") { onSeeAll() } }
        items(s.days, key = { it.label }) { day -> DaySection(day, onOpen) }
    }
}

/* ---------- header ---------- */

@Composable
private fun TopBar(dateLine: String, account: AccountUiState, onSettings: () -> Unit) {
    val c = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(c.accent), Alignment.Center) {
                    Text("₹", color = c.accentInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Bricolage)
                }
                Spacer(Modifier.width(8.dp))
                Text("Ledger", style = LedgerType.brand, color = c.ink)
            }
            Row(Modifier.padding(top = 1.dp).pressable(onClick = onSettings), verticalAlignment = Alignment.CenterVertically) {
                Text("$dateLine · ", style = LedgerType.small, color = c.ink2)
                val dot = when (account.status) {
                    is SyncStatus.Synced -> c.income
                    is SyncStatus.Problem -> c.spent
                    SyncStatus.Syncing -> c.accent
                    else -> c.ink2
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(account.statusLine, style = LedgerType.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SquareIconButton(LedgerIcons.Settings, "Settings and backup", onSettings)
    }
}

/* ---------- hero: this month ---------- */

@Composable
private fun Hero(s: HomeUiState.Ready) {
    val c = LocalLedgerColors.current
    Box(
        Modifier.fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = c.deep, spotColor = c.deep)
            .clip(RoundedCornerShape(22.dp)).background(c.deep),
    ) {
        // the faint rings in the corner (web: .hero::after)
        Canvas(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-40).dp).size(160.dp)) {
            val step = 11.dp.toPx()
            var r = step
            while (r < size.minDimension / 2) {
                drawCircle(Color.White.copy(alpha = 0.05f), radius = r, style = Stroke(1.dp.toPx()))
                r += step
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
            Text(
                "Spent in ${s.monthName}".uppercase(), color = c.deepInk2, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em, style = LedgerType.small,
            )
            Text(
                s.spent, color = c.deepInk, fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold,
                fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em, modifier = Modifier.padding(top = 4.dp),
                style = LedgerType.body.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                buildAnnotatedString {
                    s.comparisonBold?.let { withStyle(SpanStyle(color = c.deepInk, fontWeight = FontWeight.SemiBold)) { append(it) } }
                    append(s.comparisonRest)
                },
                color = c.deepInk2, style = LedgerType.body.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mini("Received", s.received, Modifier.weight(1f))
                Mini("Owed to you", s.owedToYou, Modifier.weight(1f))
                Mini("You owe", s.youOwe, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier) {
    val c = LocalLedgerColors.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.deep2).padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 9.dp)) {
        Text(label, color = c.deepInk2, style = LedgerType.small.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value, color = c.deepInk, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = LedgerType.amount,
        )
    }
}

/* ---------- money on the way: refunds awaited, card dues ---------- */

@Composable
private fun InFlightCard(f: InFlight, onOpen: () -> Unit, onCard: (String) -> Unit) {
    val c = LocalLedgerColors.current
    LedgerCard(Modifier.padding(top = 12.dp)) {
        Column(Modifier.pressable(shrink = false, onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            f.refunds?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Refunds", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink, modifier = Modifier.weight(1f))
                    Text(it, style = LedgerType.small.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = c.loan)
                }
                f.refundsNote?.let { n -> Text(n, style = LedgerType.small, color = c.spent) }
            }
            if (f.refunds != null && f.cards.isNotEmpty()) Hairline()
            if (f.cards.isNotEmpty()) {
                Text("Card due", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                f.cards.forEach { (name, due) ->
                    Row(Modifier.pressable(shrink = false) { onCard(name) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = LedgerType.body, color = c.ink2, modifier = Modifier.weight(1f))
                        Text(due, style = LedgerType.amount, color = c.ink)
                        Icon(LedgerIcons.ChevronRight, "Open $name", tint = c.ink2, modifier = Modifier.size(18.dp))
                    }
                }
                f.cardNote?.let { n -> Text(n, style = LedgerType.small, color = c.ink2) }
            }
        }
    }
}

/* ---------- streak ---------- */

@Composable
private fun StreakCard(s: HomeUiState.Ready) {
    val c = LocalLedgerColors.current
    LedgerCard(Modifier.padding(top = 12.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${s.streak} day${if (s.streak == 1) "" else "s"} streak", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink)
                Text(if (s.loggedToday) "Logged today. Nice." else "Nothing logged today yet", style = LedgerType.small, color = c.ink2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.week.forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            Modifier.size(20.dp)
                                .then(if (d.isToday) Modifier.border(2.dp, c.accentSoft, CircleShape) else Modifier),
                            Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(16.dp).clip(CircleShape)
                                    .background(if (d.logged) c.accent else Color.Transparent)
                                    .border(2.dp, if (d.logged) c.accent else c.line, CircleShape),
                            )
                        }
                        Text(d.letter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.ink2, style = LedgerType.small)
                    }
                }
            }
        }
    }
}

/* ---------- quick actions ---------- */

@Composable
private fun QuickActions(onSay: () -> Unit, onWith: () -> Unit, onLent: () -> Unit, onBorrowed: () -> Unit) {
    val c = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction("Say it", LedgerIcons.Mic, c.ink, onSay, Modifier.weight(1f))
        QuickAction("With people", LedgerIcons.Group, c.accent, onWith, Modifier.weight(1f))
        QuickAction("Lent", LedgerIcons.Out, c.loan, onLent, Modifier.weight(1f))
        QuickAction("Borrowed", LedgerIcons.In, c.income, onBorrowed, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalLedgerColors.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
            .pressable(onClick = onClick).padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconTile(tint, icon = icon, size = 36.dp)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LedgerType.small)
    }
}

/* ---------- empty ---------- */

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val c = LocalLedgerColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface)
            .drawBehind {
                drawRoundRect(
                    c.line, cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                )
            }
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).rotate(-6f).clip(RoundedCornerShape(22.dp)).background(c.accent),
            Alignment.Center,
        ) { Text("₹", color = c.accentInk, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp) }
        Text("Your diary is empty", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = c.ink, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Text(
            "Tap the + button to log your first expense. Amount first, everything else is optional.",
            style = LedgerType.body, color = c.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(bottom = 18.dp),
        )
        PrimaryButton("Add first entry", Modifier.width(200.dp), height = 44.dp, onClick = onAdd)
    }
}

