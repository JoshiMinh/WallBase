package com.joshiminh.wallbase.sources.google_drive

import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.source.SourceSeed

val GoogleDriveSource = SourceSeed(
    key = SourceKeys.GOOGLE_DRIVE,
    iconRes = R.drawable.google_drive,
    title = "Google Drive",
    description = "Login, pick folder(s)",
    showInExplore = true,
    enabledByDefault = true
)