package com.joshiminh.wallbase.data.entity.source

import com.joshiminh.wallbase.sources.google_drive.GoogleDriveSource
import com.joshiminh.wallbase.sources.google_photos.GooglePhotosSource
import com.joshiminh.wallbase.sources.local.LocalSource
import com.joshiminh.wallbase.sources.pinterest.PinterestSource
import com.joshiminh.wallbase.sources.reddit.RedditSource

/**
 * Describes a built-in source that should be preloaded into the local database on first launch.
 */
data class SourceSeed(
    val key: String,
    val providerKey: String = key,
    val iconRes: Int? = null,
    val iconUrl: String? = null,
    val title: String,
    val description: String,
    val showInExplore: Boolean,
    val enabledByDefault: Boolean,
    val isLocal: Boolean = false,
    val config: String? = null
)

/** List of default sources bundled with the app. */
val DefaultSources: List<SourceSeed> = listOf(
    GooglePhotosSource,
    GoogleDriveSource,
    RedditSource,
    PinterestSource,
    LocalSource
)