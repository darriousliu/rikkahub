package me.rerere.ai.util

import me.rerere.ai.ui.UIMessagePart

expect fun UIMessagePart.Image.encodeBase64(withPrefix: Boolean = true): Result<String>
