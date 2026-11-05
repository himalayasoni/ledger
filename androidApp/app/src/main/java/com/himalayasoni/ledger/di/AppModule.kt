package com.himalayasoni.ledger.di

import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.data.auth.AuthRepository
import com.himalayasoni.ledger.data.auth.FirebaseAuthRepository
import com.himalayasoni.ledger.data.prefs.SyncPrefs
import com.himalayasoni.ledger.data.sync.FirestoreSync
import com.himalayasoni.ledger.ui.account.AccountViewModel
import com.himalayasoni.ledger.data.local.LedgerDatabase
import com.himalayasoni.ledger.data.local.RoomEntryRepository
import com.himalayasoni.ledger.ui.add.AddEntryViewModel
import com.himalayasoni.ledger.ui.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The app's wiring diagram. Each line says "when something needs X, build it like this".
 * Screens ask for a ViewModel; the ViewModel asks for an EntryRepository; nobody builds
 * their own dependencies, which keeps parts swappable and testable.
 */
val appModule = module {
    single { LedgerDatabase.build(androidContext()) }
    single { get<LedgerDatabase>().entryDao() }
    single<EntryRepository> { RoomEntryRepository(get()) }
    single { SyncPrefs(androidContext()) } bind SettingsRepository::class
    single<AuthRepository> { FirebaseAuthRepository() }
    single { FirestoreSync(get(), get(), get()) }

    viewModel { HomeViewModel(get(), get()) }
    viewModel { AccountViewModel(get(), get()) }
    viewModel { AddEntryViewModel(get()) }
}
