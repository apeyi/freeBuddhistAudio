package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.api.apis.CatalogueApi
import com.dharmachakra.fba_android.data.remote.api.ApiHeadersInterceptor
import com.dharmachakra.fba_android.data.remote.api.V2Catalogue
import com.dharmachakra.fba_android.domain.model.ContentSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the Phase 1 catalogue migration end-to-end against the LIVE API:
 * ContentSource -> V2Catalogue -> generated client -> mapped back to the app's
 * ListPage/SearchResult. Device + network + client key required.
 */
class V2CatalogueLiveTest {
    private val base = "https://www.freebuddhistaudio.com/api/v2"
    private val client = OkHttpClient.Builder().addInterceptor(ApiHeadersInterceptor()).build()
    private val v2 = V2Catalogue(
        com.dharmachakra.fba_android.api.apis.CatalogueApi(base, client),
        com.dharmachakra.fba_android.api.apis.ServiceApi(base, client),
        com.dharmachakra.fba_android.api.apis.SearchApi(base, client),
    )

    @Test fun latestListMapsToTalks() = runBlocking {
        val page = v2.getPage(ContentSource.ApiCollection("latest", "Latest"), 1)
        assertTrue("no items", page.items.isNotEmpty())
        assertTrue("first should be a talk", page.items.first().isTalk)
        assertTrue("paging total", page.totalItems > page.items.size)
        assertTrue("has a title", page.title.isNotBlank())
    }

    @Test fun allSeriesMapsToSeries() = runBlocking {
        val page = v2.getPage(ContentSource.ApiCollection("all_series", "Series"), 1)
        assertTrue("no series", page.items.isNotEmpty())
        assertTrue("first should be a series", page.items.first().isSeries)
    }

    @Test fun browseBySpeakerMapsToTalks() = runBlocking {
        val page = v2.getPage(ContentSource.Browse("/browse?s=Sangharakshita"), 1)
        assertTrue("no talks for speaker", page.items.isNotEmpty())
        assertTrue("speaker talks are talks", page.items.all { it.isTalk })
    }

    @Test fun seriesPageMapsToMembers() = runBlocking {
        val page = v2.getPage(ContentSource.seriesByCatNum("X16"), 1)
        assertTrue("series has no members", page.items.isNotEmpty())
        assertTrue("members are talks", page.items.first().isTalk)
    }

    @Test fun speakerIndexMaps() = runBlocking {
        val page = v2.getPage(ContentSource.ApiCollection("speakers", "Speakers"), 1)
        assertTrue("no speakers", page.items.isNotEmpty())
        assertTrue("speaker rows are browse links", page.items.first().isBrowseLink)
    }

    @Test fun menuMapsToSections() = runBlocking {
        val menu = v2.getMenu()
        assertTrue("menu empty", menu.isNotEmpty())
        val labels = menu.map { it.label.lowercase() }
        assertTrue("no people/places section", labels.any { it == "people" } && labels.any { it == "places" })
    }

    @Test fun searchTalksAndSeries() = runBlocking {
        assertTrue("no talk hits", v2.searchTalks("metta").isNotEmpty())
        assertTrue("no series hits", v2.searchSeries("meditation").any { it.isSeries })
    }

    @Test fun digitalLegacyHasSample() = runBlocking {
        val dl = v2.getDigitalLegacy()
        assertTrue("no title", dl.title.isNotBlank())
        assertTrue("no sample catNum", dl.sampleCatNum.isNotBlank())
    }

    @Test fun speakerIndexIsWhole() = runBlocking {
        val speakers = v2.getIndexEntries("speakers")
        assertTrue("speaker index too small (${speakers.size})", speakers.size > 300)
        assertTrue("index rows are browse links", speakers.first().isBrowseLink)
    }
}
