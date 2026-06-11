package com.fba.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.DownloadEntity
import com.fba.app.data.local.DownloadStatus
import com.fba.app.download.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val context: Context,
) {
    fun observeAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun observeCompletedDownloads(): Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()

    fun observeDownload(catNum: String): Flow<DownloadEntity?> = downloadDao.observeDownload(catNum)

    suspend fun getDownload(catNum: String): DownloadEntity? = downloadDao.getDownload(catNum)

    suspend fun startDownload(
        catNum: String,
        title: String,
        speaker: String,
        imageUrl: String,
        audioUrl: String,
        trackUrls: List<String> = emptyList(),
        transcriptUrl: String = "",
    ) {
        val entity = DownloadEntity(
            catNum = catNum,
            title = title,
            speaker = speaker,
            imageUrl = imageUrl,
            filePath = "",
            status = DownloadStatus.PENDING,
        )
        downloadDao.insert(entity)

        // Pass all track URLs (or just the main audioUrl if single-track)
        val urls = if (trackUrls.isNotEmpty()) trackUrls else listOf(audioUrl)
        val workData = Data.Builder()
            .putString(DownloadWorker.KEY_CAT_NUM, catNum)
            .putString(DownloadWorker.KEY_AUDIO_URL, audioUrl)
            .putString(DownloadWorker.KEY_TITLE, title)
            .putStringArray(DownloadWorker.KEY_TRACK_URLS, urls.toTypedArray())
            .putString(DownloadWorker.KEY_TRANSCRIPT_URL, transcriptUrl)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("download_$catNum")
            .build()

        // Unique work keyed by catNum: a double-tap (or retry racing a cancel) must
        // never run two workers writing the same files concurrently.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$catNum",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun deleteAllDownloads() {
        val allDownloads = downloadDao.getAllDownloadsOnce()
        for (download in allDownloads) {
            deleteDownload(download.catNum)
        }
    }

    suspend fun deleteDownload(catNum: String) {
        // Cancel any in-flight worker FIRST so it stops writing files we're about
        // to delete (its own cancellation cleanup removes partial files).
        WorkManager.getInstance(context).cancelUniqueWork("download_$catNum")
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$catNum")

        withContext(Dispatchers.IO) {
            val download = downloadDao.getDownload(catNum)
            // Delete old-style single file
            if (download != null && download.filePath.isNotBlank()) {
                val file = java.io.File(download.filePath)
                if (file.exists()) file.delete()
            }
            // Delete all track files and transcript
            val downloadsDir = java.io.File(context.filesDir, "downloads")
            if (downloadsDir.exists()) {
                downloadsDir.listFiles()?.filter { it.name.startsWith("${catNum}_") }?.forEach { it.delete() }
            }
            downloadDao.delete(catNum)
        }
    }
}
