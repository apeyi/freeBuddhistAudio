package com.fba.app.data.repository

import com.fba.app.data.auth.AuthRepository
import com.fba.app.data.local.RecentlyListenedDao
import com.fba.app.data.local.RecentlyListenedEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.parser.Parser
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Two-way sync of listening history with the FBA account, using what the
 * website already has: a per-user history (talk + time) and a per-talk resume
 * position ("checkpoint"). Everything here is best-effort and silent — the
 * local store stays the source of truth offline.
 */
class HistoryRepository(
    private val client: OkHttpClient,
    private val auth: AuthRepository,
    private val recentlyListenedDao: RecentlyListenedDao,
) {
    companion object {
        private const val BASE = "https://www.freebuddhistaudio.com"
        private val JSON = "application/json".toMediaType()

        /** "2026-09-05T17:43:20.223708+0000" → epoch millis (0 if unparseable). */
        fun parseListenTime(value: String): Long {
            val trimmed = value.replace(Regex("\\.\\d+"), "")
            return try {
                OffsetDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() } catch (_: Exception) { 0L }
            }
        }
    }

    /**
     * Merge the account's history into Recently listened. Talks not yet known
     * locally are added; known talks keep their local position and take the
     * newer of the two timestamps.
     */
    suspend fun syncFromServer(maxItems: Int = 30) {
        if (!auth.isLoggedIn) return
        val json = getJson("$BASE/api/v1/history?maxItems=$maxItems") ?: return
        val items = json.getAsJsonArray("historyItems") ?: return
        val local = recentlyListenedDao.getAllOnce().associateBy { it.catNum }
        for (el in items) {
            val item = el.asJsonObject
            val talk = item.getAsJsonObject("talk") ?: continue
            val catNum = talk.str("cat_num") ?: continue
            val listenedAt = item.str("listenTime")?.let { parseListenTime(it) } ?: continue
            val existing = local[catNum]
            if (existing == null) {
                recentlyListenedDao.upsert(
                    RecentlyListenedEntity(
                        catNum = catNum,
                        title = Parser.unescapeEntities(talk.str("title") ?: "", false),
                        speaker = Parser.unescapeEntities(talk.str("speaker") ?: "", false),
                        imageUrl = talk.str("image")?.let { if (it.startsWith("http")) it else "$BASE$it" } ?: "",
                        listenedAt = listenedAt,
                    )
                )
            } else if (listenedAt > existing.listenedAt) {
                recentlyListenedDao.upsert(existing.copy(listenedAt = listenedAt))
            }
        }
        recentlyListenedDao.pruneOld()
    }

    /** The website records a "stream start" per play; mirror it so web history shows app listens. */
    suspend fun recordStreamStart(catNum: String) {
        if (!auth.isLoggedIn) return
        postJson("$BASE/api/v1/history", JsonObject().apply {
            addProperty("cat_num", catNum)
            addProperty("item_type", "talk")
            addProperty("action_type", "stream_start")
        })
    }

    /** Save the resume position on the account (the website does this every few seconds while playing). */
    suspend fun postCheckpoint(catNum: String, trackId: String, positionSeconds: Int) {
        if (!auth.isLoggedIn || trackId.isBlank()) return
        postJson("$BASE/api/v1/checkpoints/", JsonObject().apply {
            addProperty("catNum", catNum)
            addProperty("trackId", trackId)
            addProperty("timeSeconds", positionSeconds)
        })
    }

    private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                if (!body.trimStart().startsWith("{")) return@withContext null
                JsonParser.parseString(body).asJsonObject
            }
        } catch (_: Exception) { null }
    }

    private suspend fun postJson(url: String, body: JsonObject) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) { /* offline or session gone — silent */ }
    }

    private fun JsonObject.str(key: String): String? =
        if (has(key) && !get(key).isJsonNull && get(key).isJsonPrimitive) get(key).asString else null
}
