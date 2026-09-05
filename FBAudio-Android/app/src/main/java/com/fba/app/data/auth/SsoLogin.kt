package com.fba.app.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Performs the FBA website's Triratna single sign-on with a username and
 * password, without showing the (unstyled) web form:
 *
 *  1. GET the FBA login URL → redirects to the SSO login page (sso.triratna.co)
 *  2. POST username + password to that form
 *  3. The SSO answers with an auto-submitting form carrying the SAML response →
 *     POST it back to FBA, which sets the session cookies
 *
 * Runs on a private cookie jar so the app's own session store is only updated
 * once the whole flow has succeeded. Pure OkHttp + Jsoup (no Android types) so
 * the flow can be exercised from a JVM test.
 */
class SsoLogin(baseClient: OkHttpClient) {

    sealed class Result {
        /** The FBA session cookies (PHPSESSID, SimpleSAMLAuthToken, fba). */
        data class Success(val cookies: Map<String, String>) : Result()
        object InvalidCredentials : Result()
        data class Failure(val message: String) : Result()
    }

    private val jar = MemoryCookieJar()
    private val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(jar)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun login(username: String, password: String): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Land on the SSO login form
            val (formUrl, formHtml) = get(AuthRepository.LOGIN_URL)
            val form = Jsoup.parse(formHtml, formUrl).selectFirst("form:has(input[name=username])")
                ?: return@withContext Result.Failure("Login form not found")

            // 2. Submit credentials
            val fields = hiddenFields(form) + mapOf("username" to username, "password" to password)
            var (url, html) = post(form.absAction(formUrl), fields)

            // 3. Relay the SAML response back to FBA (at most a couple of hops)
            var hops = 0
            while (hops < 3) {
                val saml = Jsoup.parse(html, url).selectFirst("form:has(input[name=SAMLResponse])") ?: break
                val r = post(saml.absAction(url), hiddenFields(saml))
                url = r.first; html = r.second
                hops++
            }

            val cookies = jar.cookiesFor(SessionCookieStore.HOST)
            when {
                SessionCookieStore.isCompleteSession(cookies) -> Result.Success(cookies)
                html.contains("not recognised", ignoreCase = true) ||
                    Jsoup.parse(html).selectFirst("form:has(input[name=username])") != null -> Result.InvalidCredentials
                else -> Result.Failure("Sign-on did not complete")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Network error")
        }
    }

    private fun get(url: String): Pair<String, String> =
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            r.request.url.toString() to (r.body?.string() ?: "")
        }

    private fun post(url: String, fields: Map<String, String>): Pair<String, String> {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return client.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            r.request.url.toString() to (r.body?.string() ?: "")
        }
    }

    private fun hiddenFields(form: Element): Map<String, String> =
        form.select("input[name]").toList().filter { it.attr("type") != "submit" }
            .associate { it.attr("name") to it.attr("value") }

    private fun Element.absAction(pageUrl: String): String {
        val action = attr("action")
        // Empty / query-only actions post back to the current page
        return if (action.isBlank() || action.startsWith("?")) {
            pageUrl.substringBefore('?') + action
        } else absUrl("action").ifBlank { pageUrl.toHttpUrl().resolve(action)?.toString() ?: pageUrl }
    }

    /** Per-host cookie jar for the duration of one login. */
    private class MemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, MutableMap<String, Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (c in cookies) store.getOrPut(c.domain) { mutableMapOf() }[c.name] = c
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            store.filterKeys { domain -> url.host == domain || url.host.endsWith(".$domain") }
                .values.flatMap { it.values }.filter { it.matches(url) }

        fun cookiesFor(host: String): Map<String, String> =
            store.filterKeys { host == it || host.endsWith(".$it") }
                .values.flatMap { it.values }.associate { it.name to it.value }
    }
}
