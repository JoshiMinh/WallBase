package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

fun Modifier.longPressReorderHandle(
    key: Any,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume() // The card's long-click action must not run on its drag handle.
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress != null) {
            onDragStart()
            val completed = drag(longPress.id) { change ->
                onDrag(change.positionChange())
                change.consume()
            }
            if (completed) onDragEnd() else onDragCancel()
        }
    }
}
