package com.himalayasoni.ledger.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.himalayasoni.ledger.data.sync.SyncStatus
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.PrimaryButton
import com.himalayasoni.ledger.ui.components.QuietButton
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(state: AccountUiState, onSignIn: (Activity) -> Unit, onSignOut: (Context) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = LocalLedgerColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = c.surface, scrimColor = c.scrim, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(4.dp)).background(c.line)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Backup & sync", style = LedgerType.title, color = c.ink)
            when {
                !state.configured -> Text("Sync isn't set up in this build yet. Entries are saved on this phone.", style = LedgerType.body, color = c.ink2)
                state.email == null -> {
                    Text(
                        "Sign in with the same Google account as Ledger on the web. Your entries appear here and stay in sync both ways.",
                        style = LedgerType.body, color = c.ink2,
                    )
                    PrimaryButton(if (state.busy) "Signing in…" else "Sign in with Google", enabled = !state.busy) {
                        context.findActivity()?.let(onSignIn)
                    }
                }
                else -> {
                    LedgerCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(state.email, fontWeight = FontWeight.Bold, style = LedgerType.body, color = c.ink)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dot = when (state.status) {
                                    is SyncStatus.Synced -> c.income
                                    is SyncStatus.Problem -> c.spent
                                    SyncStatus.Syncing -> c.accent
                                    else -> c.ink2
                                }
                                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                                Box(Modifier.width(6.dp))
                                Text(
                                    state.detailLine,
                                    style = LedgerType.small, color = if (state.status is SyncStatus.Problem) c.spent else c.ink2,
                                )
                            }
                        }
                    }
                    QuietButton("Sign out", Modifier.fillMaxWidth()) { onSignOut(context) }
                    Text(
                        "Signing out keeps your entries on this phone. Private categories from the web app stay hidden here.",
                        style = LedgerType.small, color = c.ink2,
                    )
                }
            }
            state.message?.let { Text(it, color = c.spent, fontWeight = FontWeight.SemiBold, style = LedgerType.small) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
