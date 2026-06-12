package com.fba.app.data.repository

import com.fba.app.data.local.TalkDao
import com.fba.app.data.local.TalkEntity
import com.fba.app.data.remote.BrowsePage
import com.fba.app.data.remote.FBAScraper
import com.fba.app.domain.model.BrowseCategory
import com.fba.app.domain.model.SangharakshitaData
import com.fba.app.domain.model.SearchResult
import com.fba.app.domain.model.Talk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class TalkRepository @Inject constructor(
    private val scraper: FBAScraper,
    private val talkDao: TalkDao,
) {
    // In-memory cache for browse results (keyed by browseUrl), capped size
    private val browseCache = LinkedHashMap<String, List<SearchResult>>(16, 0.75f, true)

    init {
        // Prune talks cached more than 30 days ago on startup
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            talkDao.deleteOlderThan(thirtyDaysAgo)
        }
    }
    fun observeCachedTalks(): Flow<List<Talk>> {
        return talkDao.getAllTalks().map { entities -> entities.map { it.toDomain() } }
    }

    fun observeTalk(catNum: String): Flow<Talk?> {
        return talkDao.observeTalk(catNum).map { it?.toDomain() }
    }

    suspend fun getLatestTalks(): BrowsePage {
        return scraper.fetchLatestTalks()
    }

    /**
     * Null-swallowing convenience for best-effort callers (player restore, etc.)
     * where a missing talk and a network failure are handled the same way.
     * UI that can offer a Retry should use [fetchTalkDetail] instead.
     */
    suspend fun getTalkDetail(catNum: String): Talk? {
        return try {
            fetchTalkDetail(catNum)
        } catch (_: Exception) {
            null
        }
    }

    /** Like [getTalkDetail] but propagates network/parse errors to the caller. */
    suspend fun fetchTalkDetail(catNum: String): Talk? {
        // Check cache first
        val cached = talkDao.getTalk(catNum)
        if (cached != null) return cached.toDomain()

        // Fetch from web and cache
        val talk = scraper.fetchTalkDetail(catNum) ?: return null
        talkDao.insertTalk(TalkEntity.fromDomain(talk))
        return talk
    }

    /** General audio search via FBA API. */
    suspend fun searchAudio(query: String): List<SearchResult> {
        return scraper.searchAudio(query)
    }

    /** Series search via FBA API. */
    suspend fun searchSeries(query: String): List<SearchResult> {
        return scraper.searchSeries(query)
    }

    /** Browse all talks by a speaker. Returns BrowsePage with pagination. */
    suspend fun browseBySpeaker(speakerName: String): com.fba.app.data.remote.BrowsePage {
        return scraper.browseBySpeaker(speakerName)
    }

    suspend fun getBrowseCategories(): List<BrowseCategory> {
        return scraper.fetchBrowseCategories()
    }

    suspend fun getTalksByBrowseUrl(browseUrl: String): BrowsePage {
        return scraper.fetchFromBrowseUrl(browseUrl)
    }

    suspend fun fetchMoreItems(apiBaseUrl: String, browseQueryString: String, startIndex: Int, count: Int): List<SearchResult> {
        return scraper.fetchMoreItems(apiBaseUrl, browseQueryString, startIndex, count)
    }

    // Access-ordered LinkedHashMap mutates internal links even on get() — this is a
    // singleton hit from multiple coroutines, so every access must be synchronized.
    fun getCachedBrowse(key: String): List<SearchResult>? = synchronized(browseCache) {
        browseCache[key]
    }

    fun setCachedBrowse(key: String, items: List<SearchResult>) = synchronized(browseCache) {
        if (browseCache.size >= 50) {
            val oldest = browseCache.keys.first()
            browseCache.remove(oldest)
        }
        browseCache[key] = items
    }

    suspend fun fetchTranscript(transcriptUrl: String): String {
        return scraper.fetchTranscript(transcriptUrl)
    }
}
