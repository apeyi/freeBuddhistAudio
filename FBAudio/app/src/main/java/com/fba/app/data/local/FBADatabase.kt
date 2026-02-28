package com.fba.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TalkEntity::class, DownloadEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class FBADatabase : RoomDatabase() {
    abstract fun talkDao(): TalkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE talks ADD COLUMN seriesHref TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): FBADatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FBADatabase::class.java,
                "fba_database"
            )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
