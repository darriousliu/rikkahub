package me.rerere.rikkahub.ui.components.richtext

internal actual class MermaidInterface actual constructor(
    actual val onHeightChanged: (Int) -> Unit,
    actual val onExportImage: (String) -> Unit
)
