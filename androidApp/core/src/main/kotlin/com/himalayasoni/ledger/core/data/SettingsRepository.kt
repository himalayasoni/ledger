package com.himalayasoni.ledger.core.data

import kotlinx.coroutines.flow.Flow

/** Settings that follow your account, plus this phone's privacy switch. */
interface SettingsRepository {
    /** Categories marked private (synced with the web app). */
    val privateCategories: Flow<Set<String>>

    /** Categories you added yourself (synced with the web app). */
    val customCategories: Flow<Set<String>>

    /** Built-in categories you removed on the web. */
    val removedCategories: Flow<Set<String>>

    /** Whether private entries are showing on this phone. Off until you unlock with your fingerprint. */
    val showPrivate: Flow<Boolean>

    suspend fun setShowPrivate(show: Boolean)
}
