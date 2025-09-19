package com.joshiminh.wallbase.data.source

data class Source(
    val id: Long,
    val iconRes: Int?,
    val iconUrl: String?,
    val title: String,
    val description: String,
    val showInExplore: Boolean,
    val enabled: Boolean,
    val key: String,
    val providerKey: String,
    val isLocal: Boolean,
    val config: String? = null
)
