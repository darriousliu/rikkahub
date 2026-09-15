package me.rerere.rikkahub.shared

public actual val currentPlatformKind: PlatformKind = PlatformKind.IOS

actual val PlatformKind.isLinux: Boolean
    get() = false
