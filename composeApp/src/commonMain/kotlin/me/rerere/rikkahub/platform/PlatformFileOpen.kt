package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberFileOpener(): (String) -> Result<Unit>
