package com.himalayasoni.ledger.data

import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.withoutPrivate
import com.himalayasoni.ledger.core.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The one place that decides which entries the screens may see.
 * Private categories are left out unless you've unlocked them. Every screen reads from here,
 * so nothing can leak a private entry by forgetting to filter.
 */
class VisibleEntries(repository: EntryRepository, settings: SettingsRepository) {
    val entries: Flow<List<Entry>> =
        combine(repository.observeEntries(), settings.privateCategories, settings.showPrivate) { all, private, show ->
            if (show) all else all.withoutPrivate(private)
        }
}
