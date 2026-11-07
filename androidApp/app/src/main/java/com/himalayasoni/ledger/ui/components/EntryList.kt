package com.himalayasoni.ledger.ui.components

import com.himalayasoni.ledger.ui.home.DayGroup
import com.himalayasoni.ledger.ui.home.EntryRow
import com.himalayasoni.ledger.ui.home.RowKind

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

/* Entry lists shared by Home, People, Search and Insights (web: groupedList, rowHtml). */

/* ---------- list ---------- */

@Composable
fun SectionHeader(title: String, link: String?, onLink: () -> Unit) {
    val c = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 22.dp, bottom = 0.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.ink, modifier = Modifier.weight(1f))
        if (link != null) Text(
            link, style = LedgerType.small, fontWeight = FontWeight.SemiBold, color = c.ink2,
            textDecoration = TextDecoration.Underline, modifier = Modifier.pressable(onClick = onLink),
        )
    }
}

@Composable
fun DaySection(day: DayGroup, onOpen: (String) -> Unit) {
    val c = LocalLedgerColors.current
    Column(Modifier.padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) {
            Text(day.label.uppercase(), style = LedgerType.label.copy(letterSpacing = 0.06.em), color = c.ink2, modifier = Modifier.weight(1f))
            day.spent?.let { Text(it.uppercase(), style = LedgerType.label.copy(letterSpacing = 0.06.em, fontFeatureSettings = "tnum"), color = c.ink2) }
        }
        LedgerCard {
            Column {
                day.rows.forEachIndexed { i, row ->
                    if (i > 0) Hairline()
                    EntryRowItem(row) { onOpen(row.id) }
                }
            }
        }
    }
}

@Composable
fun EntryRowItem(row: EntryRow, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    val tint = when (row.kind) {
        RowKind.LOAN_OUT, RowKind.LOAN_IN -> c.loan
        RowKind.IN -> c.income
        RowKind.OUT -> categoryColor(row.category)
    }
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (row.kind) {
            RowKind.LOAN_OUT -> IconTile(tint, icon = LedgerIcons.Out)
            RowKind.LOAN_IN -> IconTile(tint, icon = LedgerIcons.In)
            else -> IconTile(tint, letter = row.initial)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.ink, style = LedgerType.body)
            if (row.subtitle.isNotBlank()) Text(row.subtitle, style = LedgerType.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.note.isNotBlank()) Text("“${row.note}”", style = LedgerType.small, fontStyle = FontStyle.Italic, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                row.amount, style = LedgerType.amount,
                color = when (row.kind) { RowKind.IN -> c.income; RowKind.LOAN_IN, RowKind.LOAN_OUT -> c.loan; else -> c.ink },
            )
            row.badge?.let { Text(it.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.em, color = c.ink2, style = LedgerType.small) }
        }
    }
}

