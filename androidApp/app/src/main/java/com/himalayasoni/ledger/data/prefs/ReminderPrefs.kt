package com.himalayasoni.ledger.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderStore by preferencesDataStore(name = "reminders")

/**
 * Your reminder and widget choices. Kept on this phone only (not synced): reminders are about
 * this phone's notifications, and another device may want different ones.
 *
 * @property dailyMinute the daily reminder's time, as minutes after midnight (21 * 60 = 9:00 pm)
 */
data class ReminderSettings(
    val daily: Boolean = false,
    val dailyMinute: Int = 21 * 60,
    val refunds: Boolean = true,
    val cardBills: Boolean = true,
    val weeklyPeople: Boolean = false,
    val hideWidgetAmounts: Boolean = false,
) {
    /** Is any reminder on? (Then Android 13+ needs the notification permission.) */
    val anyOn: Boolean get() = daily || refunds || cardBills || weeklyPeople
}

class ReminderPrefs(private val context: Context) {
    private object Keys {
        val daily = booleanPreferencesKey("daily")
        val dailyMinute = intPreferencesKey("dailyMinute")
        val refunds = booleanPreferencesKey("refunds")
        val cardBills = booleanPreferencesKey("cardBills")
        val weeklyPeople = booleanPreferencesKey("weeklyPeople")
        val hideWidgetAmounts = booleanPreferencesKey("hideWidgetAmounts")
        val reminded = stringSetPreferencesKey("reminded")
    }

    private fun Preferences.toSettings() = ReminderSettings(
        daily = this[Keys.daily] ?: false,
        dailyMinute = this[Keys.dailyMinute] ?: (21 * 60),
        refunds = this[Keys.refunds] ?: true,
        cardBills = this[Keys.cardBills] ?: true,
        weeklyPeople = this[Keys.weeklyPeople] ?: false,
        hideWidgetAmounts = this[Keys.hideWidgetAmounts] ?: false,
    )

    val settings: Flow<ReminderSettings> = context.reminderStore.data.map { it.toSettings() }

    suspend fun current(): ReminderSettings = settings.first()

    /** Changes one or more settings at once: `prefs.update { it.copy(daily = true) }`. */
    suspend fun update(change: (ReminderSettings) -> ReminderSettings) {
        context.reminderStore.edit { p ->
            val s = change(p.toSettings())
            p[Keys.daily] = s.daily
            p[Keys.dailyMinute] = s.dailyMinute
            p[Keys.refunds] = s.refunds
            p[Keys.cardBills] = s.cardBills
            p[Keys.weeklyPeople] = s.weeklyPeople
            p[Keys.hideWidgetAmounts] = s.hideWidgetAmounts
        }
    }

    /** Keys of reminders already shown (a refund, one card bill), so each nags only once. */
    suspend fun reminded(): Set<String> = context.reminderStore.data.first()[Keys.reminded] ?: emptySet()

    suspend fun setReminded(keys: Set<String>) { context.reminderStore.edit { it[Keys.reminded] = keys } }
}
