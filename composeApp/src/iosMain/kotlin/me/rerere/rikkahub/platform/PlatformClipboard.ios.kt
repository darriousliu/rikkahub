package me.rerere.rikkahub.platform

import platform.UIKit.UIPasteboard

internal actual object PlatformClipboard {
    actual fun readText(): String = UIPasteboard.generalPasteboard.string.orEmpty()

    actual fun writeText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
