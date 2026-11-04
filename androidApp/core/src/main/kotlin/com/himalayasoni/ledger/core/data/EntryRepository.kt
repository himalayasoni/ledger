package com.himalayasoni.ledger.core.data

import com.himalayasoni.ledger.core.model.Entry
import kotlinx.coroutines.flow.Flow

/**
 * Where entries live. The core only knows this interface; the app decides the storage
 * (Room on the phone today, plus Firestore sync in Phase 2). Screens don't care which.
 */
interface EntryRepository {
    /** Every entry that isn't deleted, newest first, updating live as entries change. */
    fun observeEntries(): Flow<List<Entry>>

    /** Adds a new entry or replaces one with the same id. */
    suspend fun save(entry: Entry)

    /** Deletes by marking it deleted, so the deletion can sync too. */
    suspend fun delete(id: String)
}
