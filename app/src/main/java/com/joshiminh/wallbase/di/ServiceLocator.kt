package com.joshiminh.wallbase.di

import com.joshiminh.wallbase.data.wallpapers.WallpaperRepository
import com.joshiminh.wallbase.network.JsoupWebScraper
import com.joshiminh.wallbase.network.RedditService
import com.joshiminh.wallbase.network.WebScraper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ServiceLocator {

    private const val USER_AGENT = "WallBase/1.0 (Android)"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val updatedRequest = request.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(updatedRequest)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
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

    val wallpaperRepository: WallpaperRepository by lazy {
        WallpaperRepository(
            redditService = redditService,
            webScraper = scraper
        )
    }
}
