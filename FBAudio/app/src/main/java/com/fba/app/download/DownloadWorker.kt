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
        const val KEY_TRANSCRIPT_URL = "transcript_url"

        fun trackFilePath(context: Context, catNum: String, trackIndex: Int): String {
            val downloadsDir = File(context.filesDir, "downloads")
            return File(downloadsDir, "${catNum}_track${trackIndex}.mp3").absolutePath
        }

        fun transcriptFilePath(context: Context, catNum: String): String {
            val downloadsDir = File(context.filesDir, "downloads")
            return File(downloadsDir, "${catNum}_transcript.txt").absolutePath
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

            // Download transcript if available (best-effort, don't fail the download)
            val transcriptUrl = inputData.getString(KEY_TRANSCRIPT_URL)
            if (!transcriptUrl.isNullOrBlank()) {
                try {
                    val transcriptRequest = Request.Builder().url(transcriptUrl).build()
                    val transcriptResponse = client.newCall(transcriptRequest).execute()
                    if (transcriptResponse.isSuccessful) {
                        val html = transcriptResponse.body?.string() ?: ""
                        if (html.isNotBlank()) {
                            // Parse transcript HTML to plain text
                            val doc = org.jsoup.Jsoup.parse(html)
                            // Try document.__FBA__.text.content first
                            var text = ""
                            for (script in doc.select("script")) {
                                val data = script.data()
                                val marker = "document.__FBA__.text"
                                val idx = data.indexOf(marker)
                                if (idx >= 0) {
                                    val braceIdx = data.indexOf('{', idx)
                                    if (braceIdx >= 0) {
                                        val jsonStr = extractBalancedBraces(data, braceIdx)
                                        if (jsonStr != null) {
                                            val json = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                                            val content = json.get("content")?.asString ?: ""
                                            if (content.isNotBlank()) {
                                                val contentDoc = org.jsoup.Jsoup.parse(content)
                                                val sb = StringBuilder()
                                                for (el in contentDoc.select("p, br, h1, h2, h3, h4, h5, h6, blockquote, li")) {
                                                    val t = el.text().trim()
                                                    if (t.isNotBlank()) sb.append(t).append("\n\n")
                                                }
                                                text = sb.toString().trim().ifBlank { contentDoc.wholeText().trim() }
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                            if (text.isBlank()) {
                                text = doc.select(".text-content, .content, article, main").text()
                                    .ifBlank { doc.body()?.text() ?: "" }
                            }
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
            downloadDao.updateProgress(catNum, DownloadStatus.FAILED, 0)
            return Result.failure()
        }
    }

    private fun extractBalancedBraces(data: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until data.length) {
            val c = data[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') { depth--; if (depth == 0) return data.substring(start, i + 1) }
            }
        }
        return null
    }
}
