package com.fba.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * The FBA website session: the three cookies the site sets after the Triratna
 * single sign-on (PHPSESSID, SimpleSAMLAuthToken, fba), kept only while the
 * user is logged in. Anonymous visits also receive PHPSESSID/fba cookies, so
 * the presence of cookies never implies a login — [loggedIn] is set explicitly
 * once the site confirms the session.
 */
class SessionCookieStore(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("fba_session", Context.MODE_PRIVATE)

    private val _loggedIn = MutableStateFlow(prefs.getBoolean(KEY_LOGGED_IN, false))
    val loggedIn: StateFlow<Boolean> = _loggedIn

    fun cookies(): Map<String, String> =
        SESSION_COOKIES.mapNotNull { name -> prefs.getString("c_$name", null)?.let { name to it } }.toMap()

    fun setCookies(cookies: Map<String, String>) {
        val editor = prefs.edit()
        for ((name, value) in cookies) if (name in SESSION_COOKIES) editor.putString("c_$name", value)
        editor.apply()
    }

    fun setLoggedIn(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()
        _loggedIn.value = value
    }

    fun clear() {
        prefs.edit().clear().apply()
        _loggedIn.value = false
    }

    // --- OkHttp CookieJar: attach the session to every website request, follow rotations ---

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!_loggedIn.value || !url.host.endsWith(HOST_SUFFIX)) return emptyList()
        return cookies().map { (name, value) ->
            Cookie.Builder().name(name).value(value).domain(HOST).path("/").build()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Only track rotations of an existing login; anonymous sessions are not kept.
        if (!_loggedIn.value || !url.host.endsWith(HOST_SUFFIX)) return
        val updates = cookies.filter { it.name in SESSION_COOKIES }.associate { it.name to it.value }
        if (updates.isNotEmpty()) setCookies(updates)
    }

    companion object {
        const val HOST = "www.freebuddhistaudio.com"
        private const val HOST_SUFFIX = "freebuddhistaudio.com"
        val SESSION_COOKIES = setOf("PHPSESSID", "SimpleSAMLAuthToken", "fba")
        private const val KEY_LOGGED_IN = "logged_in"

        /** Parse a WebView "k=v; k2=v2" cookie header into a map. */
        fun parseCookieHeader(header: String?): Map<String, String> =
            header.orEmpty().split(';').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
            }.toMap()

        /** The login is complete once the SAML token and the site session are both present. */
        fun isCompleteSession(cookies: Map<String, String>): Boolean =
            cookies.containsKey("SimpleSAMLAuthToken") && cookies.containsKey("fba") && cookies.containsKey("PHPSESSID")
    }
}
