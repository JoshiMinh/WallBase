package com.joshiminh.wallbase.ui.sort

import com.joshiminh.wallbase.data.entity.album.AlbumItem
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import java.util.Locale

interface SortDescriptor<T> {
    val label: String
    val comparator: Comparator<T>

    fun sort(items: List<T>): List<T> {
        if (items.size < 2) return items
        val sorted = items.sortedWith(comparator)
        return if (sorted == items) items else sorted
    }
}

enum class WallpaperSortOption(
    override val label: String,
    override val comparator: Comparator<WallpaperItem>
) : SortDescriptor<WallpaperItem> {
    RECENTLY_ADDED(
        label = "Recently added",
        comparator = compareByDescending<WallpaperItem> { it.addedAt ?: Long.MIN_VALUE }
    ),
    OLDEST_ADDED(
        label = "Oldest added",
        comparator = compareBy<WallpaperItem> { it.addedAt ?: Long.MAX_VALUE }
    ),
    TITLE_ASCENDING(
        label = "Title (A-Z)",
        comparator = compareBy { it.title.lowercase(Locale.ROOT) }
    ),
    TITLE_DESCENDING(
        label = "Title (Z-A)",
        comparator = compareByDescending<WallpaperItem> { it.title.lowercase(Locale.ROOT) }
    )
}

enum class AlbumSortOption(
    override val label: String,
    override val comparator: Comparator<AlbumItem>
) : SortDescriptor<AlbumItem> {
    TITLE_ASCENDING(
        label = "Title (A-Z)",
        comparator = compareBy { it.title.lowercase(Locale.ROOT) }
    ),
    TITLE_DESCENDING(
        label = "Title (Z-A)",
        comparator = compareByDescending<AlbumItem> { it.title.lowercase(Locale.ROOT) }
    ),
    MOST_WALLPAPERS(
        label = "Most wallpapers",
        comparator = compareByDescending<AlbumItem> { it.wallpaperCount }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    ),
    FEWEST_WALLPAPERS(
        label = "Fewest wallpapers",
        comparator = compareBy<AlbumItem> { it.wallpaperCount }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    )
}

fun <T> List<T>.sortedWith(option: SortDescriptor<T>): List<T> = option.sort(this)
