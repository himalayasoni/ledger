package com.himalayasoni.ledger.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.himalayasoni.ledger.data.sync.SyncStatus
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(state: AccountUiState, onSignIn: (Activity) -> Unit, onSignOut: (Context) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = LocalLedgerColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Account & sync", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            when {
                !state.configured -> Text(
                    "Sync isn't set up in this build yet. Entries are saved on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.email == null -> {
                    Text(
                        "Sign in with the same Google account as Ledger on the web. Your entries appear here and stay in sync both ways.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { context.findActivity()?.let(onSignIn) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    ) { Text(if (state.busy) "Signing in…" else "Sign in with Google", fontWeight = FontWeight.Bold) }
                }
                else -> {
                    Text("Signed in as ${state.email}", fontWeight = FontWeight.SemiBold)
                    Text(
                        when (val s = state.status) {
                            is SyncStatus.Problem -> s.message
                            else -> state.statusLine
                        },
                        color = if (state.status is SyncStatus.Problem) c.spent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onSignOut(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign out")
                    }
                    Text(
                        "Signing out keeps your entries on this phone. Private categories from the web app stay hidden here.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.message?.let { Text(it, color = c.spent, fontWeight = FontWeight.SemiBold) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
