package com.joshiminh.wallbase

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import com.joshiminh.wallbase.util.network.ServiceLocator

class WallBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.ensureInitialized(this)
        SingletonImageLoader.setSafe { context ->
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
