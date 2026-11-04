package com.himalayasoni.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The phone's database file (SQLite, via Room). Bump [version] and add a migration
 * whenever the table shape changes; exported schemas in app/schemas track every version.
 */
@Database(entities = [EntryEntity::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db").build()
    }
}
