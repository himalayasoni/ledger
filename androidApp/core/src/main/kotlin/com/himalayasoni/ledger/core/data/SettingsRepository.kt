package com.himalayasoni.ledger.core.data

import kotlinx.coroutines.flow.Flow

/** Settings that come from your account (set in the web app today). */
interface SettingsRepository {
    /** Categories marked private. Their entries stay hidden until privacy controls arrive here. */
    val privateCategories: Flow<Set<String>>
}
