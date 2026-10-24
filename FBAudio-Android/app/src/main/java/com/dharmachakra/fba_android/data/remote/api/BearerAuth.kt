package com.dharmachakra.fba_android.data.remote.api

import com.dharmachakra.fba_android.data.auth.TokenStore
import com.dharmachakra.fba_android.data.auth.V2Auth
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

private fun Request.isTokenEndpoint() = url.encodedPath.endsWith("/auth/token")

/** Adds `Authorization: Bearer <access token>` to API calls while logged in. */
class BearerInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStore.accessToken()
        if (token == null || request.isTokenEndpoint()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/**
 * On a 401, refreshes the access token once (rotating refresh token) and retries.
 * If the refresh fails the session is cleared (the user is signed out). Skips the
 * token endpoint and bails after one attempt so it can't loop.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val v2Auth: V2Auth,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.isTokenEndpoint() || responseCount(response) >= 2) return null
        if (!v2Auth.refreshBlocking()) {
            tokenStore.clear()
            return null
        }
        val fresh = tokenStore.accessToken() ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
