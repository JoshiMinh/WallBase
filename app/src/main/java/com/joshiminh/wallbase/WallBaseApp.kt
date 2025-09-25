package com.joshiminh.wallbase

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.joshiminh.wallbase.util.network.ServiceLocator
import okio.Path.Companion.toOkioPath

class WallBaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
        SingletonImageLoader.imageLoader = { context ->
            ImageLoader.Builder(context)
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("coil_previews").toOkioPath())
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .build()
        }
    }
}