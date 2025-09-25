package com.joshiminh.wallbase

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.joshiminh.wallbase.util.network.ServiceLocator
import java.io.File
import okio.Path.Companion.toPath

class WallBaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
        SingletonImageLoader.set { context ->
            ImageLoader.Builder(context)
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.cacheDir, "coil_previews").absolutePath.toPath())
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .build()
        }
    }
}
