package com.dharmachakra.fba_android

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dharmachakra.fba_android.api.apis.CatalogueApi
import com.dharmachakra.fba_android.api.apis.MeApi
import com.dharmachakra.fba_android.data.auth.TokenStore
import com.dharmachakra.fba_android.data.auth.V2Auth
import com.dharmachakra.fba_android.data.remote.api.ApiHeadersInterceptor
import com.dharmachakra.fba_android.data.remote.api.BearerInterceptor
import com.dharmachakra.fba_android.data.remote.api.TokenAuthenticator
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end v2 login against the LIVE API with the test account. Drives the
 * authorize → Triratna SSO → `fbaudio://auth?code=` redirect headlessly (the
 * Custom Tab's job in the real app), then exchanges, reads /me, fetches an
 * authed talk and refreshes — proving the whole Phase 2 chain.
 *
 * Creds come from instrumentation args:
 *   am instrument -e fba_name <user> -e fba_pass <pass> -e class …V2AuthLiveTest …
 */
class V2AuthLiveTest {
    private val args get() = InstrumentationRegistry.getArguments()
    private val user get() = args.getString("fba_name")
    private val pass get() = args.getString("fba_pass")

    private lateinit var tokenStore: TokenStore
    private lateinit var v2Auth: V2Auth

    private val plainClient = OkHttpClient.Builder().addInterceptor(ApiHeadersInterceptor()).build()

    @Before fun setUp() {
        assumeTrue("creds not passed", !user.isNullOrBlank() && !pass.isNullOrBlank())
        // Skip until FBA ships the v2 auth section (/auth/* and /me are 404 today).
        val probe = okhttp3.Request.Builder().url("${V2Auth.BASE}/auth/authorize").head().build()
        val code = plainClient.newCall(probe).execute().use { it.code }
        assumeTrue("v2 auth endpoints not live yet (HTTP $code)", code != 404)
        tokenStore = TokenStore(ApplicationProvider.getApplicationContext())
        tokenStore.clear()
        v2Auth = V2Auth(plainClient, tokenStore)
    }

    @Test fun fullLoginFlow() = runBlocking {
        val (authorizeUri, pending) = v2Auth.authorizeUrl()
        val code = headlessAuthorize(authorizeUri.toString())
        val redirect = Uri.parse("${V2Auth.REDIRECT_URI}?code=$code&state=${pending.state}")
        assertTrue("code exchange failed", v2Auth.completeAuthorization(redirect, pending))
        assertTrue("no tokens stored", tokenStore.isLoggedIn)

        // authed client (bearer + refresh), as the app wires it
        val authed = OkHttpClient.Builder()
            .addInterceptor(ApiHeadersInterceptor())
            .addInterceptor(BearerInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, v2Auth))
            .build()

        val me = MeApi(V2Auth.BASE, authed).getMe()
        assertTrue("username blank", me.username.isNotBlank())

        // an authed talk fetch should succeed (and may carry a checkpoint)
        val talk = CatalogueApi(V2Auth.BASE, authed).getTalk("LOC3883")
        assertTrue("talk has no tracks", talk.tracks.isNotEmpty())

        // refresh rotates and still works
        assertTrue("refresh failed", v2Auth.refresh())
        assertTrue("still logged in after refresh", tokenStore.isLoggedIn)
    }

    /** Follow authorize redirects + SSO forms until the fbaudio:// redirect, return the code. */
    private fun headlessAuthorize(startUrl: String): String {
        val jar = object : CookieJar {
            val store = HashMap<String, MutableMap<String, Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookies.forEach { store.getOrPut(it.domain) { mutableMapOf() }[it.name] = it }
            }
            override fun loadForRequest(url: HttpUrl) =
                store.filterKeys { url.host == it || url.host.endsWith(".$it") }
                    .values.flatMap { it.values }.filter { it.matches(url) }
        }
        val client = OkHttpClient.Builder().cookieJar(jar).followRedirects(false).followSslRedirects(false).build()
        var url = startUrl
        var request = Request.Builder().url(url).build()
        repeat(20) {
            client.newCall(request).execute().use { resp ->
                if (resp.code in 300..399) {
                    val loc = resp.header("Location") ?: error("redirect without Location at $url")
                    val abs = resp.request.url.resolve(loc)?.toString() ?: loc
                    if (abs.startsWith("fbaudio://")) return Uri.parse(abs).getQueryParameter("code") ?: error("no code in $abs")
                    url = abs; request = Request.Builder().url(url).build()
                } else {
                    val base = resp.request.url.toString()
                    val doc = Jsoup.parse(resp.body?.string() ?: "", base)
                    val login = doc.selectFirst("form:has(input[name=username])")
                    val saml = doc.selectFirst("form:has(input[name=SAMLResponse])")
                    val form = login ?: saml ?: error("stuck: no redirect and no form at $base")
                    val action = form.absUrl("action").ifBlank { base }
                    val fields = form.select("input[name]").toList().filter { it.attr("type") != "submit" }
                        .associate { it.attr("name") to it.attr("value") }
                        .toMutableMap()
                    if (login != null) { fields["username"] = user!!; fields["password"] = pass!! }
                    val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
                    url = action; request = Request.Builder().url(url).post(body).build()
                }
            }
        }
        error("authorize did not reach the redirect in 20 hops")
    }
}
