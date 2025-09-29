package com.joshiminh.wallbase.data.entity.wallpaper

import android.os.Parcelable
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
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
    val height: Int? = null,
    val addedAt: Long? = null,
    val localUri: String? = null,
    val isDownloaded: Boolean = false,
    val cropSettings: WallpaperCropSettings? = null,
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

    fun providerKey(): String? = sourceKey?.substringBefore(':', missingDelimiterValue = sourceKey)

    fun remoteIdentifierWithinSource(): String? {
        val key = libraryKey() ?: return null
        val prefix = sourceKey?.let { "$it:" }
        return when {
            prefix != null && key.startsWith(prefix) -> key.removePrefix(prefix)
            else -> {
                val delimiterIndex = key.indexOf(':')
                if (delimiterIndex in 0 until key.lastIndex) {
                    key.substring(delimiterIndex + 1)
                } else {
                    null
                }
            }
        }
    }

    fun previewModel(): Any =
        localUri?.takeIf { isDownloaded && it.isNotBlank() } ?: imageUrl

    fun transitionKey(): String = "wallpaper-$id"
}
