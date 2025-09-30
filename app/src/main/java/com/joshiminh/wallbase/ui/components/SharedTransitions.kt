package com.joshiminh.wallbase.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSharedContentState
import androidx.compose.animation.sharedBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem

private const val SHARED_TRANSITION_DURATION_MILLIS = 350

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun sharedWallpaperTransitionModifier(
    wallpaper: WallpaperItem,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) {
        return Modifier
    }

    return with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = wallpaper.transitionKey()),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
            boundsTransform = BoundsTransform { _, _ ->
                tween(durationMillis = SHARED_TRANSITION_DURATION_MILLIS)
            }
        )
    }
}
