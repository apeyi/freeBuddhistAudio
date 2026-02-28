package com.fba.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETE' ORDER BY downloadedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE catNum = :catNum")
    suspend fun getDownload(catNum: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE catNum = :catNum")
    fun observeDownload(catNum: String): Flow<DownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE catNum = :catNum")
    suspend fun delete(catNum: String)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE catNum = :catNum")
    suspend fun updateProgress(catNum: String, status: DownloadStatus, progress: Int)

    @Query("UPDATE downloads SET status = :status, filePath = :filePath, downloadedAt = :downloadedAt, totalBytes = :totalBytes WHERE catNum = :catNum")
    suspend fun markComplete(catNum: String, status: DownloadStatus = DownloadStatus.COMPLETE, filePath: String, downloadedAt: Long = System.currentTimeMillis(), totalBytes: Long = 0)
}
