package com.joshiminh.wallbase.data.source

import com.joshiminh.wallbase.R

val PinterestSource = SourceSeed(
    key = "${SourceKeys.PINTEREST}:wallpaper_board",
    providerKey = SourceKeys.PINTEREST,
    iconRes = R.drawable.pinterest,
    iconUrl = null,
    title = "Pinterest Wallpapers",
    description = "Latest pins from our Pinterest board",
    showInExplore = true,
    enabledByDefault = true,
    config = "https://www.pinterest.com/wallpapercollec/wallpapers"
)
