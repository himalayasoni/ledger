package com.himalayasoni.ledger

import android.app.Application
import com.himalayasoni.ledger.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Runs once when the app starts: switches on dependency injection (Koin). */
class LedgerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LedgerApp)
            modules(appModule)
        }
    }
}
