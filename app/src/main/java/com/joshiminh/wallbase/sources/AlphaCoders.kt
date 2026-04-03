package com.joshiminh.wallbase.sources

import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.SourceSeed

val AlphaCodersSource = SourceSeed(
	key = "${SourceKeys.ALPHA_CODERS}:wallpapers",
	providerKey = SourceKeys.ALPHA_CODERS,
	iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=wall.alphacoders.com",
	title = "AlphaCoders (Wallpaper Abyss)",
	description = "Browse wallpapers from AlphaCoders' extensive collection",
	showInExplore = true,
	enabledByDefault = true,
	config = "https://wall.alphacoders.com/by_category.php?id=3"
)

