package com.himalayasoni.ledger.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.himalayasoni.ledger.core.domain.nextDaily
import com.himalayasoni.ledger.core.domain.nextWeekly
import com.himalayasoni.ledger.data.prefs.ReminderPrefs
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sets Android alarms for the reminders. Each alarm goes off once; when it does,
 * [ReminderReceiver] does its check and calls [reschedule] for the next one. Alarms are also
 * set again when the app starts, when a setting changes, and after the phone restarts
 * (Android forgets alarms on reboot).
 *
 * Why AlarmManager and not WorkManager: these are "at about this time" jobs that need no
 * network and take milliseconds, and AlarmManager is built into Android (nothing to add to the app).
 * The alarms are inexact: `setWindow` lets Android deliver them within 10 minutes, batched with
 * other apps' alarms to save battery, and needs no special "exact alarm" permission.
 */
class ReminderScheduler(private val context: Context, private val prefs: ReminderPrefs) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    suspend fun reschedule(now: LocalDateTime = LocalDateTime.now()) {
        val s = prefs.current()
        set(ReminderReceiver.ACTION_DAILY, if (s.daily) nextDaily(now, s.dailyMinute) else null)
        set(ReminderReceiver.ACTION_CHECK, if (s.refunds || s.cardBills) nextDaily(now, CHECK_MINUTE) else null)
        set(ReminderReceiver.ACTION_WEEKLY, if (s.weeklyPeople) nextWeekly(now, DayOfWeek.SUNDAY, WEEKLY_MINUTE) else null)
        // just after midnight: the widget's "today" starts again at ₹0. Doesn't wake the phone.
        set(ReminderReceiver.ACTION_MIDNIGHT, nextDaily(now, 0).plusMinutes(1), wake = false)
    }

    private fun set(action: String, at: LocalDateTime?, wake: Boolean = true) {
        val am = alarms ?: return
        val pi = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) { am.cancel(pi); return }
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(if (wake) AlarmManager.RTC_WAKEUP else AlarmManager.RTC, ms, WINDOW_MS, pi)
    }

    companion object {
        /** Refunds and card bills are checked once a day at 10:00 am. */
        const val CHECK_MINUTE = 10 * 60
        /** The weekly people summary: Sunday, 7:00 pm. */
        const val WEEKLY_MINUTE = 19 * 60
        private const val WINDOW_MS = 10 * 60_000L
    }
}
