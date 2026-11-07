package com.himalayasoni.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.himalayasoni.ledger.ui.components.PageHeader
import com.himalayasoni.ledger.ui.components.Tab
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors

/** Placeholder for tabs still being built. */
@Composable
fun ComingSoon(tab: Tab, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 14.dp)) {
        PageHeader(tab.name.lowercase().replaceFirstChar { it.uppercase() }, onSettings)
        Text("Coming in the next update.", style = LedgerType.body, color = LocalLedgerColors.current.ink2)
    }
}
