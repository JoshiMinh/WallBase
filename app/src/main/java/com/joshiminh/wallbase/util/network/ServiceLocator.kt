package com.joshiminh.wallbase.util.network

import android.content.Context
import androidx.work.WorkManager
import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.data.WallBaseDatabase
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.repository.UpdateRepository
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import com.joshiminh.wallbase.data.repository.WallpaperRotationRepository
import com.joshiminh.wallbase.data.repository.settingsDataStore
import com.joshiminh.wallbase.sources.danbooru.DanbooruService
import com.joshiminh.wallbase.sources.reddit.RedditService
import com.joshiminh.wallbase.sources.unsplash.UnsplashService
import com.joshiminh.wallbase.sources.wallhaven.WallhavenService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.jvm.Volatile
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

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

    fun ensureInitialized(context: Context) {
        if (appContext == null) {
            initialize(context)
        }
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

    private val wallhavenRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://wallhaven.cc/api/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val wallhavenService: WallhavenService by lazy {
        wallhavenRetrofit.create(WallhavenService::class.java)
    }

    private val danbooruRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://danbooru.donmai.us/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val danbooruService: DanbooruService by lazy {
        danbooruRetrofit.create(DanbooruService::class.java)
    }

    private val unsplashRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://unsplash.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val unsplashService: UnsplashService by lazy {
        unsplashRetrofit.create(UnsplashService::class.java)
    }

    private val githubRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/repos/JoshiMinh/WallBase/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val updateService: UpdateService by lazy {
        githubRetrofit.create()
    }

    private val scraper: WebScraper by lazy { JsoupWebScraper() }

    private val database: WallBaseDatabase by lazy {
        WallBaseDatabase.Companion.getInstance(context)
    }

    val wallpaperRepository: WallpaperRepository by lazy {
        WallpaperRepository(
            redditService = redditService,
            webScraper = scraper,
            wallhavenService = wallhavenService,
            danbooruService = danbooruService,
            unsplashService = unsplashService
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

    val rotationRepository: WallpaperRotationRepository by lazy {
        WallpaperRotationRepository(
            database = database,
            workManager = WorkManager.getInstance(context)
        )
    }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(updateService)
    }
}
