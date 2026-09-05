package com.fba.app

import com.fba.app.data.auth.SessionCookieStore
import com.fba.app.data.auth.SsoLogin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live check of the native single sign-on against the real FBA/Triratna servers.
 * Skipped unless FBA_NAME and FBA_PASSWORD are set in the environment — run
 * locally to confirm the flow still matches the website:
 *   FBA_NAME=… FBA_PASSWORD=… ./gradlew testDebugUnitTest --tests '*SsoLogin*'
 */
class SsoLoginIntegrationTest {

    private val username = System.getenv("FBA_NAME")
    private val password = System.getenv("FBA_PASSWORD")

    @Test
    fun logsInAndReturnsCompleteSession() = runBlocking {
        assumeTrue("FBA_NAME/FBA_PASSWORD not set — skipping live SSO test", !username.isNullOrBlank() && !password.isNullOrBlank())
        val result = SsoLogin(OkHttpClient()).login(username!!, password!!)
        assertTrue("expected Success, got $result", result is SsoLogin.Result.Success)
        val cookies = (result as SsoLogin.Result.Success).cookies
        assertTrue(SessionCookieStore.isCompleteSession(cookies))
    }

    @Test
    fun wrongPasswordIsReportedAsInvalidCredentials() = runBlocking {
        assumeTrue("FBA_NAME not set — skipping live SSO test", !username.isNullOrBlank())
        val result = SsoLogin(OkHttpClient()).login(username!!, "definitely-not-the-password")
        assertEquals(SsoLogin.Result.InvalidCredentials, result)
    }
}
