package com.joshiminh.wallbase.data.source

val AlphaCodersSource = SourceSeed(
    key = "${SourceKeys.WEBSITES}:alphacoders_mobile",
    providerKey = SourceKeys.WEBSITES,
    iconRes = android.R.drawable.ic_menu_gallery,
    iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=mobile.alphacoders.com",
    title = "Alpha Coders Mobile",
    description = "Mobile wallpapers from Alpha Coders",
    showInExplore = true,
    enabledByDefault = true,
    config = "https://mobile.alphacoders.com"
)
