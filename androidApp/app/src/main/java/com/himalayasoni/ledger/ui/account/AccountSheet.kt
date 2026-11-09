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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.theme.categoryColor
import com.himalayasoni.ledger.data.sync.SyncStatus
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.PrimaryButton
import com.himalayasoni.ledger.ui.components.QuietButton
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    state: AccountUiState,
    onSignIn: (Activity) -> Unit,
    onSignOut: (Context) -> Unit,
    onDismiss: () -> Unit,
    privacy: PrivacyUi? = null,
    onTogglePrivate: (String) -> Unit = {},
    onShowPrivate: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val c = LocalLedgerColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = c.surface, scrimColor = c.scrim, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(4.dp)).background(c.line)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", fontFamily = com.himalayasoni.ledger.ui.theme.Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = c.ink)
            privacy?.let { PrivacySection(it, onTogglePrivate, onShowPrivate) }
            RemindersSection()
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

/** Privacy (web: privateSection). Hidden: one button to show them (fingerprint). Showing: pick categories, hide again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacySection(p: PrivacyUi, onToggle: (String) -> Unit, onShow: (Boolean) -> Unit) {
    val c = LocalLedgerColors.current
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    LedgerCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Privacy", style = LedgerType.title, color = c.ink)
            if (!p.showing) {
                Text(
                    if (p.privateCategories.isEmpty()) "Private entries are hidden. Show them to choose which categories are private."
                    else "Private expenses are hidden from every list, total and search.",
                    style = LedgerType.body, color = c.ink2,
                )
                PrimaryButton("Show private expenses", height = 48.dp) {
                    error = null
                    (context.findActivity() as? FragmentActivity)?.let { a ->
                        unlockWithPhoneLock(a, onSuccess = { onShow(true) }, onError = { error = it })
                    }
                }
                Text("Needs your fingerprint or phone lock.", style = LedgerType.small, color = c.ink2)
            } else {
                val n = p.privateCategories.size
                Text(
                    "$n private categor${if (n == 1) "y" else "ies"}, showing right now. Tap a category to make it private or not. Private ones are marked dark.",
                    style = LedgerType.body, color = c.ink2,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.categories.forEach { cat ->
                        val on = p.privateCategories.any { it.equals(cat, true) }
                        LedgerChip(cat, on, swatch = if (on) null else categoryColor(cat), trailing = if (on) "· private" else null) { onToggle(cat) }
                    }
                }
                if (p.suggestions.isNotEmpty()) Text(
                    "Looks like these could be private: " + p.suggestions.joinToString(", "),
                    style = LedgerType.small, color = c.ink2,
                )
                PrimaryButton("Hide private expenses", height = 48.dp) { onShow(false) }
                Text("Synced with the web app, so the same categories are private there.", style = LedgerType.small, color = c.ink2)
            }
            error?.let { Text(it, color = c.spent, fontWeight = FontWeight.SemiBold, style = LedgerType.small) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
