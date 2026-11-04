package com.himalayasoni.ledger.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The SQL behind the entries table. Room generates the code from these annotations. */
@Dao
interface EntryDao {
    /** A live query: Room re-emits the list whenever the table changes. */
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): EntryEntity?

    @Upsert
    suspend fun upsert(entry: EntryEntity)
}
