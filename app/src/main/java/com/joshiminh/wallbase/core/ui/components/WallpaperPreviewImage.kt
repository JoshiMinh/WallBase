package com.joshiminh.wallbase.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun WallpaperPreviewImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    clipShape: RoundedCornerShape = RoundedCornerShape(0.dp)
) {
    val context = LocalContext.current
    val imageRequest = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val gradientBrush = remember(surfaceVariant) {
        Brush.verticalGradient(
            colors = listOf(
                surfaceVariant.copy(alpha = 0.25f),
                Color.Transparent,
                surfaceVariant.copy(alpha = 0.2f)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(clipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(gradientBrush)
        )
    }
}