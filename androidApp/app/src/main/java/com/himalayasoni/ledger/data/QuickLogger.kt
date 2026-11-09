package com.himalayasoni.ledger.data

import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.ReplyResult
import com.himalayasoni.ledger.core.domain.Usual
import com.himalayasoni.ledger.core.domain.newEntryId
import com.himalayasoni.ledger.core.domain.parseReply
import com.himalayasoni.ledger.core.domain.toEntry
import com.himalayasoni.ledger.core.domain.voiceContext
import com.himalayasoni.ledger.core.domain.widgetUsuals
import com.himalayasoni.ledger.core.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Logging from outside the app (a widget tap, a notification reply). Saves through the same
 * repository as the add sheet, so the entry syncs like any other. Never offers or learns from
 * private categories, whatever the app's "show private" switch says.
 */
class QuickLogger(
    private val repository: EntryRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private suspend fun all(): List<Entry> = repository.observeEntries().first()

    suspend fun privateCategories(): Set<String> = settings.privateCategories.first()

    /** Logs the usual with this key again, dated today. Null if it isn't a usual any more. */
    suspend fun logUsual(key: String): Pair<Usual, Entry>? {
        val usual = all().widgetUsuals(privateCategories(), limit = 50).firstOrNull { it.key == key } ?: return null
        val now = clock()
        val entry = usual.toEntry(newEntryId(now), LocalDate.now(), now)
        repository.save(entry)
        return usual to entry
    }

    /** "chai 20 cash" typed into a notification: saved when it's complete, else says what's missing. */
    suspend fun logReply(text: String): ReplyResult {
        val now = clock()
        val cards = settings.cardSettings.first().map { it.name }
        val context = all().voiceContext(privateCategories(), LocalDate.now(), cards)
        val result = parseReply(text, context, newEntryId(now), now)
        if (result is ReplyResult.Ready) repository.save(result.entry)
        return result
    }

    /** Undo: deletes the entry just logged (as a synced deletion, like deleting it in the app). */
    suspend fun undo(id: String) = repository.delete(id)
}

/**
 * The app's settings with "show private" forced off. The quick add pop-up uses this, so private
 * categories never show over the home screen even if you unlocked them in the app earlier.
 */
class PrivateAlwaysHidden(private val settings: SettingsRepository) : SettingsRepository by settings {
    override val showPrivate: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun setShowPrivate(show: Boolean) {}
}
