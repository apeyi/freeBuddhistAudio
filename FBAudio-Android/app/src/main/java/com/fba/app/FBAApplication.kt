package com.fba.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.launch

@HiltAndroidApp
class FBAApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var downloadRepository: dagger.Lazy<com.fba.app.data.repository.DownloadRepository>

    @Inject
    lateinit var appScope: kotlinx.coroutines.CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            setupCrashLogging()
            uploadUnsentCrashLogs()
        }
        // Repair download rows orphaned by crashes/process death (status says
        // "downloading" but no worker exists) so they show Retry instead of 0%.
        appScope.launch {
            try {
                downloadRepository.get().reconcileStaleDownloads()
            } catch (e: Exception) {
                Log.e("FBAudio", "Download reconcile failed", e)
            }
        }
    }

    private fun crashDir(): File = File(filesDir, "crash_logs").apply { mkdirs() }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val dir = crashDir()
                // Keep only the last 10 crash logs
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(9)?.forEach { it.delete() }
                val report = "Thread: ${thread.name}\nTime: $timestamp\nVersion: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n$sw"
                val file = File(dir, "crash_$timestamp.txt")
                file.writeText(report)
                Log.e("FBAudio", "Crash logged to crash_logs/crash_$timestamp.txt", throwable)
                // Best-effort remote report before the process dies. Bounded wait so
                // we never hang the crash path on a bad network.
                if (sendCrashReport(report)) {
                    file.renameTo(File(dir, file.name + ".sent"))
                }
            } catch (_: Exception) {
                // Don't let crash logging itself cause issues
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Re-attempt upload of crash logs that didn't make it out at crash time. */
    private fun uploadUnsentCrashLogs() {
        if (BuildConfig.CRASH_REPORT_TG_TOKEN.isEmpty()) return
        thread(name = "crash-log-upload") {
            crashDir().listFiles()
                ?.filter { it.name.endsWith(".txt") }
                ?.sortedBy { it.lastModified() }
                ?.take(3) // don't spam the chat after a crash loop
                ?.forEach { file ->
                    try {
                        if (sendCrashReport("(from previous session)\n" + file.readText())) {
                            file.renameTo(File(file.parentFile, file.name + ".sent"))
                        }
                    } catch (_: Exception) {
                    }
                }
        }
    }

    /**
     * Debug-only: post the crash text to the developer's Telegram chat. The
     * token/chat are injected from the BUILD environment and are empty in any
     * build made without them (and always in release). Blocking with a short
     * timeout — called from a background thread or the (already dying) crash path.
     */
    private fun sendCrashReport(report: String): Boolean {
        val token = BuildConfig.CRASH_REPORT_TG_TOKEN
        val chat = BuildConfig.CRASH_REPORT_TG_CHAT
        if (token.isEmpty() || chat.isEmpty()) return false

        var ok = false
        val t = thread(name = "crash-report") {
            try {
                // Telegram caps messages at 4096 chars — keep the head of the trace,
                // which carries the exception + topmost frames.
                val text = "🐛 FBAudio debug crash:\n" + report.take(3800)
                val body = "chat_id=" + URLEncoder.encode(chat, "UTF-8") +
                    "&text=" + URLEncoder.encode(text, "UTF-8")
                val conn = URL("https://api.telegram.org/bot$token/sendMessage")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
                ok = conn.responseCode == 200
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
        t.join(4000)
        return ok
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
