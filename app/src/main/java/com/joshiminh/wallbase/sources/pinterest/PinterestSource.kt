package com.joshiminh.wallbase.sources.pinterest

import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.source.SourceSeed

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