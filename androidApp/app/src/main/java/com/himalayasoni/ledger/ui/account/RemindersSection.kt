package com.himalayasoni.ledger.ui.account

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himalayasoni.ledger.data.prefs.ReminderPrefs
import com.himalayasoni.ledger.data.prefs.ReminderSettings
import com.himalayasoni.ledger.reminders.Notifications
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.components.QuietButton
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings → Reminders. Reads and saves [ReminderPrefs]; LedgerApp notices the change and sets
 * the alarms again. On Android 13+ the first switch you turn on asks for permission to notify.
 */
@Composable
fun RemindersSection(prefs: ReminderPrefs = koinInject()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by prefs.settings.collectAsStateWithLifecycle(ReminderSettings())
    var allowed by remember { mutableStateOf(Notifications.allowed(context)) }
    var asked by remember { mutableStateOf(false) }
    // notifications may have been switched on or off in Android's settings while we were away
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = Notifications.allowed(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && Notifications.allowed(context); asked = true
    }
    fun askOrOpenSettings() {
        if (Build.VERSION.SDK_INT >= 33 && !asked) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    RemindersContent(
        s, notificationsAllowed = allowed,
        onChange = { change ->
            val next = change(s)
            // switching a reminder on is the moment to ask (once), so the question makes sense
            if (next.anyOn && !allowed && !asked && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            scope.launch { prefs.update(change) }
        },
        onAllow = ::askOrOpenSettings,
    )
}

/** The section itself, with no storage: what the screenshot test draws. */
@Composable
internal fun RemindersContent(
    s: ReminderSettings,
    notificationsAllowed: Boolean,
    onChange: ((ReminderSettings) -> ReminderSettings) -> Unit,
    onAllow: () -> Unit,
) {
    val c = LocalLedgerColors.current
    var pickingTime by remember { mutableStateOf(false) }
    LedgerCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reminders", style = LedgerType.title, color = c.ink)
            ToggleRow(
                "Daily reminder",
                "At ${timeText(s.dailyMinute)}, only if you haven't logged anything that day. Reply right in the notification: “chai 20 cash”.",
                s.daily,
            ) { on -> onChange { it.copy(daily = on) } }
            if (s.daily) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Time", style = LedgerType.body, color = c.ink2)
                Spacer(Modifier.width(10.dp))
                LedgerChip(timeText(s.dailyMinute), soft = true) { pickingTime = true }
            }
            ToggleRow("Refunds", "Once, when a refund still hasn't arrived after 7 days.", s.refunds) { on -> onChange { it.copy(refunds = on) } }
            ToggleRow("Card bills", "3 days before a card's bill is due. Set the due day on the card's page.", s.cardBills) { on -> onChange { it.copy(cardBills = on) } }
            ToggleRow("Who owes you, weekly", "Sunday evening: what people owe you.", s.weeklyPeople) { on -> onChange { it.copy(weeklyPeople = on) } }
            Hairline()
            ToggleRow("Hide amounts on widgets", "Home-screen widgets show •••• instead of totals and prices.", s.hideWidgetAmounts) { on ->
                onChange { it.copy(hideWidgetAmounts = on) }
            }
            if (s.anyOn && !notificationsAllowed) {
                Text("Android needs your OK before Ledger can remind you.", style = LedgerType.small, color = c.spent, fontWeight = FontWeight.SemiBold)
                QuietButton("Allow notifications", Modifier.fillMaxWidth()) { onAllow() }
            }
        }
    }
    if (pickingTime) TimeDialog(s.dailyMinute, onPick = { m -> pickingTime = false; onChange { it.copy(dailyMinute = m) } }, onDismiss = { pickingTime = false })
}

@Composable
private fun ToggleRow(title: String, detail: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    val c = LocalLedgerColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgerType.body, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(detail, style = LedgerType.small, color = c.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = on, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.deepInk, checkedTrackColor = c.deep, checkedBorderColor = c.deep,
                uncheckedThumbColor = c.ink2, uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.line,
            ),
        )
    }
}

/** Material's clock-face time picker, in a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = LocalLedgerColors.current
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Daily reminder", style = LedgerType.title, color = c.ink) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Set", color = c.ink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

/** 21 * 60 → "9:00 pm". */
internal fun timeText(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase()
