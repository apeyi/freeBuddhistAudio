package com.dharmachakra.fba_android.data.auth

import android.net.Uri
import android.os.Build
import android.util.Base64
import com.dharmachakra.fba_android.BuildConfig
import com.dharmachakra.fba_android.api.apis.AuthApi
import com.dharmachakra.fba_android.api.models.GrantType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The v2 login: OAuth 2.1 authorization-code + PKCE (RFC 7636) over the website's
 * existing Triratna SSO. The app opens [authorizeUrl] in a Custom Tab; the SSO
 * returns to `fbaudio://auth?code=…`, which [completeAuthorization] exchanges for
 * tokens. Uses a plain client (no bearer/authenticator) so refreshing can't recurse.
 */
@Singleton
class V2Auth @Inject constructor(
    @Named("plainApiClient") plainClient: OkHttpClient,
    private val tokenStore: TokenStore,
) {
    private val authApi = AuthApi(BASE, plainClient)

    data class Pending(val verifier: String, val state: String)

    fun authorizeUrl(): Pair<Uri, Pending> {
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(16)
        val uri = Uri.parse("$BASE/auth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.FBA_CLIENT_KEY)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", s256(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
        return uri to Pending(verifier, state)
    }

    suspend fun completeAuthorization(redirect: Uri, pending: Pending): Boolean = withContext(Dispatchers.IO) {
        val code = redirect.getQueryParameter("code") ?: return@withContext false
        if (redirect.getQueryParameter("state") != pending.state) return@withContext false
        try {
            val t = authApi.token(
                grantType = GrantType.AUTHORIZATION_CODE,
                code = code,
                codeVerifier = pending.verifier,
                redirectUri = REDIRECT_URI,
                deviceName = Build.MODEL,
            )
            tokenStore.save(t.accessToken, t.refreshToken, t.expiresIn)
            true
        } catch (_: Exception) { false }
    }

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val rt = tokenStore.refreshToken() ?: return@withContext false
        try {
            val t = authApi.token(grantType = GrantType.REFRESH_TOKEN, refreshToken = rt)
            tokenStore.save(t.accessToken, t.refreshToken, t.expiresIn)
            true
        } catch (_: Exception) { false }
    }

    /** Blocking refresh for the OkHttp Authenticator (already off the main thread). */
    fun refreshBlocking(): Boolean = runBlocking { refresh() }

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes); SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    private fun s256(verifier: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(d, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val BASE = "https://www.freebuddhistaudio.com/api/v2"
        const val REDIRECT_URI = "fbaudio://auth"
    }
}
