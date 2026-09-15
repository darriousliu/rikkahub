package me.rerere.rikkahub.shared

public actual val currentPlatformKind: PlatformKind = PlatformKind.ANDROID

actual val PlatformKind.isLinux: Boolean
    get() = false
