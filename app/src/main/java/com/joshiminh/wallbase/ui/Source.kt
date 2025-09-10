package com.joshiminh.wallbase.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Source(
    val id: String,
    @DrawableRes val icon: Int,
    val title: String,
    val description: String,
    enabled: Boolean = false
) {
    var enabled by mutableStateOf(enabled)
}

