package com.fba.app.di

import android.content.Context
import com.fba.app.data.local.FBADatabase
import com.fba.app.data.local.TalkDao
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.RecentlyListenedDao
import com.fba.app.data.remote.FBAScraper
import com.fba.app.data.repository.TalkRepository
import com.fba.app.data.repository.DownloadRepository
import com.fba.app.data.repository.ContentRepository
import com.fba.app.data.local.AppSettings
import com.fba.app.data.local.ContentCache
import com.fba.app.data.auth.AuthRepository
import com.fba.app.data.auth.MembershipRepository
import com.fba.app.data.auth.SessionCookieStore
import com.fba.app.data.repository.HistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.fba.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Application-lifetime scope for persistence writes that must outlive the
     * ViewModel that issued them (e.g. the final playback-position save when
     * the player's ViewModel is cleared — viewModelScope cancels those mid-write).
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideSessionCookieStore(@ApplicationContext context: Context): SessionCookieStore =
        SessionCookieStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(sessionCookieStore: SessionCookieStore): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Attaches the FBA login session (when logged in) to website requests.
            .cookieJar(sessionCookieStore)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Referer", "https://www.freebuddhistaudio.com/")
                    .build()
                chain.proceed(request)
            }
            .followRedirects(true)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                            else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FBADatabase {
        return FBADatabase.create(context)
    }

    @Provides
    fun provideTalkDao(db: FBADatabase): TalkDao = db.talkDao()

    @Provides
    fun provideDownloadDao(db: FBADatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideRecentlyListenedDao(db: FBADatabase): RecentlyListenedDao = db.recentlyListenedDao()

    @Provides
    @Singleton
    fun provideFBAScraper(client: OkHttpClient): FBAScraper {
        return FBAScraper(client)
    }

    @Provides
    @Singleton
    fun provideTalkRepository(
        scraper: FBAScraper,
        talkDao: TalkDao,
    ): TalkRepository {
        return TalkRepository(scraper, talkDao)
    }

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings = AppSettings(context)

    @Provides
    @Singleton
    fun provideContentCache(@ApplicationContext context: Context): ContentCache = ContentCache(context)

    @Provides
    @Singleton
    fun provideContentRepository(
        scraper: FBAScraper,
        cache: ContentCache,
        settings: AppSettings,
    ): ContentRepository = ContentRepository(scraper, cache, settings)

    @Provides
    @Singleton
    fun provideAuthRepository(
        store: SessionCookieStore,
        scraper: FBAScraper,
        client: OkHttpClient,
    ): AuthRepository = AuthRepository(store, scraper, client)

    @Provides
    @Singleton
    fun provideMembershipRepository(): MembershipRepository = MembershipRepository()

    @Provides
    @Singleton
    fun provideHistoryRepository(
        client: OkHttpClient,
        auth: AuthRepository,
        recentlyListenedDao: RecentlyListenedDao,
    ): HistoryRepository = HistoryRepository(client, auth, recentlyListenedDao)

    @Provides
    @Singleton
    fun provideDownloadRepository(
        downloadDao: DownloadDao,
        @ApplicationContext context: Context,
    ): DownloadRepository {
        return DownloadRepository(downloadDao, context)
    }
}
