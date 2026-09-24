package com.joshiminh.wallbase.util.network

import android.content.Context
import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.data.WallBaseDatabase
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.LocalStorageCoordinator
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SourceCredentialStore
import com.joshiminh.wallbase.data.repository.SourceRepository
import com.joshiminh.wallbase.data.repository.UpdateRepository
import com.joshiminh.wallbase.data.repository.WallpaperRepository
import com.joshiminh.wallbase.data.repository.settingsDataStore
import com.joshiminh.wallbase.sources.RedditService
import com.joshiminh.wallbase.sources.WallhavenService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.jvm.Volatile
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

object ServiceLocator {

    private const val USER_AGENT = "android:com.joshiminh.wallbase:v1.1.0 (by /u/JoshiMinh)"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) {
                    val applicationContext = context.applicationContext
                    appContext = applicationContext
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
            .client(okHttpClient.newBuilder().addInterceptor { chain ->
                val request = chain.request()
                val key = sourceCredentialStore.snapshot().wallhavenApiKey
                if (key.isBlank()) {
                    chain.proceed(request)
                } else {
                    val builder = request.newBuilder()
                    builder.header("X-API-Key", key)
                    val newUrl = if (request.url.queryParameter("apikey") == null) {
                        request.url.newBuilder().addQueryParameter("apikey", key).build()
                    } else {
                        request.url
                    }
                    builder.url(newUrl)
                    chain.proceed(builder.build())
                }
            }.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val wallhavenService: WallhavenService by lazy {
        wallhavenRetrofit.create(WallhavenService::class.java)
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
        WallBaseDatabase.getInstance(context)
    }

    val declarativeScraperEngine: com.joshiminh.wallbase.scraper.engine.DeclarativeScraperEngine by lazy {
        com.joshiminh.wallbase.scraper.engine.DeclarativeScraperEngine(
            okHttpClient = okHttpClient,
            moshi = moshi
        )
    }

    val extensionRepositoryManager: com.joshiminh.wallbase.scraper.repository.ExtensionRepositoryManager by lazy {
        com.joshiminh.wallbase.scraper.repository.ExtensionRepositoryManager(
            context = context,
            moshi = moshi,
            okHttpClient = okHttpClient,
            sourceDao = database.sourceDao(),
            dataStore = context.settingsDataStore
        )
    }

    val wallpaperRepository: WallpaperRepository by lazy {
        WallpaperRepository(
            redditService = redditService,
            webScraper = scraper,
            wallhavenService = wallhavenService,
            credentialStore = sourceCredentialStore,
            declarativeScraperEngine = declarativeScraperEngine,
            extensionRepositoryManager = extensionRepositoryManager
        )
    }

    val sourceRepository: SourceRepository by lazy {
        SourceRepository(
            sourceDao = database.sourceDao(),
            wallpaperDao = database.wallpaperDao(),
            localStorage = localStorageCoordinator,
            extensionRepositoryManager = extensionRepositoryManager
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context.settingsDataStore)
    }

    val sourceCredentialStore: SourceCredentialStore by lazy {
        SourceCredentialStore(context)
    }

    val localStorageCoordinator: LocalStorageCoordinator by lazy {
        LocalStorageCoordinator(context)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            wallpaperDao = database.wallpaperDao(),
            albumDao = database.albumDao(),
            categoryDao = database.categoryDao(),
            localStorage = localStorageCoordinator
        )
    }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(updateService)
    }
}
