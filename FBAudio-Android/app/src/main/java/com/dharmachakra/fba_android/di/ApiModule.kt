package com.dharmachakra.fba_android.di

import com.dharmachakra.fba_android.api.apis.CatalogueApi
import com.dharmachakra.fba_android.api.apis.MeApi
import com.dharmachakra.fba_android.api.apis.SearchApi
import com.dharmachakra.fba_android.api.apis.ServiceApi
import com.dharmachakra.fba_android.data.remote.api.ApiHeadersInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * The generated FBA v2 API client (`com.dharmachakra.fba_android.api`). Uses its
 * own lean OkHttp client — just the two required headers, no session cookie jar,
 * since the read-only catalogue is anonymous. The bearer token for the `me` endpoints
 * arrives with the Phase 2 login.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    private const val BASE_URL = "https://www.freebuddhistaudio.com/api/v2"

    @Provides
    @Singleton
    @Named("apiClient")
    fun provideApiHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ApiHeadersInterceptor())
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
}
