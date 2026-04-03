package com.joshiminh.wallbase.sources

import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.SourceSeed

val PixivSource = SourceSeed(
	key = "${SourceKeys.PIXIV}:wallpaper",
	providerKey = SourceKeys.PIXIV,
	iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=pixiv.net",
	title = "Pixiv Wallpapers",
	description = "Curated Pixiv wallpaper searches and artwork pages",
	showInExplore = true,
	enabledByDefault = true,
	config = "https://www.pixiv.net/en/tags/wallpaper/artworks"
)



