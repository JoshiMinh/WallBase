package com.joshiminh.wallbase.data.source

import com.joshiminh.wallbase.R

val GooglePhotosSource = SourceSeed(
    key = SourceKeys.GOOGLE_PHOTOS,
    iconRes = R.drawable.google_photos,
    title = "Google Photos",
    description = "Login, pick albums",
    showInExplore = true,
    enabledByDefault = true
)