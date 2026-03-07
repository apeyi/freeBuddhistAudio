package com.fba.app.data.remote

import com.fba.app.domain.model.BrowseCategory
import com.fba.app.domain.model.CategoryType
import com.fba.app.domain.model.SearchResult
import com.fba.app.domain.model.Talk
import com.fba.app.domain.model.Track
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.Calendar
import javax.inject.Inject

/**
 * Represents a page of browse results, including pagination metadata needed to load more.
 * @param apiBaseUrl  Full URL to the API endpoint, e.g. "https://…/api/v1/collections/s"
 * @param browseQueryString  Original query string from the browse URL, e.g. "s=Sangharakshita&t=audio"
 */
data class BrowsePage(
    val items: List<SearchResult>,
    val totalItems: Int,
    val apiBaseUrl: String,
    val browseQueryString: String,
    val title: String = "",
) {
    val hasMore: Boolean get() = items.size < totalItems
}

class FBAScraper @Inject constructor(
    private val client: OkHttpClient,
) {
    companion object {
        private const val BASE_URL = "https://www.freebuddhistaudio.com"
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
            response.body?.string() ?: throw Exception("Empty response: $url")
        }
    }

    private fun extractFbaJson(html: String, key: String): JsonObject? {
        val doc = Jsoup.parse(html)
        for (script in doc.select("script")) {
            val data = script.data()
            val marker = "document.__FBA__.$key"
            val idx = data.indexOf(marker)
            if (idx >= 0) {
                val eqIdx = data.indexOf('=', idx)
                if (eqIdx >= 0) {
                    val jsonStart = data.indexOf('{', eqIdx)
                    if (jsonStart >= 0) {
                        return tryParseJsonObject(data, jsonStart)
                    }
                }
            }
        }
        return null
    }

    private fun extractFbaJsonArray(html: String, key: String): List<JsonObject> {
        val doc = Jsoup.parse(html)
        for (script in doc.select("script")) {
            val data = script.data()
            val marker = "document.__FBA__.$key"
            val idx = data.indexOf(marker)
            if (idx >= 0) {
                val eqIdx = data.indexOf('=', idx)
                if (eqIdx >= 0) {
                    val arrayStart = data.indexOf('[', eqIdx)
                    if (arrayStart >= 0) {
                        val jsonStr = extractBalanced(data, arrayStart, '[', ']')
                        if (jsonStr != null) {
                            val array = JsonParser.parseString(jsonStr).asJsonArray
                            return array.map { it.asJsonObject }
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun tryParseJsonObject(data: String, start: Int): JsonObject? {
        val jsonStr = extractBalanced(data, start, '{', '}') ?: return null
        return try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun extractBalanced(data: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until data.length) {
            val c = data[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\' && inString) {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == open) depth++
                else if (c == close) {
                    depth--
                    if (depth == 0) return data.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun unescape(str: String): String = Parser.unescapeEntities(str, false)

    suspend fun fetchTalkDetail(catNum: String): Talk? {
        val html = fetchHtml("$BASE_URL/audio/details?num=$catNum")
        val talkJson = extractFbaJson(html, "talk") ?: return null
        return parseTalk(talkJson, html)
    }

    private fun parseTalk(json: JsonObject, html: String = ""): Talk {
        val catNum = json.getStr("catNum") ?: json.getStr("cat_num") ?: ""
        val title = unescape(json.getStr("title") ?: "")
        val speaker = unescape(
            json.getStr("speaker")
                ?: json.getAsJsonArray("speakers")?.firstOrNull()?.asString
                ?: ""
        )
        val year = json.getInt("year") ?: 0
        val genre = json.getStr("genre") ?: json.getStr("genre1") ?: ""
        val duration = (json.getInt("durationSeconds") ?: json.getInt("duration") ?: 0).coerceAtLeast(0)
        val imageUrl = json.getStr("image") ?: json.getStr("imageUrl") ?: json.getStr("image_url") ?: ""
        val rawDesc = json.getStr("blurb") ?: json.getStr("description") ?: ""
        val description = if (rawDesc.contains('<')) {
            val descDoc = Jsoup.parse(rawDesc)
            descDoc.select("p").prepend("\n\n")
            descDoc.select("br").append("\n")
            descDoc.text().trim()
        } else unescape(rawDesc)

        val tracks = parseTracksArray(json)
        val audioUrl = tracks.firstOrNull()?.audioUrl ?: buildAudioUrl(catNum)

        // transcriptHref is the correct field; fall back to HTML link scan
        val transcriptUrl = run {
            val fromJson = json.getStr("transcriptHref") ?: json.getStr("text_url")
                ?: json.getStr("textUrl") ?: json.getStr("transcriptUrl")
                ?: json.getStr("transcript_url") ?: ""
            if (fromJson.isNotBlank()) {
                resolveUrl(fromJson)
            } else if (html.isNotBlank()) {
                val doc = Jsoup.parse(html)
                val link = doc.select("a[href*=/texts]").firstOrNull()
                val href = link?.attr("href") ?: ""
                if (href.isNotBlank()) resolveUrl(href) else ""
            } else ""
        }

        // series can be a string OR an object {"title": "...", "href": "/series/details?num=..."}
        val seriesEl = json.get("series")
        val seriesTitle: String
        val seriesHref: String
        when {
            seriesEl == null || seriesEl.isJsonNull -> { seriesTitle = ""; seriesHref = "" }
            seriesEl.isJsonPrimitive -> { seriesTitle = unescape(seriesEl.asString); seriesHref = "" }
            seriesEl.isJsonObject -> {
                val obj = seriesEl.asJsonObject
                seriesTitle = unescape(obj.getStr("title") ?: "")
                seriesHref = obj.getStr("href") ?: ""
            }
            else -> { seriesTitle = ""; seriesHref = "" }
        }

        return Talk(
            catNum = catNum,
            title = title,
            speaker = speaker,
            year = year,
            genre = genre,
            durationSeconds = duration,
            imageUrl = resolveUrl(imageUrl),
            audioUrl = resolveUrl(audioUrl),
            description = description,
            tracks = tracks,
            transcriptUrl = transcriptUrl,
            series = seriesTitle,
            seriesHref = seriesHref,
        )
    }

    private fun parseTracksArray(json: JsonObject): List<Track> {
        val tracksJson = json.getAsJsonArray("tracks") ?: return emptyList()
        val result = mutableListOf<Track>()
        for (trackEl in tracksJson) {
            val t = trackEl.asJsonObject
            val audio = t.getAsJsonObject("audio") ?: continue
            val mp3 = audio.getStr("mp3") ?: continue
            result.add(
                Track(
                    title = unescape(t.getStr("title") ?: ""),
                    durationSeconds = (t.getInt("durationSeconds") ?: 0).coerceAtLeast(0),
                    audioUrl = resolveUrl(mp3),
                )
            )
        }
        return result
    }

    private fun buildAudioUrl(catNum: String): String {
        return "$BASE_URL/audio/stream?num=$catNum"
    }

    private fun resolveUrl(url: String): String {
        if (url.isBlank()) return ""
        return if (url.startsWith("http")) url else "$BASE_URL$url"
    }

    suspend fun fetchLatestTalks(): BrowsePage {
        val url = "$BASE_URL/browse?cat=latest&t=audio"
        val html = fetchHtml(url)
        return parseBrowseCollectionPage(html, "cat=latest&t=audio")
    }

    /**
     * Parse a /browse page into a BrowsePage with pagination metadata.
     * Items include: cat_num, title, speaker, image_url, year, url (/audio/details?num=…)
     */
    private fun parseBrowseCollectionPage(html: String, queryString: String): BrowsePage {
        val collectionJson = extractFbaJson(html, "collection")
            ?: return BrowsePage(emptyList(), 0, "", queryString)
        val items = collectionJson.getAsJsonArray("items")
            ?: return BrowsePage(emptyList(), 0, "", queryString)
        val results = mutableListOf<SearchResult>()
        for (item in items) {
            val obj = item.asJsonObject
            val path = obj.getStr("url") ?: ""
            if (!path.contains("/audio/")) continue
            val catNum = obj.getStr("cat_num") ?: obj.getStr("catNum")
                ?: path.substringAfter("num=", "").substringBefore("&")
            if (catNum.isBlank()) continue
            val year = obj.getStr("year")?.toIntOrNull() ?: 0
            results.add(
                SearchResult(
                    catNum = catNum,
                    title = unescape(obj.getStr("title") ?: ""),
                    speaker = unescape(obj.getStr("speaker") ?: ""),
                    imageUrl = resolveUrl(obj.getStr("image_url") ?: obj.getStr("image") ?: ""),
                    path = resolveUrl(path),
                    year = year,
                )
            )
        }
        val totalItems = collectionJson.getInt("total_items") ?: results.size
        val apiPath = collectionJson.getStr("url") ?: ""
        val apiBaseUrl = if (apiPath.isNotBlank()) resolveUrl(apiPath) else ""
        return BrowsePage(results, totalItems, apiBaseUrl, queryString)
    }

    /**
     * Fetch a batch of items by their 1-based indices using the collection API.
     * The site returns one item per API call; this fetches [count] items in parallel
     * starting at [startIndex] (1-based).
     */
    suspend fun fetchMoreItems(apiBaseUrl: String, browseQueryString: String, startIndex: Int, count: Int): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val jobs = (startIndex until startIndex + count).map { idx ->
                async {
                    try {
                        val url = "$apiBaseUrl?$browseQueryString&page=$idx"
                        val request = Request.Builder().url(url).build()
                        val body = client.newCall(request).execute().use { it.body?.string() } ?: return@async null
                        val data = JsonParser.parseString(body).asJsonObject
                        val coll = data.getAsJsonObject("collection") ?: return@async null
                        val items = coll.getAsJsonArray("items") ?: return@async null
                        val obj = items.firstOrNull()?.asJsonObject ?: return@async null
                        val path = obj.getStr("url") ?: return@async null
                        if (!path.contains("/audio/")) return@async null
                        val catNum = obj.getStr("cat_num") ?: obj.getStr("catNum")
                            ?: path.substringAfter("num=", "").substringBefore("&")
                        if (catNum.isBlank()) return@async null
                        SearchResult(
                            catNum = catNum,
                            title = unescape(obj.getStr("title") ?: ""),
                            speaker = unescape(obj.getStr("speaker") ?: ""),
                            imageUrl = resolveUrl(obj.getStr("image_url") ?: obj.getStr("image") ?: ""),
                            path = resolveUrl(path),
                            year = obj.getStr("year")?.toIntOrNull() ?: 0,
                        )
                    } catch (_: Exception) { null }
                }
            }
            jobs.awaitAll().filterNotNull()
        }

    /** Browse all talks by a speaker. Returns a BrowsePage with pagination info. */
    suspend fun browseBySpeaker(speakerName: String): BrowsePage {
        val browseUrl = "$BASE_URL/browse".toHttpUrl().newBuilder()
            .addQueryParameter("s", speakerName)
            .addQueryParameter("t", "audio")
            .build().toString()
        return parseBrowseCollectionPage(fetchHtml(browseUrl), "s=$speakerName&t=audio")
    }

    /**
     * Search audio talks via the FBA API: /api/v1/search?q=TERM&type=audio
     * Returns results matching across titles, speakers, and descriptions.
     */
    suspend fun searchAudio(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "audio")
            .build().toString()
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty response")
        }
        val json = JsonParser.parseString(body).asJsonObject
        val searchObj = json.getAsJsonObject("search") ?: return@withContext emptyList()
        val items = searchObj.getAsJsonArray("results") ?: return@withContext emptyList()
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        for (item in items) {
            if (results.size >= 200) break
            val obj = item.asJsonObject
            val catNum = obj.getStr("cat_num") ?: obj.getStr("catNum") ?: continue
            if (catNum.isBlank() || !seen.add(catNum)) continue
            val link = obj.getStr("link") ?: "/audio/details?num=$catNum"
            results.add(
                SearchResult(
                    catNum = catNum,
                    title = unescape(obj.getStr("title") ?: ""),
                    speaker = unescape(obj.getStr("speaker") ?: ""),
                    imageUrl = resolveUrl(obj.getStr("image_url") ?: obj.getStr("image") ?: ""),
                    path = resolveUrl(link),
                    year = obj.getStr("year")?.toIntOrNull() ?: 0,
                )
            )
        }
        results
    }

    /**
     * Fetch browse categories. Sangharakshita first, then curated topics.
     * No live speaker fetching.
     */
    suspend fun fetchBrowseCategories(): List<BrowseCategory> {
        val categories = mutableListOf<BrowseCategory>()

        // Sangharakshita first — hardcoded data, no network needed
        categories.add(
            BrowseCategory(
                id = "Sangharakshita",
                name = "Sangharakshita",
                type = CategoryType.SANGHARAKSHITA,
                browseUrl = "sang://root",
            )
        )

        // Mitra Study second
        categories.add(
            BrowseCategory(
                id = "mitra_study",
                name = "Mitra Study",
                type = CategoryType.MITRA_STUDY,
                browseUrl = "mitra://study",
            )
        )

        // Curated topic list with /browse?th= URLs
        val topics = listOf(
            "Meditation", "Mindfulness", "Wisdom", "Ethics", "Sangha",
            "The Buddha", "Dharma", "Devotion", "Death", "Relationships",
            "Impermanence", "Compassion",
        )
        for (topic in topics) {
            categories.add(
                BrowseCategory(
                    id = topic,
                    name = topic,
                    type = CategoryType.THEME,
                    browseUrl = "$BASE_URL/browse?th=${topic.replace(" ", "%20")}",
                )
            )
        }

        // Year categories — current year down to 2010
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (year in currentYear downTo 2010) {
            categories.add(
                BrowseCategory(
                    id = "year_$year",
                    name = year.toString(),
                    type = CategoryType.YEAR,
                    browseUrl = "$BASE_URL/browse?y=$year&t=audio",
                )
            )
        }

        return categories
    }

    /** Fetch transcript text from a transcript URL. Returns plain text with paragraph breaks. */
    suspend fun fetchTranscript(transcriptUrl: String): String {
        val url = resolveUrl(transcriptUrl)
        val html = fetchHtml(url)
        // transcript content is in document.__FBA__.text.content (HTML string)
        val textJson = extractFbaJson(html, "text")
        if (textJson != null) {
            val content = textJson.getStr("content") ?: ""
            if (content.isNotBlank()) {
                val doc = Jsoup.parse(content)
                // Set Jsoup output to preserve whitespace
                doc.outputSettings().prettyPrint(false)
                val sb = StringBuilder()
                for (el in doc.select("p, br, h1, h2, h3, h4, h5, h6, blockquote, li")) {
                    val text = el.text().trim()
                    if (text.isNotBlank()) {
                        sb.append(text).append("\n\n")
                    }
                }
                if (sb.isNotBlank()) return sb.toString().trim()
                // Fallback if no p tags: split on br
                return doc.wholeText().trim()
            }
        }
        // Fallback: just extract body text from the HTML page
        val doc = Jsoup.parse(html)
        return doc.select(".text-content, .content, article, main").text().ifBlank {
            doc.body()?.text() ?: ""
        }
    }

    /** Fetch a page of audio talks from any browse or series URL with full pagination metadata. */
    suspend fun fetchFromBrowseUrl(browseUrl: String): BrowsePage {
        val resolved = resolveUrl(browseUrl)
        val html = fetchHtml(resolved)
        return if (resolved.contains("/series/details")) {
            parseSeriesDetailPage(html)
        } else {
            val queryString = resolved.substringAfter('?', "")
            parseBrowseCollectionPage(html, queryString)
        }
    }

    /** Parse a /series/details page — talks are in document.__FBA__.series.members[]. */
    private fun parseSeriesDetailPage(html: String): BrowsePage {
        val seriesJson = extractFbaJson(html, "series")
            ?: return BrowsePage(emptyList(), 0, "", "")
        val seriesTitle = unescape(seriesJson.getStr("title") ?: "")
        val members = seriesJson.getAsJsonArray("members")
            ?: return BrowsePage(emptyList(), 0, "", "", title = seriesTitle)
        val results = mutableListOf<SearchResult>()
        for (item in members) {
            val obj = item.asJsonObject
            val catNum = obj.getStr("cat_num") ?: obj.getStr("catNum") ?: obj.getStr("member_cat_num") ?: continue
            if (catNum.isBlank()) continue
            val path = obj.getStr("link") ?: obj.getStr("url") ?: obj.getStr("href")
                ?: "/audio/details?num=$catNum"
            results.add(
                SearchResult(
                    catNum = catNum,
                    title = unescape(obj.getStr("title") ?: ""),
                    speaker = unescape(obj.getStr("speaker") ?: obj.getStr("author") ?: ""),
                    imageUrl = resolveUrl(obj.getStr("image_url") ?: obj.getStr("image") ?: ""),
                    path = resolveUrl(path),
                    year = obj.getStr("year")?.toIntOrNull() ?: 0,
                )
            )
        }
        return BrowsePage(results, results.size, "", "", title = seriesTitle)
    }

    private fun JsonObject.getStr(key: String): String? {
        return if (has(key) && !get(key).isJsonNull && get(key).isJsonPrimitive) get(key).asString else null
    }

    private fun JsonObject.getInt(key: String): Int? {
        return if (has(key) && !get(key).isJsonNull) {
            try { get(key).asInt } catch (_: Exception) { null }
        } else null
    }
}
