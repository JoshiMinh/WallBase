package com.joshiminh.wallbase

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class WallBaseApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val client = if (::okHttpClient.isInitialized) {
            okHttpClient
        } else {
            OkHttpClient.Builder()
                .dns(com.joshiminh.wallbase.util.network.ResilientDns.create(context.cacheDir))
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host.lowercase(java.util.Locale.ROOT)
                    val requestBuilder = request.newBuilder()
                    if (host.contains("pximg.net") || host.contains("pixiv.net")) {
                        requestBuilder.header("Referer", "https://www.pixiv.net/")
                    } else if (host.contains("alphacoders.com")) {
                        requestBuilder.header("Referer", "https://wall.alphacoders.com/")
                    }
                    chain.proceed(requestBuilder.build())
                }
                .build()
        }

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(client))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
