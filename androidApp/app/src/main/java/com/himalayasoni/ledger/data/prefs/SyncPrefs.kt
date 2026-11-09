package com.himalayasoni.ledger.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.CardSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStore by preferencesDataStore(name = "sync")

/**
 * Small key-value settings kept with Jetpack DataStore (the modern replacement for
 * SharedPreferences: asynchronous, and observable as a Flow).
 */
/** 2: refunds, named cards, card bills. */
const val CURRENT_FORMAT = 2

class SyncPrefs(private val context: Context) : SettingsRepository {
    private object Keys {
        val uid = stringPreferencesKey("uid")
        val lastServerTime = longPreferencesKey("lastServerTime")
        val lastSyncAt = longPreferencesKey("lastSyncAt")
        val privateCategories = stringSetPreferencesKey("privateCategories")
        val showPrivate = booleanPreferencesKey("showPrivate")
        val cardSettings = androidx.datastore.preferences.core.stringPreferencesKey("cardSettings")
        val format = androidx.datastore.preferences.core.intPreferencesKey("format")
        val customCategories = stringSetPreferencesKey("customCategories")
        val removedCategories = stringSetPreferencesKey("removedCategories")
    }

    override val privateCategories: Flow<Set<String>> =
        context.syncStore.data.map { it[Keys.privateCategories] ?: emptySet() }

    override val customCategories: Flow<Set<String>> = context.syncStore.data.map { it[Keys.customCategories] ?: emptySet() }
    override val removedCategories: Flow<Set<String>> = context.syncStore.data.map { it[Keys.removedCategories] ?: emptySet() }

    suspend fun setCategories(custom: Collection<String>, removed: Collection<String>) {
        context.syncStore.edit { it[Keys.customCategories] = custom.toSet(); it[Keys.removedCategories] = removed.toSet() }
    }

    override val cardSettings: Flow<List<CardSettings>> = context.syncStore.data.map { p ->
        val a = org.json.JSONArray(p[Keys.cardSettings] ?: "[]")
        List(a.length()) { i -> a.getJSONObject(i).let { o -> CardSettings(o.getString("name"), o.optInt("statementDay", 0).takeIf { it in 1..31 }) } }
    }

    suspend fun setCardSettings(cards: List<CardSettings>) {
        val a = org.json.JSONArray()
        cards.forEach { c -> a.put(org.json.JSONObject().put("name", c.name).apply { c.statementDay?.let { put("statementDay", it) } }) }
        context.syncStore.edit { it[Keys.cardSettings] = a.toString() }
    }

    override val showPrivate: Flow<Boolean> = context.syncStore.data.map { it[Keys.showPrivate] ?: false }

    override suspend fun setShowPrivate(show: Boolean) { context.syncStore.edit { it[Keys.showPrivate] = show } }

    val lastSyncAt: Flow<Long> = context.syncStore.data.map { it[Keys.lastSyncAt] ?: 0L }

    suspend fun uid(): String? = context.syncStore.data.first()[Keys.uid]

    /** Server time of the newest change already downloaded; we ask only for newer ones. */
    suspend fun lastServerTime(): Long = context.syncStore.data.first()[Keys.lastServerTime] ?: 0L

    /**
     * The data format this phone last synced with. When the app learns new entry types or fields,
     * bumping [CURRENT_FORMAT] makes the next sync download everything once, so documents it
     * couldn't read before (e.g. card bills made on the web) arrive now.
     */
    suspend fun needsFullResync(): Boolean = (context.syncStore.data.first()[Keys.format] ?: 1) < CURRENT_FORMAT
    suspend fun markFormatCurrent() { context.syncStore.edit { it[Keys.format] = CURRENT_FORMAT } }

    suspend fun setLastServerTime(value: Long) { context.syncStore.edit { it[Keys.lastServerTime] = value } }
    suspend fun markSynced(at: Long) { context.syncStore.edit { it[Keys.lastSyncAt] = at } }
    suspend fun setPrivateCategories(cats: Set<String>) { context.syncStore.edit { it[Keys.privateCategories] = cats } }

    /** A different account signed in: start downloading from the beginning. */
    suspend fun switchAccount(uid: String) {
        context.syncStore.edit { it[Keys.uid] = uid; it[Keys.lastServerTime] = 0L; it.remove(Keys.privateCategories) }
    }
}
