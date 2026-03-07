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
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recently_listened (
                        catNum TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        speaker TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        positionMs INTEGER NOT NULL DEFAULT 0,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        trackIndex INTEGER NOT NULL DEFAULT 0,
                        totalDurationSeconds INTEGER NOT NULL DEFAULT 0,
                        listenedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recently_listened ADD COLUMN totalDurationSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): FBADatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FBADatabase::class.java,
                "fba_database"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
