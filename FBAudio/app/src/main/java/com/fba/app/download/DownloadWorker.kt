package com.fba.app.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val client: OkHttpClient,
    private val downloadDao: DownloadDao,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_CAT_NUM = "cat_num"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_TITLE = "title"
        const val KEY_TRACK_URLS = "track_urls"

        /** Get the file path for a specific track of a downloaded talk. */
        fun trackFilePath(context: Context, catNum: String, trackIndex: Int): String {
            val downloadsDir = File(context.filesDir, "downloads")
            return File(downloadsDir, "${catNum}_track${trackIndex}.mp3").absolutePath
        }
    }

    override suspend fun doWork(): Result {
        val catNum = inputData.getString(KEY_CAT_NUM) ?: return Result.failure()
        val trackUrls = inputData.getStringArray(KEY_TRACK_URLS)?.toList()
        val fallbackUrl = inputData.getString(KEY_AUDIO_URL)

        val urls = if (!trackUrls.isNullOrEmpty()) trackUrls else listOfNotNull(fallbackUrl)
        if (urls.isEmpty()) return Result.failure()

        try {
            downloadDao.updateProgress(catNum, DownloadStatus.DOWNLOADING, 0)

            val downloadsDir = File(applicationContext.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            var totalDownloaded: Long = 0

            for ((index, url) in urls.withIndex()) {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    downloadDao.updateProgress(catNum, DownloadStatus.FAILED, 0)
                    return Result.failure()
                }

                val body = response.body ?: run {
                    downloadDao.updateProgress(catNum, DownloadStatus.FAILED, 0)
                    return Result.failure()
                }

                val file = File(downloadsDir, "${catNum}_track${index}.mp3")

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read == -1) break
                            outputStream.write(buffer, 0, read)
                            totalDownloaded += read
                        }
                    }
                }

                // Update progress: completed tracks / total tracks
                val progress = ((index + 1) * 100) / urls.size
                downloadDao.updateProgress(catNum, DownloadStatus.DOWNLOADING, progress)
            }

            // Mark complete — filePath points to first track for backward compatibility
            val firstTrackFile = File(downloadsDir, "${catNum}_track0.mp3")
            downloadDao.markComplete(
                catNum = catNum,
                filePath = firstTrackFile.absolutePath,
                totalBytes = totalDownloaded,
            )

            return Result.success()
        } catch (e: Exception) {
            downloadDao.updateProgress(catNum, DownloadStatus.FAILED, 0)
            return Result.failure()
        }
    }
}
