package com.himalayasoni.ledger.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.himalayasoni.ledger.core.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStore by preferencesDataStore(name = "sync")

/**
 * Small key-value settings kept with Jetpack DataStore (the modern replacement for
 * SharedPreferences: asynchronous, and observable as a Flow).
 */
class SyncPrefs(private val context: Context) : SettingsRepository {
    private object Keys {
        val uid = stringPreferencesKey("uid")
        val lastServerTime = longPreferencesKey("lastServerTime")
        val lastSyncAt = longPreferencesKey("lastSyncAt")
        val privateCategories = stringSetPreferencesKey("privateCategories")
    }

    override val privateCategories: Flow<Set<String>> =
        context.syncStore.data.map { it[Keys.privateCategories] ?: emptySet() }

    val lastSyncAt: Flow<Long> = context.syncStore.data.map { it[Keys.lastSyncAt] ?: 0L }

    suspend fun uid(): String? = context.syncStore.data.first()[Keys.uid]

    /** Server time of the newest change already downloaded; we ask only for newer ones. */
    suspend fun lastServerTime(): Long = context.syncStore.data.first()[Keys.lastServerTime] ?: 0L

    suspend fun setLastServerTime(value: Long) { context.syncStore.edit { it[Keys.lastServerTime] = value } }
    suspend fun markSynced(at: Long) { context.syncStore.edit { it[Keys.lastSyncAt] = at } }
    suspend fun setPrivateCategories(cats: Set<String>) { context.syncStore.edit { it[Keys.privateCategories] = cats } }

    /** A different account signed in: start downloading from the beginning. */
    suspend fun switchAccount(uid: String) {
        context.syncStore.edit { it[Keys.uid] = uid; it[Keys.lastServerTime] = 0L; it.remove(Keys.privateCategories) }
    }
}
