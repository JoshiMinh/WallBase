package com.joshiminh.wallbase.data

import androidx.annotation.DrawableRes

data class Source(
    @DrawableRes val icon: Int,
    val title: String,
    val description: String,
    val showInExplore: Boolean,
    val enabled: Boolean
)
