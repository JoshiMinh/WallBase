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

enum class SortField { Alphabet, DateAdded }

enum class SortDirection { Ascending, Descending }

data class SortSelection(val field: SortField, val direction: SortDirection)

enum class WallpaperSortOption(
    override val label: String,
    override val comparator: Comparator<WallpaperItem>
) : SortDescriptor<WallpaperItem> {
    RECENTLY_ADDED(
        label = "Recently added",
        comparator = compareByDescending { it.addedAt ?: Long.MIN_VALUE }
    ),
    OLDEST_ADDED(
        label = "Oldest added",
        comparator = compareBy { it.addedAt ?: Long.MAX_VALUE }
    ),
    TITLE_ASCENDING(
        label = "Title (A-Z)",
        comparator = compareBy { it.title.lowercase(Locale.ROOT) }
    ),
    TITLE_DESCENDING(
        label = "Title (Z-A)",
        comparator = compareByDescending { it.title.lowercase(Locale.ROOT) }
    )
}

enum class AlbumSortOption(
    override val label: String,
    override val comparator: Comparator<AlbumItem>
) : SortDescriptor<AlbumItem> {
    RECENTLY_CREATED(
        label = "Recently added",
        comparator = compareByDescending<AlbumItem> { it.createdAt }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    ),
    EARLIEST_CREATED(
        label = "Oldest added",
        comparator = compareBy<AlbumItem> { it.createdAt }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    ),
    TITLE_ASCENDING(
        label = "Title (A-Z)",
        comparator = compareBy { it.title.lowercase(Locale.ROOT) }
    ),
    TITLE_DESCENDING(
        label = "Title (Z-A)",
        comparator = compareByDescending { it.title.lowercase(Locale.ROOT) }
    )
}

fun <T> List<T>.sortedWith(option: SortDescriptor<T>): List<T> = option.sort(this)

fun WallpaperSortOption.toSelection(): SortSelection = when (this) {
    WallpaperSortOption.RECENTLY_ADDED -> SortSelection(SortField.DateAdded, SortDirection.Descending)
    WallpaperSortOption.OLDEST_ADDED -> SortSelection(SortField.DateAdded, SortDirection.Ascending)
    WallpaperSortOption.TITLE_ASCENDING -> SortSelection(SortField.Alphabet, SortDirection.Ascending)
    WallpaperSortOption.TITLE_DESCENDING -> SortSelection(SortField.Alphabet, SortDirection.Descending)
}

fun SortSelection.toWallpaperSortOption(): WallpaperSortOption = when (field) {
    SortField.Alphabet -> if (direction == SortDirection.Ascending) {
        WallpaperSortOption.TITLE_ASCENDING
    } else {
        WallpaperSortOption.TITLE_DESCENDING
    }

    SortField.DateAdded -> if (direction == SortDirection.Ascending) {
        WallpaperSortOption.OLDEST_ADDED
    } else {
        WallpaperSortOption.RECENTLY_ADDED
    }
}

fun AlbumSortOption.toSelection(): SortSelection = when (this) {
    AlbumSortOption.RECENTLY_CREATED -> SortSelection(SortField.DateAdded, SortDirection.Descending)
    AlbumSortOption.EARLIEST_CREATED -> SortSelection(SortField.DateAdded, SortDirection.Ascending)
    AlbumSortOption.TITLE_ASCENDING -> SortSelection(SortField.Alphabet, SortDirection.Ascending)
    AlbumSortOption.TITLE_DESCENDING -> SortSelection(SortField.Alphabet, SortDirection.Descending)
}

fun SortSelection.toAlbumSortOption(): AlbumSortOption = when (field) {
    SortField.Alphabet -> if (direction == SortDirection.Ascending) {
        AlbumSortOption.TITLE_ASCENDING
    } else {
        AlbumSortOption.TITLE_DESCENDING
    }

    SortField.DateAdded -> if (direction == SortDirection.Ascending) {
        AlbumSortOption.EARLIEST_CREATED
    } else {
        AlbumSortOption.RECENTLY_CREATED
    }
}

val SortField.displayName: String
    get() = when (this) {
        SortField.Alphabet -> "Alphabet"
        SortField.DateAdded -> "Date added"
    }

fun SortField.defaultDirection(): SortDirection = when (this) {
    SortField.Alphabet -> SortDirection.Ascending
    SortField.DateAdded -> SortDirection.Descending
}

fun SortDirection.toggle(): SortDirection = when (this) {
    SortDirection.Ascending -> SortDirection.Descending
    SortDirection.Descending -> SortDirection.Ascending
}