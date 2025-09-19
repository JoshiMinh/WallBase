package com.joshiminh.wallbase.data.source

val LocalSource = SourceSeed(
    key = SourceKeys.LOCAL,
    icon = android.R.drawable.ic_menu_gallery,
    title = "Local",
    description = "Device Photo Picker / SAF",
    showInExplore = false,
    enabledByDefault = false,
    isLocal = true
)
