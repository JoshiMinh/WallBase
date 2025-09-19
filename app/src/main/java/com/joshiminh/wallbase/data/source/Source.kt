package com.joshiminh.wallbase.data.source

import androidx.annotation.DrawableRes

data class Source(
    val id: Long,
    @DrawableRes val icon: Int,
    val title: String,
    val description: String,
    val showInExplore: Boolean,
    val enabled: Boolean,
    val key: String,
    val providerKey: String,
    val isLocal: Boolean,
    val config: String? = null
)
