package com.joshiminh.wallbase.data.source

import com.joshiminh.wallbase.R

val GoogleDriveSource = SourceSeed(
    key = SourceKeys.GOOGLE_DRIVE,
    iconRes = R.drawable.google_drive,
    title = "Google Drive",
    description = "Login, pick folder(s)",
    showInExplore = true,
    enabledByDefault = true
)
