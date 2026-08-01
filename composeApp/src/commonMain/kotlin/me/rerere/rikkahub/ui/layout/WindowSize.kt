package me.rerere.rikkahub.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.toSize

@Composable
fun currentWindowDpSize(): DpSize {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) {
        val size = containerSize.toSize()
        DpSize(size.width.toDp(), size.height.toDp())
    }
}
