package com.himalayasoni.ledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himalayasoni.ledger.ui.add.AddEntrySheet
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import com.himalayasoni.ledger.ui.theme.categoryColor
import org.koin.androidx.compose.koinViewModel

/** The Home screen. Stateless pieces below take plain data, which makes them easy to preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = LocalLedgerColors.current.accent,
                contentColor = LocalLedgerColors.current.onAccent,
                shape = RoundedCornerShape(20.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Add entry") }
        },
    ) { padding ->
        when (val s = state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is HomeUiState.Ready -> HomeContent(s, PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp))
        }
    }
    if (adding) AddEntrySheet(onDismiss = { adding = false })
}

@Composable
private fun HomeContent(s: HomeUiState.Ready, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Ledger", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground) }
        item { MonthCard(s) }
        if (s.days.isEmpty()) item {
            Text("Nothing logged yet. Tap + to add your first entry.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
        }
        items(s.days, key = { it.label }) { day -> DayCard(day) }
    }
}

@Composable
private fun MonthCard(s: HomeUiState.Ready) {
    val c = LocalLedgerColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.deep).padding(20.dp),
    ) {
        Text("SPENT IN ${s.monthName.uppercase()}", color = c.onDeepMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        Text(s.spent, color = c.onDeep, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("Received", s.received, Modifier.weight(1f))
            Mini("Owed to you", s.owedToYou, Modifier.weight(1f))
            Mini("You owe", s.youOwe, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier) {
    val c = LocalLedgerColors.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.deepRaised).padding(10.dp)) {
        Text(label, color = c.onDeepMuted, fontSize = 12.sp, maxLines = 1)
        Text(value, color = c.onDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DayCard(day: DayGroup) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text(day.label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            day.spent?.let { Text(it.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            day.rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                EntryRowItem(row)
            }
        }
    }
}

@Composable
private fun EntryRowItem(row: EntryRow) {
    val c = LocalLedgerColors.current
    val tint = when (row.kind) { RowKind.LOAN -> c.loan; RowKind.IN -> c.income; RowKind.OUT -> categoryColor(row.category) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)), Alignment.Center) {
            Text(row.initial, color = tint, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            if (row.subtitle.isNotBlank()) Text(row.subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.amount, fontWeight = FontWeight.Bold, color = when (row.kind) { RowKind.IN -> c.income; RowKind.LOAN -> c.loan; else -> MaterialTheme.colorScheme.onSurface })
            row.badge?.let { Text(it.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
