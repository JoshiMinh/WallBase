package com.joshiminh.wallbase.data.source

import com.joshiminh.wallbase.R

val RedditSource = SourceSeed(
    key = "reddit:wallpapers",
    providerKey = SourceKeys.REDDIT,
    icon = R.drawable.reddit,
    title = "r/wallpapers",
    description = "Top posts from r/wallpapers",
    showInExplore = true,
    enabledByDefault = true,
    config = "wallpapers"
)
