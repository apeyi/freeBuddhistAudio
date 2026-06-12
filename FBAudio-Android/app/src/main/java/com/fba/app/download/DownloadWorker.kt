package com.fba.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.DownloadStatus
import com.fba.app.data.remote.TranscriptParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
        const val KEY_TRANSCRIPT_URL = "transcript_url"
        private const val MAX_RETRIES = 3
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 2001

        /** Sanitize catNum to prevent path traversal — allow only alphanumeric and common prefixes. */
        private fun sanitize(catNum: String): String =
            catNum.replace(Regex("[^a-zA-Z0-9_-]"), "")

        fun trackFilePath(context: Context, catNum: String, trackIndex: Int): String {
            val downloadsDir = File(context.filesDir, "downloads")
            return File(downloadsDir, "${sanitize(catNum)}_track${trackIndex}.mp3").absolutePath
        }

        fun transcriptFilePath(context: Context, catNum: String): String {
            val downloadsDir = File(context.filesDir, "downloads")
            return File(downloadsDir, "${sanitize(catNum)}_transcript.txt").absolutePath
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(-1)

    /** [progress] 0-100 shows a determinate bar; anything else indeterminate. */
    private fun buildForegroundInfo(progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val title = inputData.getString(KEY_TITLE).takeUnless { it.isNullOrBlank() } ?: "Downloading talk"
        val determinate = progress in 0..100
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (determinate) "Downloading… $progress%" else "Downloading…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), !determinate)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** Refresh the foreground notification's progress bar; never fatal. */
    private suspend fun notifyProgress(progress: Int) {
        try {
            setForeground(buildForegroundInfo(progress))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // DAO rows are keyed by the RAW catNum (that's what the repository inserts);
        // only file names use the sanitized form (via trackFilePath).
        val catNum = inputData.getString(KEY_CAT_NUM) ?: return@withContext Result.failure()
        val trackUrls = inputData.getStringArray(KEY_TRACK_URLS)?.toList()
        val fallbackUrl = inputData.getString(KEY_AUDIO_URL)

        val urls = if (!trackUrls.isNullOrEmpty()) trackUrls else listOfNotNull(fallbackUrl)
        if (urls.isEmpty()) return@withContext Result.failure()

        // Promote to a foreground service so long downloads survive backgrounding.
        // Best-effort: on Android 12+ this can throw if the app is background-restricted.
        notifyProgress(-1)

        val downloadsDir = File(applicationContext.filesDir, "downloads")

        /** Status writes that must land even when the coroutine is being cancelled. */
        suspend fun markFailed() = withContext(NonCancellable) {
            downloadDao.updateStatus(catNum, DownloadStatus.FAILED)
        }

        /** Remove leftover partial files for this talk. */
        fun cleanPartials() {
            downloadsDir.listFiles()?.filter {
                it.name.startsWith("${sanitize(catNum)}_") && it.name.endsWith(".part")
            }?.forEach { it.delete() }
        }

        try {
            downloadDao.updateProgress(catNum, DownloadStatus.DOWNLOADING, 0)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            var totalDownloaded: Long = 0

            for ((index, url) in urls.withIndex()) {
                if (isStopped) {
                    cleanPartials()
                    markFailed()
                    return@withContext Result.retry()
                }

                val finalFile = File(trackFilePath(applicationContext, catNum, index))
                // Stream to a .part file and rename on success, so a crash or
                // cancellation never leaves a half-written file that looks complete.
                val partFile = File(finalFile.absolutePath + ".part")

                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                    val body = response.body ?: throw IOException("Empty body for $url")
                    val contentLength = body.contentLength() // -1 when the server omits it
                    var trackDownloaded = 0L
                    var lastReportedProgress = -1
                    body.byteStream().use { inputStream ->
                        FileOutputStream(partFile).use { outputStream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (isStopped) throw CancellationException("Worker stopped")
                                val read = inputStream.read(buffer)
                                if (read == -1) break
                                outputStream.write(buffer, 0, read)
                                totalDownloaded += read
                                trackDownloaded += read

                                // Byte-level progress within the track, so single-track
                                // talks don't sit at 0% and jump straight to 100%.
                                if (contentLength > 0) {
                                    val withinTrack = trackDownloaded.toDouble() / contentLength
                                    val progress = (((index + withinTrack) * 100) / urls.size).toInt()
                                    if (progress >= lastReportedProgress + 5) {
                                        lastReportedProgress = progress
                                        downloadDao.updateProgress(catNum, DownloadStatus.DOWNLOADING, progress)
                                        notifyProgress(progress)
                                    }
                                }
                            }
                        }
                    }
                }
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                val progress = ((index + 1) * 100) / urls.size
                downloadDao.updateProgress(catNum, DownloadStatus.DOWNLOADING, progress)
                notifyProgress(progress)
            }

            // Download transcript if available (best-effort, don't fail the download)
            val transcriptUrl = inputData.getString(KEY_TRANSCRIPT_URL)
            if (!transcriptUrl.isNullOrBlank() && !isStopped) {
                try {
                    client.newCall(Request.Builder().url(transcriptUrl).build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val html = resp.body?.string() ?: ""
                            if (html.isNotBlank()) {
                                val text = TranscriptParser.parseTranscriptHtml(html)
                                if (text.isNotBlank()) {
                                    File(transcriptFilePath(applicationContext, catNum)).writeText(text)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Transcript download failure is non-fatal
                }
            }

            // Mark complete — filePath points to first track for backward compatibility
            downloadDao.markComplete(
                catNum = catNum,
                filePath = trackFilePath(applicationContext, catNum, 0),
                totalBytes = totalDownloaded,
            )

            Result.success()
        } catch (e: CancellationException) {
            // Cancelled (user cancel, constraints lost, app stopped): clean partials,
            // record the failure with a NonCancellable write, and rethrow — never
            // swallow cancellation.
            withContext(NonCancellable) {
                cleanPartials()
                downloadDao.updateStatus(catNum, DownloadStatus.FAILED)
            }
            throw e
        } catch (e: IOException) {
            cleanPartials()
            // Transient network problem — retry with backoff up to MAX_RETRIES.
            return@withContext if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                markFailed()
                Result.failure()
            }
        } catch (e: Exception) {
            cleanPartials()
            markFailed()
            Result.failure()
        }
    }
}
