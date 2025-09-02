package com.joshiminh.wallbase

import androidx.compose.runtime.MutableState

/**
 * Represents a wallpaper source that can be toggled on or off.
 */
data class SourceOption(
    val name: String,
    val enabled: MutableState<Boolean>
)
