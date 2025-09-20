package com.joshiminh.wallbase.sources.local

import android.R
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.source.SourceSeed

val LocalSource = SourceSeed(
    key = SourceKeys.LOCAL,
    iconRes = R.drawable.ic_menu_gallery,
    title = "Local",
    description = "Device Photo Picker / SAF",
    showInExplore = false,
    enabledByDefault = false,
    isLocal = true
)
