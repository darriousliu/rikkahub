package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
actual fun FloatingWindow(
    tag: String,
    visibility: Boolean,
    content: @Composable () -> Unit,
) {
    var position by remember(tag) { mutableStateOf<Offset?>(null) }
    var contentSize by remember(tag) { mutableStateOf(IntSize.Zero) }
    val alpha by animateFloatAsState(if (visibility) 1f else 0f, tween(200))

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().zIndex(1f)) {
        val maxX = (constraints.maxWidth - contentSize.width).coerceAtLeast(0).toFloat()
        val maxY = (constraints.maxHeight - contentSize.height).coerceAtLeast(0).toFloat()
        fun currentPosition(): Offset {
            val point = position ?: Offset(20f, maxY - 20f)
            return Offset(point.x.coerceIn(0f, maxX), point.y.coerceIn(0f, maxY))
        }

        Box(
            Modifier
                .absoluteOffset {
                    val point = currentPosition()
                    IntOffset(point.x.roundToInt(), point.y.roundToInt())
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        // 隐藏时保留内容状态；不放置内容，避免拦截页面点击。
                        if (alpha > 0f) placeable.place(0, 0)
                    }
                }
                .graphicsLayer { this.alpha = alpha }
                .onSizeChanged { contentSize = it }
                .pointerInput(tag, maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            val point = currentPosition()
                            position = point.copy(x = if (point.x < maxX / 2f) 0f else maxX)
                        },
                    ) { change, amount ->
                        change.consume()
                        val point = currentPosition() + amount
                        position = Offset(point.x.coerceIn(0f, maxX), point.y.coerceIn(0f, maxY))
                    }
                },
        ) {
            content()
        }
    }
}
