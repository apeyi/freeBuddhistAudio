package com.fba.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TalkEntity::class, DownloadEntity::class, RecentlyListenedEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class FBADatabase : RoomDatabase() {
    abstract fun talkDao(): TalkDao
    abstract fun downloadDao(): DownloadDao
    abstract fun recentlyListenedDao(): RecentlyListenedDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE talks ADD COLUMN seriesHref TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v4 schema does NOT include totalDurationSeconds — that column is
                // added by MIGRATION_4_5. Creating it here too made the 3→4→5 chain
                // fail with "duplicate column" (previously masked by the destructive
                // fallback, which wiped user data instead).
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recently_listened (
                        catNum TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        speaker TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        positionMs INTEGER NOT NULL DEFAULT 0,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        trackIndex INTEGER NOT NULL DEFAULT 0,
                        listenedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Defensive: some v4 databases already have the column (created by
                // the old buggy MIGRATION_3_4, or a fresh install at v4).
                val hasColumn = db.query("PRAGMA table_info(recently_listened)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    generateSequence { if (cursor.moveToNext()) cursor.getString(nameIdx) else null }
                        .any { it == "totalDurationSeconds" }
                }
                if (!hasColumn) {
                    db.execSQL("ALTER TABLE recently_listened ADD COLUMN totalDurationSeconds INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun create(context: Context): FBADatabase {
            // No destructive fallback: a missing migration must fail loudly in
            // development, not silently wipe users' downloads and history.
            return Room.databaseBuilder(
                context.applicationContext,
                FBADatabase::class.java,
                "fba_database"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }
    }
}
