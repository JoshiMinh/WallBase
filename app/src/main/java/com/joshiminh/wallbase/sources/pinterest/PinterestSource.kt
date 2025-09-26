package com.joshiminh.wallbase.sources.pinterest

import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.source.SourceSeed

val PinterestSource = SourceSeed(
    key = "${SourceKeys.PINTEREST}:wallpaper_board",
    providerKey = SourceKeys.PINTEREST,
    iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=pinterest.com",
    title = "Pinterest Wallpapers",
    description = "Latest pins from our Pinterest board (limited support)",
    showInExplore = true,
    enabledByDefault = true,
    config = "https://www.pinterest.com/wallpapercollec/wallpapers"
)