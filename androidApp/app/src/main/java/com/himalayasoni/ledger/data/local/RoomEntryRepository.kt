package com.himalayasoni.ledger.data.local

import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The phone-storage version of [EntryRepository]. Every change is marked "dirty" so the
 * Phase 2 sync knows what to upload. The rest of the app never sees Room directly.
 */
class RoomEntryRepository(
    private val dao: EntryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : EntryRepository {

    override fun observeEntries(): Flow<List<Entry>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toEntry() } }

    override suspend fun save(entry: Entry) {
        dao.upsert(entry.copy(updatedAt = clock()).toEntity(dirty = true))
    }

    override suspend fun delete(id: String) {
        val row = dao.get(id) ?: return
        dao.upsert(row.copy(deleted = true, updatedAt = clock(), dirty = true))
    }
}
