package com.himalayasoni.ledger.ui.components

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

/* The app frame shared by every tab (web: nav.nav, .toast, .top). */

/* ---------- bottom bar ---------- */

@Composable
fun BottomBar(tab: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    val c = LocalLedgerColors.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(c.surface)) {
                Hairline()
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TabButton("Home", LedgerIcons.Home, tab == Tab.HOME, Modifier.weight(1f)) { onTab(Tab.HOME) }
                    TabButton("People", LedgerIcons.People, tab == Tab.PEOPLE, Modifier.weight(1f)) { onTab(Tab.PEOPLE) }
                    Spacer(Modifier.weight(1f))
                    TabButton("Search", LedgerIcons.Search, tab == Tab.SEARCH, Modifier.weight(1f)) { onTab(Tab.SEARCH) }
                    TabButton("Insights", LedgerIcons.Insights, tab == Tab.INSIGHTS, Modifier.weight(1f)) { onTab(Tab.INSIGHTS) }
                }
            }
            // the raised marigold add key (web: .fab, margin-top:-26px)
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-20).dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = c.accent, spotColor = c.accent)
                    .size(62.dp).clip(RoundedCornerShape(20.dp)).background(c.accent).pressable(onClick = onAdd),
                Alignment.Center,
            ) { Icon(LedgerIcons.Plus, "Add entry", tint = c.accentInk, modifier = Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun TabButton(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).pressable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, null, tint = if (on) c.accent else c.ink2, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) c.ink else c.ink2, style = LedgerType.small)
    }
}

/** A message at the bottom of the screen, optionally with one action ("Undo"). */
data class ToastMessage(val text: String, val action: String? = null, val onAction: () -> Unit = {})

@Composable
fun ToastBar(msg: ToastMessage, onDone: () -> Unit) {
    val c = LocalLedgerColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.ink).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg.text, color = c.bg, style = LedgerType.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (msg.action != null) Text(
            msg.action, color = c.accent, fontWeight = FontWeight.ExtraBold, style = LedgerType.body,
            modifier = Modifier.pressable { msg.onAction(); onDone() }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** The four tabs. */
enum class Tab { HOME, PEOPLE, SEARCH, INSIGHTS }

/** Big page title with the settings button: "People", "Search", "Insights" (web: .top h1.page). */
@Composable
fun PageHeader(title: String, onSettings: () -> Unit) {
    val c = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.02).em, color = c.ink, modifier = Modifier.weight(1f))
        SquareIconButton(LedgerIcons.Settings, "Settings and backup", onSettings)
    }
}

/** "‹ People": the back link at the top of a detail page. */
@Composable
fun BackLink(label: String, onBack: () -> Unit) {
    val c = LocalLedgerColors.current
    Row(Modifier.pressable(onClick = onBack).padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(LedgerIcons.ChevronLeft, null, tint = c.ink2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = LedgerType.body, fontWeight = FontWeight.SemiBold, color = c.ink2)
    }
}
