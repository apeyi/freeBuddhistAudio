package com.dharmachakra.fba_android.di

import com.dharmachakra.fba_android.api.apis.AuthApi
import com.dharmachakra.fba_android.api.apis.CatalogueApi
import com.dharmachakra.fba_android.api.apis.MeApi
import com.dharmachakra.fba_android.api.apis.SearchApi
import com.dharmachakra.fba_android.api.apis.ServiceApi
import com.dharmachakra.fba_android.data.auth.TokenStore
import com.dharmachakra.fba_android.data.auth.V2Auth
import com.dharmachakra.fba_android.data.remote.api.ApiHeadersInterceptor
import com.dharmachakra.fba_android.data.remote.api.BearerInterceptor
import com.dharmachakra.fba_android.data.remote.api.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * The generated FBA v2 API client. Two OkHttp clients:
 *  - "plainApiClient": the two required headers only. Used by the token endpoint
 *    (login / refresh), so refreshing never recurses through the authenticator.
 *  - "apiClient": the same headers plus a bearer token (when signed in) and a
 *    401 → refresh → retry authenticator. Used by everything else, so logged-in
 *    calls carry the token (resume positions, Order-only content).
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    private const val BASE_URL = "https://www.freebuddhistaudio.com/api/v2"

    @Provides @Singleton @Named("plainApiClient")
    fun providePlainHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ApiHeadersInterceptor())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton @Named("apiClient")
    fun provideApiHttpClient(tokenStore: TokenStore, v2Auth: V2Auth): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ApiHeadersInterceptor())
            .addInterceptor(BearerInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, v2Auth))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun provideServiceApi(@Named("apiClient") client: OkHttpClient): ServiceApi = ServiceApi(BASE_URL, client)

    @Provides @Singleton
    fun provideCatalogueApi(@Named("apiClient") client: OkHttpClient): CatalogueApi = CatalogueApi(BASE_URL, client)

    @Provides @Singleton
    fun provideSearchApi(@Named("apiClient") client: OkHttpClient): SearchApi = SearchApi(BASE_URL, client)

    @Provides @Singleton
    fun provideMeApi(@Named("apiClient") client: OkHttpClient): MeApi = MeApi(BASE_URL, client)

    @Provides @Singleton
    fun provideAuthApi(@Named("apiClient") client: OkHttpClient): AuthApi = AuthApi(BASE_URL, client)
}
