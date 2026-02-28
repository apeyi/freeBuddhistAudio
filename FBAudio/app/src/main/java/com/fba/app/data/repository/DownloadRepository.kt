package com.fba.app.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.DownloadEntity
import com.fba.app.data.local.DownloadStatus
import com.fba.app.download.DownloadWorker
import kotlinx.coroutines.flow.Flow
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
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("download_$catNum")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    suspend fun deleteDownload(catNum: String) {
        val download = downloadDao.getDownload(catNum)
        // Delete old-style single file
        if (download != null && download.filePath.isNotBlank()) {
            val file = java.io.File(download.filePath)
            if (file.exists()) file.delete()
        }
        // Delete all track files (new naming convention)
        val downloadsDir = java.io.File(context.filesDir, "downloads")
        if (downloadsDir.exists()) {
            downloadsDir.listFiles()?.filter { it.name.startsWith("${catNum}_track") }?.forEach { it.delete() }
        }
        downloadDao.delete(catNum)
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$catNum")
    }
}
