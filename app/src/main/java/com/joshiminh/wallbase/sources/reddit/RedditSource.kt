package com.joshiminh.wallbase.sources.reddit

import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.source.SourceSeed

val RedditSource = SourceSeed(
    key = "reddit:wallpapers",
    providerKey = SourceKeys.REDDIT,
    iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=reddit.com",
    title = "r/wallpapers",
    description = "Top posts from r/wallpapers",
    showInExplore = true,
    enabledByDefault = true,
    config = "wallpapers"
)