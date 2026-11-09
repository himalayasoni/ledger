package com.himalayasoni.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The phone's database file (SQLite, via Room). Bump [version] and add a migration
 * whenever the table shape changes; exported schemas in app/schemas track every version.
 */
/**
 * Version history:
 * 1. entries
 * 2. + card, refund (credit cards and refunds)
 */
@Database(entities = [EntryEntity::class], version = 2, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds the two new columns in place. Existing entries are kept as they are. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN card TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE entries ADD COLUMN refund TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
