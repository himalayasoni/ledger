package com.himalayasoni.ledger.di

import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.data.local.LedgerDatabase
import com.himalayasoni.ledger.data.local.RoomEntryRepository
import com.himalayasoni.ledger.ui.add.AddEntryViewModel
import com.himalayasoni.ledger.ui.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
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

    viewModel { HomeViewModel(get()) }
    viewModel { AddEntryViewModel(get()) }
}
