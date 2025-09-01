package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn

@Composable
fun KeepScreenOn() {
    Box(
        modifier = Modifier.keepScreenOn()
    )
}
