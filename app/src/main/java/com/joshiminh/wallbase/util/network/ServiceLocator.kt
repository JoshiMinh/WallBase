package com.joshiminh.wallbase.util.network

import android.content.Context
import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.data.WallBaseDatabase
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.settingsDataStore
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import com.joshiminh.wallbase.sources.reddit.RedditService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlin.jvm.Volatile

object ServiceLocator {

    private const val USER_AGENT = "WallBase/1.0 (Android)"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) {
                    val applicationContext = context.applicationContext
                    appContext = applicationContext
                    WallBaseDatabase.getInstance(applicationContext)
                }
            }
        }
    }

    private val context: Context
        get() = requireNotNull(appContext) {
            "ServiceLocator.initialize(context) must be called before accessing repositories."
        }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val updatedRequest = request.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(updatedRequest)
            }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }

        builder.build()
    }

    private val redditRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.reddit.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val redditService: RedditService by lazy {
        redditRetrofit.create(RedditService::class.java)
    }

    private val scraper: WebScraper by lazy { JsoupWebScraper() }

    private val database: WallBaseDatabase by lazy {
        WallBaseDatabase.Companion.getInstance(context)
    }

    val wallpaperRepository: WallpaperRepository by lazy {
        WallpaperRepository(
            redditService = redditService,
            webScraper = scraper
        )
    }

    val sourceRepository: SourceRepository by lazy {
        SourceRepository(
            sourceDao = database.sourceDao(),
            wallpaperDao = database.wallpaperDao(),
            localStorage = localStorageCoordinator
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context.settingsDataStore)
    }

    val localStorageCoordinator: LocalStorageCoordinator by lazy {
        LocalStorageCoordinator(context)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            wallpaperDao = database.wallpaperDao(),
            albumDao = database.albumDao(),
            localStorage = localStorageCoordinator
        )
    }
}