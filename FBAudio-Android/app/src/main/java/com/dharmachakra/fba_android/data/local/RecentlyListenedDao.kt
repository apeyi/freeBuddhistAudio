package com.dharmachakra.fba_android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyListenedDao {
    @Query("SELECT * FROM recently_listened ORDER BY listenedAt DESC LIMIT 20")
    fun getRecentlyListened(): Flow<List<RecentlyListenedEntity>>

    @Query("SELECT * FROM recently_listened")
    suspend fun getAllOnce(): List<RecentlyListenedEntity>

    @Query("DELETE FROM recently_listened")
    suspend fun deleteAll()

    /** Swipe "Mark as finished" on Continue listening. */
    @Query("UPDATE recently_listened SET positionMs = totalDurationSeconds * 1000 WHERE catNum = :catNum")
    suspend fun markFinished(catNum: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentlyListenedEntity)

    @Query("DELETE FROM recently_listened WHERE catNum NOT IN (SELECT catNum FROM recently_listened ORDER BY listenedAt DESC LIMIT 30)")
    suspend fun pruneOld()
}
