package com.fba.app.data.auth

import com.fba.app.data.remote.FBAScraper
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class AuthState(
    val loggedIn: Boolean = false,
    val username: String = "",
    val avatarUrl: String = "",
    val isOrderMember: Boolean = false,
    /** True while the stored session is being verified at startup. */
    val checking: Boolean = false,
)

/**
 * Login with the existing FBA (Triratna single sign-on) account, done through the
 * website's own login page in a WebView. The captured session cookies are then
 * attached to every website request (see [SessionCookieStore]).
 *
 * To be replaced by a token-based login when the FBA API provides one — keep
 * callers on [state] / [logout] / [completeLoginFromWebView] only.
 */
class AuthRepository(
    private val store: SessionCookieStore,
    private val scraper: FBAScraper,
    private val client: OkHttpClient,
) {
    private val _state = MutableStateFlow(AuthState(loggedIn = store.loggedIn.value, checking = store.loggedIn.value))
    val state: StateFlow<AuthState> = _state

    val isLoggedIn: Boolean get() = _state.value.loggedIn

    companion object {
        const val LOGIN_URL = "https://www.freebuddhistaudio.com/sso/?login=true&returnTo=https://www.freebuddhistaudio.com/"
        const val LOGOUT_URL = "https://www.freebuddhistaudio.com/user/logout"
        private const val MY_DETAILS_URL = "https://www.freebuddhistaudio.com/api/v1/my-details"
    }

    /**
     * Called by the login WebView once it has landed back on the site with a
     * complete session. Verifies the session and loads the user header.
     */
    suspend fun completeLoginFromWebView(cookies: Map<String, String>): Boolean {
        if (!SessionCookieStore.isCompleteSession(cookies)) return false
        store.setCookies(cookies)
        store.setLoggedIn(true)
        return refresh()
    }

    /**
     * Re-check the stored session with the site and refresh the user header.
     * A session the site no longer accepts logs the user out.
     */
    suspend fun refresh(): Boolean {
        if (!store.loggedIn.value) {
            _state.value = AuthState()
            return false
        }
        _state.value = _state.value.copy(checking = true)
        return try {
            val details = fetchMyDetails()
            val siteLoggedIn = details?.get("loggedIn")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            if (!siteLoggedIn) {
                logout(clearRemote = false)
                false
            } else {
                val user = try { scraper.parseLoggedInUser(scraper.fetchHomepageHtml()) } catch (_: Exception) { null }
                _state.value = AuthState(
                    loggedIn = true,
                    username = user?.str("username") ?: user?.str("name") ?: _state.value.username,
                    avatarUrl = user?.str("profileImageUrl") ?: "",
                    isOrderMember = user?.get("isOrderMember")?.let { it.isJsonPrimitive && it.asJsonPrimitive.let { p -> p.isBoolean && p.asBoolean || p.isNumber && p.asInt != 0 } } ?: false,
                )
                true
            }
        } catch (_: Exception) {
            // Network trouble: keep the session, stop showing the spinner.
            _state.value = _state.value.copy(loggedIn = true, checking = false)
            true
        }
    }

    /** Forget the session. [clearRemote] also ends the website session. */
    suspend fun logout(clearRemote: Boolean = true) {
        if (clearRemote && store.loggedIn.value) {
            try {
                withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(LOGOUT_URL).build()).execute().close()
                }
            } catch (_: Exception) { /* best effort */ }
        }
        store.clear()
        _state.value = AuthState()
    }

    private suspend fun fetchMyDetails(): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(MY_DETAILS_URL).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) return@withContext JsonObject().apply { addProperty("loggedIn", false) }
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body?.string() ?: return@withContext null
            if (!body.trimStart().startsWith("{")) return@withContext JsonObject().apply { addProperty("loggedIn", false) }
            com.google.gson.JsonParser.parseString(body).asJsonObject
        }
    }

    private fun JsonObject.str(key: String): String? =
        if (has(key) && !get(key).isJsonNull && get(key).isJsonPrimitive) get(key).asString else null
}
