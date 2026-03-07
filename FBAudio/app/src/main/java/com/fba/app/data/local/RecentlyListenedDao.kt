package com.fba.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyListenedDao {
    @Query("SELECT * FROM recently_listened ORDER BY listenedAt DESC LIMIT 20")
    fun getRecentlyListened(): Flow<List<RecentlyListenedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentlyListenedEntity)
}
