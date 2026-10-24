package com.dharmachakra.fba_android.data.remote.api

import com.dharmachakra.fba_android.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps the two headers the FBA v2 API requires on every request, exactly as
 * the contract says to (set once on the HTTP layer, not per call):
 *  - `X-FBA-Client`     — the per-app client key (identifies the app; not a secret).
 *  - `X-FBA-App-Version` — this build's version, so the server can downgrade enum
 *    values a newer API knows but this build does not.
 */
class ApiHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-FBA-Client", BuildConfig.FBA_CLIENT_KEY)
            .header("X-FBA-App-Version", BuildConfig.VERSION_NAME)
            .build()
        return chain.proceed(request)
    }
}
