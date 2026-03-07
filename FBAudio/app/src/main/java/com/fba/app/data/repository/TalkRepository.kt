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
import javax.inject.Inject

class TalkRepository @Inject constructor(
    private val scraper: FBAScraper,
    private val talkDao: TalkDao,
) {
    // In-memory cache for browse results (keyed by browseUrl)
    private val browseCache = mutableMapOf<String, List<SearchResult>>()
    fun observeCachedTalks(): Flow<List<Talk>> {
        return talkDao.getAllTalks().map { entities -> entities.map { it.toDomain() } }
    }

    fun observeTalk(catNum: String): Flow<Talk?> {
        return talkDao.observeTalk(catNum).map { it?.toDomain() }
    }

    suspend fun getLatestTalks(): BrowsePage {
        return scraper.fetchLatestTalks()
    }

    suspend fun getTalkDetail(catNum: String): Talk? {
        // Check cache first
        val cached = talkDao.getTalk(catNum)
        if (cached != null) return cached.toDomain()

        // Fetch from web and cache
        return try {
            val talk = scraper.fetchTalkDetail(catNum) ?: return null
            talkDao.insertTalk(TalkEntity.fromDomain(talk))
            talk
        } catch (_: Exception) {
            null
        }
    }

    /** General audio search via FBA API. */
    suspend fun searchAudio(query: String): List<SearchResult> {
        return scraper.searchAudio(query)
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

    fun getCachedBrowse(key: String): List<SearchResult>? = browseCache[key]
    fun setCachedBrowse(key: String, items: List<SearchResult>) { browseCache[key] = items }

    suspend fun fetchTranscript(transcriptUrl: String): String {
        return scraper.fetchTranscript(transcriptUrl)
    }
}
