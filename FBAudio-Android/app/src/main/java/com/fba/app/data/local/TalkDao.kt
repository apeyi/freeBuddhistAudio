package com.fba.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TalkDao {
    @Query("SELECT * FROM talks ORDER BY cachedAt DESC")
    fun getAllTalks(): Flow<List<TalkEntity>>

    @Query("SELECT * FROM talks WHERE catNum = :catNum")
    suspend fun getTalk(catNum: String): TalkEntity?

    @Query("SELECT * FROM talks WHERE catNum = :catNum")
    fun observeTalk(catNum: String): Flow<TalkEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTalk(talk: TalkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTalks(talks: List<TalkEntity>)

    @Query("DELETE FROM talks WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT catNum, imageUrl FROM talks WHERE catNum IN (:catNums)")
    suspend fun getImageUrls(catNums: List<String>): List<CatNumImage>
}

data class CatNumImage(val catNum: String, val imageUrl: String)
