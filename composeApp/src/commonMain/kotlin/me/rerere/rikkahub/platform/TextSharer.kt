package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable

public fun interface TextSharer {
    public fun share(text: String): Result<Unit>
}

@Composable
public expect fun rememberPlatformTextSharer(): TextSharer
