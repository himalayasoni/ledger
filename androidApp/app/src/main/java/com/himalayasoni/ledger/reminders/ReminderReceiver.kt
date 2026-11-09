package com.himalayasoni.ledger.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.cardBillNudges
import com.himalayasoni.ledger.core.domain.needsDailyNudge
import com.himalayasoni.ledger.core.domain.owedNudge
import com.himalayasoni.ledger.core.domain.refundNudges
import com.himalayasoni.ledger.core.domain.stillRelevant
import com.himalayasoni.ledger.data.prefs.ReminderPrefs
import com.himalayasoni.ledger.widget.LedgerWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate

/**
 * Where the alarms land. Each action checks whether its reminder is due (the rules are in
 * core's Reminders.kt), posts it, then sets the next alarm. Also listens for the phone
 * restarting, the app being updated and the clock or time zone changing, to set alarms again.
 */
class ReminderReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: EntryRepository by inject()
    private val settings: SettingsRepository by inject()
    private val prefs: ReminderPrefs by inject()
    private val scheduler: ReminderScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DAILY -> daily(app)
                    ACTION_CHECK -> refundsAndCards(app)
                    ACTION_WEEKLY -> weekly(app)
                    ACTION_MIDNIGHT -> LedgerWidgets.refresh(app)
                    // boot, app updated, clock changed: nothing to post, just set the alarms again below
                }
                scheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    /** "Anything to log today?", only if nothing was logged today. */
    private suspend fun daily(context: Context) {
        if (!prefs.current().daily) return
        if (repository.observeEntries().first().needsDailyNudge(LocalDate.now())) {
            Notifications.post(context, Notifications.ID_DAILY, Notifications.daily(context))
        }
    }

    /** Refunds awaited 7+ days and card bills due within 3 days: one reminder each, ever. */
    private suspend fun refundsAndCards(context: Context) {
        val s = prefs.current()
        val entries = repository.observeEntries().first()
        val private = settings.privateCategories.first()
        val today = LocalDate.now()
        val reminded = entries.stillRelevant(prefs.reminded(), today).toMutableSet()
        val posted = buildList {
            if (s.refunds) entries.refundNudges(private, today, reminded).forEach { add(Notifications.CH_REFUNDS to it) }
            if (s.cardBills) entries.cardBillNudges(private, settings.cardSettings.first(), today, reminded).forEach { add(Notifications.CH_CARDS to it) }
        }
        if (posted.isNotEmpty() && !Notifications.allowed(context)) return // try again tomorrow, once allowed
        for ((channel, nudge) in posted) {
            val id = 1000 + (nudge.key.hashCode() and 0xFFFFF)
            Notifications.post(context, id, Notifications.nudge(context, channel, nudge, id))
            reminded += nudge.key
        }
        prefs.setReminded(reminded)
    }

    /** Sunday evening: "Owed to you: Rahul ₹1,500 · Priya ₹300". */
    private suspend fun weekly(context: Context) {
        if (!prefs.current().weeklyPeople) return
        val nudge = repository.observeEntries().first().owedNudge(settings.privateCategories.first()) ?: return
        Notifications.post(context, Notifications.ID_PEOPLE, Notifications.nudge(context, Notifications.CH_PEOPLE, nudge, Notifications.ID_PEOPLE))
    }

    companion object {
        const val ACTION_DAILY = "com.himalayasoni.ledger.reminders.DAILY"
        const val ACTION_CHECK = "com.himalayasoni.ledger.reminders.CHECK"
        const val ACTION_WEEKLY = "com.himalayasoni.ledger.reminders.WEEKLY"
        const val ACTION_MIDNIGHT = "com.himalayasoni.ledger.reminders.MIDNIGHT"
    }
}
