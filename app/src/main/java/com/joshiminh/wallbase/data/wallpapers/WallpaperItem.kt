package com.joshiminh.wallbase.data.wallpapers

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Simple representation of a wallpaper entry shown in the Explore screen.
 */
@Parcelize
data class WallpaperItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val sourceUrl: String,
    val sourceName: String? = null
) : Parcelable
