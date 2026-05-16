package com.fba.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FBAApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            setupCrashLogging()
        }
    }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val crashDir = File(filesDir, "crash_logs")
                crashDir.mkdirs()
                // Keep only the last 10 crash logs
                crashDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(9)?.forEach { it.delete() }
                File(crashDir, "crash_$timestamp.txt").writeText(
                    "Thread: ${thread.name}\nTime: $timestamp\n\n$sw"
                )
                Log.e("FBAudio", "Crash logged to crash_logs/crash_$timestamp.txt", throwable)
            } catch (_: Exception) {
                // Don't let crash logging itself cause issues
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
