package com.joshiminh.wallbase.util.wallpapers.rotation

import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget

object WallpaperRotationDefaults {
    const val MIN_INTERVAL_MINUTES: Long = 15
    const val DEFAULT_INTERVAL_MINUTES: Long = 60
    val AVAILABLE_INTERVALS: List<Long> = listOf(15, 30, 60, 240, 1440)
    val DEFAULT_TARGET: WallpaperTarget = WallpaperTarget.BOTH
}
