package com.fuso.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import com.fuso.core.database.dao.BlockDao
import com.fuso.core.database.dao.EntryDao
import com.fuso.core.database.dao.UsageDao
import com.fuso.core.database.entity.BlockEntity
import com.fuso.core.database.entity.EntryFtsEntity
import com.fuso.core.database.entity.EntryTagCrossRef
import com.fuso.core.database.entity.EntryEntity
import com.fuso.core.database.entity.TagEntity
import com.fuso.core.database.entity.UsageEventEntity
import com.fuso.core.database.entity.OutboxEntity
import com.fuso.core.database.dao.OutboxDao

@Database(
    entities = [
        EntryEntity::class,
        BlockEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        EntryFtsEntity::class,
        UsageEventEntity::class,
        OutboxEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class FusoDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    abstract fun blockDao(): BlockDao

    abstract fun usageDao(): UsageDao

    abstract fun outboxDao(): OutboxDao

    companion object {
        const val NAME = "fuso.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS usage_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        timestampEpochMillis INTEGER NOT NULL,
                        hourOfDay INTEGER NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        wordCount INTEGER NOT NULL,
                        detail TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_events_timestampEpochMillis ON usage_events(timestampEpochMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_events_type ON usage_events(type)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS outbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        opType TEXT NOT NULL,
                        entryId TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN colorIndex INTEGER")
            }
        }
    }

    suspend fun <R> runInTransaction(block: suspend () -> R): R =
        withTransaction(block)
}
