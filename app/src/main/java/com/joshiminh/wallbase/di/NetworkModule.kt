package com.joshiminh.wallbase.di

import android.content.Context
import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.data.repository.SourceCredentialStore
import com.joshiminh.wallbase.sources.RedditService
import com.joshiminh.wallbase.sources.WallhavenService
import com.joshiminh.wallbase.util.network.JsoupWebScraper
import com.joshiminh.wallbase.util.network.ResilientDns
import com.joshiminh.wallbase.util.network.UpdateService
import com.joshiminh.wallbase.util.network.WebScraper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 WallBase/6.5"
    private const val REDDIT_USER_AGENT = "android:com.joshiminh.wallbase:v6.5 (by /u/JoshiMinh)"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideDns(
        @ApplicationContext context: Context
    ): Dns {
        return ResilientDns.create(context.cacheDir)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dns: Dns
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host.lowercase(java.util.Locale.ROOT)
                val requestBuilder = request.newBuilder()

                if (host.contains("reddit.com") || host.contains("redd.it")) {
                    requestBuilder.header("User-Agent", REDDIT_USER_AGENT)
                } else if (request.header("User-Agent") == null) {
                    requestBuilder.header("User-Agent", USER_AGENT)
                }

                if (host.contains("pximg.net") || host.contains("pixiv.net")) {
                    requestBuilder.header("Referer", "https://www.pixiv.net/")
                } else if (host.contains("alphacoders.com")) {
                    requestBuilder.header("Referer", "https://wall.alphacoders.com/")
                }

                chain.proceed(requestBuilder.build())
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
    fun provideRedditService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): RedditService {
        return Retrofit.Builder()
            .baseUrl("https://www.reddit.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RedditService::class.java)
    }

    @Provides
    @Singleton
    @Named("Wallhaven")
    fun provideWallhavenOkHttpClient(
        okHttpClient: OkHttpClient,
        credentialStore: SourceCredentialStore,
    ): OkHttpClient {
        return okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val key = credentialStore.snapshot().wallhavenApiKey
                val builder = request.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 WallBase/1.1.0")
                if (key.isNotBlank()) {
                    builder.header("X-API-Key", key)
                    val newUrl = if (request.url.queryParameter("apikey") == null) {
                        request.url.newBuilder().addQueryParameter("apikey", key).build()
                    } else {
                        request.url
                    }
                    builder.url(newUrl)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideWallhavenService(
        @Named("Wallhaven") wallhavenOkHttpClient: OkHttpClient,
        moshi: Moshi
    ): WallhavenService {
        return Retrofit.Builder()
            .baseUrl("https://wallhaven.cc/api/v1/")
            .client(wallhavenOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WallhavenService::class.java)
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
            .create(UpdateService::class.java)
    }

    @Provides
    @Singleton
    fun provideWebScraper(
        okHttpClient: OkHttpClient
    ): WebScraper {
        return JsoupWebScraper(okHttpClient)
    }
}
