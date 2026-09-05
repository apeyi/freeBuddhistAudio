package com.fba.app.data.remote

import com.fba.app.domain.model.BrowseCategory
import com.fba.app.domain.model.Checkpoint
import com.fba.app.domain.model.DigitalLegacy
import com.fba.app.domain.model.ListPage
import com.fba.app.domain.model.MenuNode
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

        /** "https://www.freebuddhistaudio.com/browse?p=Adhisthana " → "/browse?p=adhisthana" */
        fun normalizeBrowsePathStatic(link: String): String =
            link.trim().removePrefix(BASE_URL).lowercase()
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
        // Negative or absurd durations (a known data problem on the site) → 0, so the
        // UI derives the length from the tracks instead.
        val duration = (json.getInt("durationSeconds") ?: json.getInt("duration") ?: 0)
            .let { if (com.fba.app.ui.player.PlaybackMath.isPlausibleDuration(it)) it else 0 }
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

        // Saved position from the FBA account — only present on logged-in page loads.
        val checkpoint = json.get("checkpoint")?.takeIf { it.isJsonObject }?.asJsonObject?.let { cp ->
            val trackId = cp.getStr("track_id") ?: return@let null
            val seconds = cp.getStr("time_seconds")?.toIntOrNull() ?: cp.getInt("time_seconds") ?: return@let null
            Checkpoint(trackId, seconds)
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
            omOnly = (json.getInt("om") ?: 0) != 0,
            checkpoint = checkpoint,
        )
    }

    private fun parseTracksArray(json: JsonObject): List<Track> {
        val tracksJson = json.getAsJsonArray("tracks") ?: return emptyList()
        val result = mutableListOf<Track>()
        for (trackEl in tracksJson) {
            val t = trackEl.asJsonObject
            val audio = t.getAsJsonObject("audio") ?: continue
            val mp3 = audio.getStr("mp3") ?: continue
            val remaster = t.get("remasterAudio")?.takeIf { it.isJsonObject }?.asJsonObject?.getStr("mp3") ?: ""
            result.add(
                Track(
                    title = unescape(t.getStr("title") ?: ""),
                    durationSeconds = (t.getInt("durationSeconds") ?: 0)
                        .let { if (com.fba.app.ui.player.PlaybackMath.isPlausibleDuration(it)) it else 0 },
                    audioUrl = resolveUrl(mp3),
                    trackId = t.getStr("trackId") ?: "",
                    remasterAudioUrl = resolveUrl(remaster),
                    remasterDurationSeconds = (t.getInt("remasterDurationSeconds") ?: 0).coerceAtLeast(0),
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
            results.add(obj.toSearchResult(catNum, path))
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
     *
     * Non-audio or unparseable pages are skipped (callers must advance their fetch
     * index by [count], NOT by the number of items returned, or indices drift and
     * duplicates appear). Throws IOException if every page in the batch errored —
     * that's a network failure, not end-of-data.
     */
    suspend fun fetchMoreItems(apiBaseUrl: String, browseQueryString: String, startIndex: Int, count: Int): List<SearchResult> =
        withContext(Dispatchers.IO) {
            // Pair<result, wasError> per index: a null result with wasError=false is a
            // legitimately skipped page (non-audio); wasError=true is a fetch failure.
            val jobs = (startIndex until startIndex + count).map { idx ->
                async {
                    try {
                        val url = "$apiBaseUrl?$browseQueryString&page=$idx"
                        val request = Request.Builder().url(url).build()
                        val body = client.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) return@async null to true
                            resp.body?.string()
                        } ?: return@async null to true
                        val data = JsonParser.parseString(body).asJsonObject
                        val coll = data.getAsJsonObject("collection") ?: return@async null to false
                        val items = coll.getAsJsonArray("items") ?: return@async null to false
                        val obj = items.firstOrNull()?.asJsonObject ?: return@async null to false
                        val path = obj.getStr("url") ?: return@async null to false
                        if (!path.contains("/audio/")) return@async null to false
                        val catNum = obj.getStr("cat_num") ?: obj.getStr("catNum")
                            ?: path.substringAfter("num=", "").substringBefore("&")
                        if (catNum.isBlank()) return@async null to false
                        obj.toSearchResult(catNum, path) to false
                    } catch (_: Exception) { null to true }
                }
            }
            val outcomes = jobs.awaitAll()
            if (outcomes.isNotEmpty() && outcomes.all { it.second }) {
                throw java.io.IOException("All $count page fetches failed (network error)")
            }
            outcomes.mapNotNull { it.first }
        }

    /** Browse all talks by a speaker. Returns a BrowsePage with pagination info. */
    suspend fun browseBySpeaker(speakerName: String): BrowsePage {
        val browseUrl = "$BASE_URL/browse".toHttpUrl().newBuilder()
            .addQueryParameter("s", speakerName)
            .addQueryParameter("t", "audio")
            .build().toString()
        val encodedName = java.net.URLEncoder.encode(speakerName, "UTF-8")
        return parseBrowseCollectionPage(fetchHtml(browseUrl), "s=$encodedName&t=audio")
    }

    /**
     * Search audio talks via the FBA API: /api/v1/search?q=TERM&type=audio
     * Returns results matching across titles, speakers, and descriptions.
     */
    suspend fun searchAudio(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        searchByType(query, "audio")
    }

    suspend fun searchSeries(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        searchByType(query, "series")
    }

    private fun searchByType(query: String, type: String): List<SearchResult> {
        val url = "$BASE_URL/api/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", type)
            .build().toString()
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty response")
        }
        val json = JsonParser.parseString(body).asJsonObject
        val searchObj = json.getAsJsonObject("search") ?: return emptyList()
        val items = searchObj.getAsJsonArray("results") ?: return emptyList()
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        for (item in items) {
            if (results.size >= 200) break
            val obj = item.asJsonObject
            val catNum = obj.getStr("cat_num") ?: obj.getStr("catNum") ?: continue
            if (catNum.isBlank() || !seen.add(catNum)) continue
            val link = obj.getStr("link") ?: "/audio/details?num=$catNum"
            results.add(obj.toSearchResult(catNum, link))
        }
        return results
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
        return TranscriptParser.parseTranscriptHtml(html)
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
            results.add(obj.toSearchResult(catNum, path))
        }
        return BrowsePage(results, results.size, "", "", title = seriesTitle)
    }

    // ------------------------------------------------------------------
    // Website content: menu, collections, series, Digital Legacy
    // ------------------------------------------------------------------

    /** Fetch a URL that returns JSON (the /api/v1 endpoints). */
    private suspend fun fetchJson(url: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
            val body = response.body?.string() ?: throw Exception("Empty response: $url")
            JsonParser.parseString(body).asJsonObject
        }
    }

    /** Raw `document.__FBA__.<key>` JSON text (object or array) from a page, or null. */
    private fun extractFbaRaw(html: String, key: String): String? {
        val doc = Jsoup.parse(html)
        for (script in doc.select("script")) {
            val data = script.data()
            val marker = "document.__FBA__.$key"
            var idx = data.indexOf(marker)
            while (idx >= 0) {
                // Skip "document.__FBA__.<key>Something" — match the exact key
                val after = idx + marker.length
                val nextChar = data.getOrNull(after)
                if (nextChar == null || nextChar == ' ' || nextChar == '=') {
                    val eqIdx = data.indexOf('=', idx)
                    if (eqIdx >= 0) {
                        val start = (eqIdx + 1 until data.length).firstOrNull { !data[it].isWhitespace() } ?: return null
                        return when (data[start]) {
                            '{' -> extractBalanced(data, start, '{', '}')
                            '[' -> extractBalanced(data, start, '[', ']')
                            else -> null
                        }
                    }
                }
                idx = data.indexOf(marker, idx + 1)
            }
        }
        return null
    }

    /** The website's curated side menu (collections, sangharakshita, themes, people, places, languages…). */
    suspend fun fetchSiteMenu(): List<MenuNode> {
        val html = fetchHtml("$BASE_URL/")
        val raw = extractFbaRaw(html, "sidebar_menu") ?: return emptyList()
        return SiteMenuParser.parse(raw)
    }

    /** Whether the site considers the current session logged in, and the user object if so. */
    fun parseLoggedInUser(html: String): JsonObject? {
        val raw = extractFbaRaw(html, "user") ?: return null
        return try { JsonParser.parseString(raw).asJsonObject } catch (_: Exception) { null }
    }

    suspend fun fetchHomepageHtml(): String = fetchHtml("$BASE_URL/")

    /**
     * One page of an API collection: latest, introductions, guided_introductions,
     * speakers, places, years, themes, series_latest, all_series, series_sangharakshita…
     * `limit` is the only page-size parameter the server honours.
     */
    suspend fun fetchApiCollectionPage(type: String, page: Int, title: String = ""): ListPage {
        val url = "$BASE_URL/api/v1/collections/${java.net.URLEncoder.encode(type, "UTF-8")}?page=$page&limit=${ListPage.PAGE_SIZE}"
        val coll = fetchJson(url).getAsJsonObject("collection") ?: return ListPage(emptyList(), 0, page, title)
        return ListPage(
            items = parseListItems(coll.getAsJsonArray("items")),
            totalItems = coll.getInt("total_items") ?: coll.getStr("total_items")?.toIntOrNull() ?: 0,
            page = page,
            title = title.ifBlank { unescape(coll.getStr("label") ?: type) },
        )
    }

    /**
     * Images for a whole index collection (speakers, places): browse path →
     * image URL, skipping the site's placeholder images. One request; the
     * server honours limit=1000.
     */
    suspend fun fetchIndexImages(type: String): Map<String, String> {
        val url = "$BASE_URL/api/v1/collections/${java.net.URLEncoder.encode(type, "UTF-8")}?page=1&limit=1000"
        val coll = fetchJson(url).getAsJsonObject("collection") ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        coll.getAsJsonArray("items")?.forEach { el ->
            val obj = el.asJsonObject
            val path = obj.getStr("url") ?: return@forEach
            val image = obj.getStr("image_url") ?: return@forEach
            if (image.isBlank() || image.contains("/default")) return@forEach
            out[normalizeBrowsePath(path)] = resolveUrl(image)
        }
        return out
    }

    private fun normalizeBrowsePath(link: String): String = normalizeBrowsePathStatic(link)

    /** One page of a `/browse?…` listing (a speaker, place, year or genre). */
    suspend fun fetchBrowsePage(path: String, page: Int, apiUrl: String = "", apiQuery: String = ""): ListPage {
        if (page > 1 && apiUrl.isNotBlank()) {
            val coll = fetchJson("$apiUrl?$apiQuery&page=$page&limit=${ListPage.PAGE_SIZE}")
                .getAsJsonObject("collection") ?: return ListPage(emptyList(), 0, page)
            return ListPage(
                items = parseListItems(coll.getAsJsonArray("items")),
                totalItems = coll.getInt("total_items") ?: coll.getStr("total_items")?.toIntOrNull() ?: 0,
                page = page, apiUrl = apiUrl, apiQuery = apiQuery,
            )
        }
        val resolved = resolveUrl(path)
        val html = fetchHtml(resolved)
        val coll = extractFbaJson(html, "collection") ?: return ListPage(emptyList(), 0, 1)
        val query = resolved.substringAfter('?', "")
        val label = unescape(coll.getStr("label") ?: "")
        return ListPage(
            items = parseListItems(coll.getAsJsonArray("items")),
            totalItems = coll.getInt("total_items") ?: coll.getStr("total_items")?.toIntOrNull() ?: 0,
            page = 1,
            title = browseTitle(query, label),
            apiUrl = coll.getStr("url")?.let { resolveUrl(it) } ?: "",
            apiQuery = query,
        )
    }

    /** "s=Subhuti&t=audio" → "Subhuti"; falls back to the collection label. */
    private fun browseTitle(query: String, label: String): String {
        for (key in listOf("s", "p", "th", "ser", "y")) {
            val v = Regex("(?:^|&)$key=([^&]+)").find(query)?.groupValues?.get(1) ?: continue
            return java.net.URLDecoder.decode(v.replace("+", "%2B"), "UTF-8").replace('_', ' ')
        }
        return label
    }

    /** One page of a curated `/collection/<slug>` page. Pages with `pageNo`. */
    suspend fun fetchNamedCollectionPage(slug: String, page: Int): ListPage {
        val html = fetchHtml("$BASE_URL/collection/${java.net.URLEncoder.encode(slug, "UTF-8")}?pageNo=$page")
        val data = extractFbaJson(html, "collectionData") ?: return ListPage(emptyList(), 0, page)
        val description = data.getStr("description")?.let { htmlToText(it) } ?: ""
        return ListPage(
            items = parseListItems(data.getAsJsonArray("items")),
            totalItems = data.getInt("totalItems") ?: 0,
            page = data.getInt("pageNo") ?: page,
            title = unescape(data.getStr("title") ?: slug),
            description = description,
            imageUrl = resolveUrl(data.getStr("marquee_image") ?: data.getStr("image") ?: ""),
        )
    }

    /** A series page: title, blurb, image, remaster flag and all member talks. */
    suspend fun fetchSeriesPage(path: String): ListPage {
        val html = fetchHtml(resolveUrl(path))
        val series = extractFbaJson(html, "series") ?: return ListPage(emptyList(), 0, 1)
        val members = series.getAsJsonArray("members")
        val items = mutableListOf<SearchResult>()
        members?.forEach { el ->
            val obj = el.asJsonObject
            val catNum = obj.getStr("cat_num") ?: obj.getStr("member_cat_num") ?: return@forEach
            if (catNum.isBlank()) return@forEach
            val link = obj.getStr("link") ?: obj.getStr("url") ?: "/audio/details?num=$catNum"
            items.add(obj.toSearchResult(catNum, link))
        }
        val hasRemaster = series.get("hasRemasteredTalk")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            ?.let { if (it.isBoolean) it.asBoolean else it.asString == "1" || it.asString == "true" } ?: false
        return ListPage(
            items = items,
            totalItems = items.size,
            page = 1,
            title = unescape(series.getStr("title") ?: ""),
            description = series.getStr("blurb")?.let { htmlToText(it) } ?: "",
            imageUrl = resolveUrl(series.getStr("marquee_image") ?: series.getStr("image") ?: series.getStr("speaker_image") ?: ""),
            hasRemaster = hasRemaster,
            omOnly = (series.getInt("om") ?: 0) != 0,
        )
    }

    /** Copy and sample talk of the Digital Legacy page. */
    suspend fun fetchDigitalLegacy(): DigitalLegacy? {
        val html = fetchHtml("$BASE_URL/digital-legacy")
        val page = extractFbaJson(html, "digitalLegacyPage") ?: return null
        val description = page.getStr("descriptionHtml")?.let { htmlToText(it) } ?: ""
        val sample = page.get("sampleTalk")?.takeIf { it.isJsonObject }?.asJsonObject
        val sampleCatNum = sample?.getStr("catNum") ?: sample?.getStr("cat_num") ?: ""
        val seriesPath = Regex("/series/details\\?num=([A-Za-z0-9]+)").find(description + (page.getStr("descriptionHtml") ?: ""))
            ?.value ?: "/series/details?num=X16"
        return DigitalLegacy(
            title = unescape(page.getStr("title") ?: "The Digital Legacy"),
            description = description,
            sampleCatNum = sampleCatNum,
            seriesPath = seriesPath,
        )
    }

    /** Items of any collection/browse listing: talks, series, and speaker/place/year links. */
    private fun parseListItems(items: com.google.gson.JsonArray?): List<SearchResult> {
        if (items == null) return emptyList()
        val out = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        for (el in items) {
            val obj = el.asJsonObject
            val path = obj.getStr("url") ?: obj.getStr("link") ?: continue
            val catNum = obj.getStr("cat_num") ?: obj.getStr("catNum")
                ?: path.substringAfter("num=", "").substringBefore("&").ifBlank { path }
            if (catNum.isBlank() || !seen.add("$path|$catNum")) continue
            // Speaker/place/year tiles carry a count in the title: "Abayanandi (1)"
            val result = obj.toSearchResult(catNum, path)
            out.add(if (path.contains("/browse")) result.copy(title = result.title.replace(Regex("\\s*\\(\\d+\\)$"), "")) else result)
        }
        return out
    }

    /** HTML blurb → readable plain text with paragraph breaks. */
    fun htmlToText(html: String): String {
        if (!html.contains('<')) return unescape(html).trim()
        val doc = Jsoup.parse(html)
        doc.select("p").prepend("\n\n")
        doc.select("br").append("\n")
        return doc.text().replace(Regex("[ \\t]+\\n"), "\n").trim()
    }

    private fun JsonObject.getStr(key: String): String? {
        return if (has(key) && !get(key).isJsonNull && get(key).isJsonPrimitive) get(key).asString else null
    }

    /** Build a SearchResult from a JSON item, given an already-resolved catNum and link path. */
    private fun JsonObject.toSearchResult(catNum: String, path: String): SearchResult =
        SearchResult(
            catNum = catNum,
            title = unescape(getStr("title") ?: ""),
            speaker = unescape(getStr("speaker") ?: getStr("author") ?: ""),
            imageUrl = resolveUrl(getStr("image_url") ?: getStr("image") ?: ""),
            path = resolveUrl(path),
            year = getStr("year")?.toIntOrNull() ?: 0,
            centre = unescape(getStr("centre") ?: ""),
            omOnly = (get("om_only")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.let { p ->
                if (p.isBoolean) p.asBoolean else p.asString == "1"
            } ?: false) || (getStr("om") == "1"),
        )

    private fun JsonObject.getInt(key: String): Int? {
        return if (has(key) && !get(key).isJsonNull) {
            try { get(key).asInt } catch (_: Exception) { null }
        } else null
    }
}
