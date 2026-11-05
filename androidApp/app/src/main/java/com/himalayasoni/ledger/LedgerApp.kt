package com.himalayasoni.ledger

import android.app.Application
import com.himalayasoni.ledger.data.FirebaseConfig
import com.himalayasoni.ledger.data.sync.FirestoreSync
import com.himalayasoni.ledger.di.appModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Runs once when the app starts: Firebase, dependency injection (Koin), then sync. */
class LedgerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.init(this)
        startKoin {
            androidContext(this@LedgerApp)
            modules(appModule)
        }
        get<FirestoreSync>().start()
    }
}
