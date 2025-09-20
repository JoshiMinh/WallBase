package com.joshiminh.wallbase.sources.google_photos

import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.source.SourceSeed

val GooglePhotosSource = SourceSeed(
    key = SourceKeys.GOOGLE_PHOTOS,
    iconRes = R.drawable.google_photos,
    title = "Google Photos",
    description = "Login, pick albums",
    showInExplore = true,
    enabledByDefault = true
)