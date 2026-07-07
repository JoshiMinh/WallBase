package com.joshiminh.wallbase

import android.app.Application
import com.joshiminh.wallbase.util.network.ServiceLocator
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WallBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
