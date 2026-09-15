package com.joshiminh.wallbase.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Shared layout rhythm for the editorial gallery interface. */
@Immutable
object WallBaseSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** Shapes are deliberately restrained: controls, cards, and image-led content each have a role. */
@Immutable
object WallBaseShapes {
    val control = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(16.dp)
    val featured = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

object WallBaseMotion {
    const val microMillis = 120
    const val shortMillis = 180
    const val reducedMillis = 0
}
