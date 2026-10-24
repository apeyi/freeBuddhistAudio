package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.api.apis.CatalogueApi
import com.dharmachakra.fba_android.api.apis.SearchApi
import com.dharmachakra.fba_android.api.apis.ServiceApi
import com.dharmachakra.fba_android.api.models.ListItemKind
import com.dharmachakra.fba_android.api.models.ListKind
import com.dharmachakra.fba_android.data.remote.api.ApiHeadersInterceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Drives the generated v2 client against the LIVE API, through the same header
 * interceptor the app uses (so X-FBA-Client / X-FBA-App-Version are real). Proves
 * the Phase 1 wiring end-to-end on a device. Needs network + the client key in
 * the build (local.properties → BuildConfig.FBA_CLIENT_KEY).
 */
class ApiClientLiveTest {
    companion object {
        private const val BASE = "https://www.freebuddhistaudio.com/api/v2"
        private lateinit var client: OkHttpClient

        @BeforeClass @JvmStatic
        fun setUp() {
            client = OkHttpClient.Builder().addInterceptor(ApiHeadersInterceptor()).build()
        }
    }

    private fun service() = ServiceApi(BASE, client)
    private fun catalogue() = CatalogueApi(BASE, client)
    private fun search() = SearchApi(BASE, client)

    @Test
    fun metaReportsV2() {
        val meta = service().getMeta()
        assertTrue("apiVersion was ${meta.apiVersion}", meta.apiVersion.startsWith("2"))
        assertEquals("1.0.0", meta.minAppVersion.android)
    }

    @Test
    fun latestListReturnsPlayableTalks() {
        val page = catalogue().getList(ListKind.LATEST, 1, 5)
        assertTrue("no items", page.items.isNotEmpty())
        assertEquals(ListItemKind.TALK, page.items.first().kind)
        assertTrue("paging total should be > 5", (page.paging.total) > 5)
    }

    @Test
    fun menuComesBackAsATree() {
        val menu = service().getMenu()
        assertTrue("menu empty", menu.items.isNotEmpty())
        // every node carries a webUrl fallback per the contract
        assertTrue(menu.items.all { it.label.isNotBlank() })
    }

    @Test
    fun searchFindsMetta() {
        val overview = search().searchOverview("metta", null)
        assertTrue("no talk hits", overview.counts.talk > 0)
    }
}
