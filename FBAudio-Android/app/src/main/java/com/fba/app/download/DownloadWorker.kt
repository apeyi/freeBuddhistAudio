package com.fba.app.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.DownloadStatus
import com.fba.app.data.remote.TranscriptParser
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
        const val KEY_TRANSCRIPT_URL = "transcript_url"

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

    override suspend fun doWork(): Result {
        val rawCatNum = inputData.getString(KEY_CAT_NUM) ?: return Result.failure()
        val catNum = sanitize(rawCatNum)
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
                // Check if worker has been stopped (e.g. cancelled or constraints no longer met)
                if (isStopped) {
                    downloadDao.updateStatus(catNum, DownloadStatus.FAILED)
                    return Result.failure()
                }

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    downloadDao.updateStatus(catNum, DownloadStatus.FAILED)
                    return Result.failure()
                }

                val body = response.body ?: run {
                    downloadDao.updateStatus(catNum, DownloadStatus.FAILED)
                    return Result.failure()
                }

                val file = File(downloadsDir, "${catNum}_track${index}.mp3")

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (isStopped) {
                                downloadDao.updateStatus(catNum, DownloadStatus.FAILED)
                                return Result.failure()
                            }
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

            // Download transcript if available (best-effort, don't fail the download)
            val transcriptUrl = inputData.getString(KEY_TRANSCRIPT_URL)
            if (!transcriptUrl.isNullOrBlank() && !isStopped) {
                try {
                    val transcriptRequest = Request.Builder().url(transcriptUrl).build()
                    val transcriptResponse = client.newCall(transcriptRequest).execute()
                    if (transcriptResponse.isSuccessful) {
                        val html = transcriptResponse.body?.string() ?: ""
                        if (html.isNotBlank()) {
                            val text = TranscriptParser.parseTranscriptHtml(html)
                            if (text.isNotBlank()) {
                                val transcriptFile = File(downloadsDir, "${catNum}_transcript.txt")
                                transcriptFile.writeText(text)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Transcript download failure is non-fatal
                }
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
            // Preserve current progress — only update status to FAILED
            downloadDao.updateStatus(catNum, DownloadStatus.FAILED)
            return Result.failure()
        }
    }
}
