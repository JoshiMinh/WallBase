package com.joshiminh.wallbase.di

import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.sources.RedditAuthService
import com.joshiminh.wallbase.sources.RedditService
import com.joshiminh.wallbase.sources.UnsplashService
import com.joshiminh.wallbase.sources.WallhavenService
import com.joshiminh.wallbase.util.network.JsoupWebScraper
import com.joshiminh.wallbase.util.network.RedditTokenManager
import com.joshiminh.wallbase.util.network.UpdateService
import com.joshiminh.wallbase.util.network.WebScraper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT = "android:com.joshiminh.wallbase:v1.1.0 (by /u/JoshiMinh)"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
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

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("Reddit")
    fun provideRedditOkHttpClient(
        okHttpClient: OkHttpClient,
        tokenManager: RedditTokenManager
    ): OkHttpClient {
        return okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath.contains("api/v1/access_token")) {
                    return@addInterceptor chain.proceed(request)
                }

                val token = tokenManager.getRedditAccessToken()
                if (token.isNotEmpty()) {
                    val updatedRequest = request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(updatedRequest)
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRedditAuthService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): RedditAuthService {
        return Retrofit.Builder()
            .baseUrl("https://www.reddit.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RedditAuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideRedditTokenManager(
        redditAuthService: RedditAuthService
    ): RedditTokenManager {
        return RedditTokenManager(redditAuthService, BuildConfig.REDDIT_CLIENT_ID)
    }

    @Provides
    @Singleton
    fun provideRedditService(
        @Named("Reddit") redditOkHttpClient: OkHttpClient,
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): RedditService {
        val hasRedditAuth = BuildConfig.REDDIT_CLIENT_ID != "YOUR_CLIENT_ID" && 
                BuildConfig.REDDIT_CLIENT_ID.isNotBlank()
        
        val client = if (hasRedditAuth) redditOkHttpClient else okHttpClient
        val baseUrl = if (hasRedditAuth) "https://oauth.reddit.com/" else "https://www.reddit.com/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RedditService::class.java)
    }

    @Provides
    @Singleton
    fun provideWallhavenService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): WallhavenService {
        return Retrofit.Builder()
            .baseUrl("https://wallhaven.cc/api/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WallhavenService::class.java)
    }

    @Provides
    @Singleton
    fun provideUnsplashService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): UnsplashService {
        return Retrofit.Builder()
            .baseUrl("https://unsplash.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UnsplashService::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdateService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): UpdateService {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/repos/JoshiMinh/WallBase/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create()
    }

    @Provides
    @Singleton
    fun provideWebScraper(): WebScraper {
        return JsoupWebScraper()
    }
}
