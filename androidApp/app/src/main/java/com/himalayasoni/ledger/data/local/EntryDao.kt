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

    // ---- used by sync ----

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    /** Rows changed on this phone and not uploaded yet. */
    @Query("SELECT * FROM entries WHERE dirty = 1")
    suspend fun dirty(): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM entries WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    /** Marks a row uploaded, unless it was edited again while the upload was in flight. */
    @Query("UPDATE entries SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    /** First sign-in (or a different account): everything on the phone needs uploading. */
    @Query("UPDATE entries SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT updatedAt FROM entries WHERE id = :id")
    suspend fun updatedAt(id: String): Long?

    /** A deletion from another device, applied only if it's newer than what's here. */
    @Query("UPDATE entries SET deleted = 1, dirty = 0, updatedAt = :updatedAt WHERE id = :id AND updatedAt < :updatedAt")
    suspend fun applyTombstone(id: String, updatedAt: Long)
}
