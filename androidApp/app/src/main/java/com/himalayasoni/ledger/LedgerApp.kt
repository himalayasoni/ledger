package com.himalayasoni.ledger

import android.app.Application
import com.himalayasoni.ledger.data.FirebaseConfig
import com.himalayasoni.ledger.data.prefs.ReminderPrefs
import com.himalayasoni.ledger.data.sync.FirestoreSync
import com.himalayasoni.ledger.di.appModule
import com.himalayasoni.ledger.reminders.Notifications
import com.himalayasoni.ledger.reminders.ReminderScheduler
import com.himalayasoni.ledger.widget.LedgerWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Runs once when the app's process starts: Firebase, dependency injection (Koin), then sync.
 * The process also starts for a widget tap, an alarm or a notification reply, without any
 * screen, so everything those need is set up here too.
 */
class LedgerApp : Application() {
    /** Work that lives as long as the process (not tied to any screen). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.init(this)
        startKoin {
            androidContext(this@LedgerApp)
            modules(appModule)
        }
        get<FirestoreSync>().start()

        Notifications.createChannels(this)
        // set the reminder alarms now, and again whenever a reminder setting changes
        val scheduler = get<ReminderScheduler>()
        appScope.launch { get<ReminderPrefs>().settings.distinctUntilChanged().collect { scheduler.reschedule() } }
        // redraw the home-screen widgets whenever what they show changes
        LedgerWidgets.keepFresh(this, appScope, LedgerWidgets.summaries(get(), get(), get()))
    }
}
