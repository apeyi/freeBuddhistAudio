package com.dharmachakra.fba_android.data.repository

import com.dharmachakra.fba_android.data.local.TalkDao
import com.dharmachakra.fba_android.data.local.TalkEntity
import com.dharmachakra.fba_android.data.remote.BrowsePage
import com.dharmachakra.fba_android.data.remote.FBAScraper
import com.dharmachakra.fba_android.domain.model.BrowseCategory
import com.dharmachakra.fba_android.domain.model.SangharakshitaData
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.domain.model.Talk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

class TalkRepository @Inject constructor(
    private val scraper: FBAScraper,
    private val v2: com.dharmachakra.fba_android.data.remote.api.V2Catalogue,
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

    /**
     * Like [getTalkDetail] but propagates network/parse errors to the caller.
     * [forceRefresh] bypasses the cache — used when logged in, so the page carries
     * the account's saved position and Order-only visibility.
     */
    suspend fun fetchTalkDetail(catNum: String, forceRefresh: Boolean = false): Talk? {
        if (!forceRefresh) {
            // Entries cached before remastered audio / track ids were parsed are
            // refetched once (cachedAt predates TALK_CACHE_EPOCH).
            val cached = talkDao.getTalk(catNum)
            if (cached != null && cached.cachedAt >= TALK_CACHE_EPOCH) return cached.toDomain()
        }

        // Fetch from web, fill chapter lengths the site lacks, add the v2 artwork/place
        // fields the talk page needs, and cache. The v2 call runs alongside the scrape and
        // is best-effort: Order-only (403) or a network blip leaves the scraped talk as is.
        val talk = kotlinx.coroutines.coroutineScope {
            val extras = async { runCatching { v2.getTalkExtras(catNum) }.getOrNull() }
            val scraped = scraper.fetchTalkDetail(catNum)?.let { scraper.fillMissingTrackDurations(it) }
            scraped?.withExtras(extras.await()) ?: run { extras.cancel(); null }
        } ?: return null
        talkDao.insertTalk(TalkEntity.fromDomain(talk))
        return talk
    }

    /** Talks related to [catNum] (v2), for the talk page's Related talks tab and Up next. */
    suspend fun getRelatedTalks(catNum: String): List<SearchResult> = v2.getRelatedTalks(catNum)

    companion object {
        /** 2026-11-08: talks now carry banner / speaker image / place from v2 — older cache rows are stale. */
        const val TALK_CACHE_EPOCH = 1_794_163_023_254L

        /** Merge v2 extras into a scraped talk; scraped series info wins when present. */
        fun Talk.withExtras(e: com.dharmachakra.fba_android.data.remote.api.V2Catalogue.TalkExtras?): Talk {
            if (e == null) return this
            return copy(
                bannerUrl = e.bannerUrl,
                speakerImageUrl = e.speakerImageUrl,
                placeName = e.placeName,
                placeSlug = e.placeSlug,
                series = series.ifBlank { e.seriesTitle },
                seriesHref = seriesHref.ifBlank { e.seriesCatNum.takeIf { it.isNotBlank() }?.let { "/series/details?num=$it" } ?: "" },
            )
        }
    }

    /** General audio search via FBA API. */
    suspend fun searchAudio(query: String, language: String? = null): List<SearchResult> {
        return v2.searchTalks(query, language)
    }

    /** Series search via FBA API. */
    suspend fun searchSeries(query: String): List<SearchResult> {
        return v2.searchSeries(query)
    }

    /** Browse all talks by a speaker. Returns BrowsePage with pagination. */
    suspend fun browseBySpeaker(speakerName: String): com.dharmachakra.fba_android.data.remote.BrowsePage {
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
