package com.joshiminh.wallbase.data.wallpapers

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Simple representation of a wallpaper entry shown in browsing screens.
 */
@Parcelize
data class WallpaperItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val sourceUrl: String,
    val sourceName: String? = null,
    val sourceKey: String? = null,
    val width: Int? = null,
    val height: Int? = null
) : Parcelable {

    val aspectRatio: Float?
        get() {
            val w = width?.takeIf { it > 0 }?.toFloat() ?: return null
            val h = height?.takeIf { it > 0 }?.toFloat() ?: return null
            if (h == 0f) return null
            return w / h
        }

    fun libraryKey(): String? {
        val key = sourceKey ?: return null
        val normalizedId = if (id.startsWith("$key:")) {
            id.removePrefix("$key:")
        } else {
            id
        }
        return "$key:$normalizedId"
    }
}
