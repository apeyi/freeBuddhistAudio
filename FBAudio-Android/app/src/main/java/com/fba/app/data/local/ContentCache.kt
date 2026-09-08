package com.fba.app.data.local

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Small on-disk JSON cache for website content (menu tree, first pages of the
 * home rows, Digital Legacy copy) so Home and Collections open instantly and
 * work offline after the first load. Entries expire after [ttlMs] but stale
 * entries are still served when the network is unavailable.
 */
class ContentCache(context: Context, private val ttlMs: Long = 24L * 60 * 60 * 1000) {
    private val dir = File(context.filesDir, "content_cache").apply { mkdirs() }
    private val gson = Gson()

    private data class Envelope(val savedAt: Long, val json: String)

    private fun file(key: String) = File(dir, key.replace(Regex("[^a-zA-Z0-9_.-]"), "_") + ".json")

    suspend fun <T> put(key: String, value: T) = withContext(Dispatchers.IO) {
        try {
            file(key).writeText(gson.toJson(Envelope(System.currentTimeMillis(), gson.toJson(value))))
        } catch (_: Exception) { /* cache is best-effort */ }
    }

    /** Returns the value and whether it is still fresh; null when absent or unreadable. */
    suspend fun <T> get(key: String, type: Class<T>): Pair<T, Boolean>? = withContext(Dispatchers.IO) {
        try {
            val f = file(key)
            if (!f.exists()) return@withContext null
            val env = gson.fromJson(f.readText(), Envelope::class.java) ?: return@withContext null
            val value = gson.fromJson(env.json, type) ?: return@withContext null
            value to (System.currentTimeMillis() - env.savedAt < ttlMs)
        } catch (_: Exception) { null }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
    }
}
