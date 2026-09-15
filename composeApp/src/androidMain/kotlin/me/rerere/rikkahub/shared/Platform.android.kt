package me.rerere.rikkahub.shared

public actual val currentPlatformKind: PlatformKind = PlatformKind.ANDROID

actual val PlatformKind.isLinux: Boolean
    get() = false

actual val PlatformKind.isWindows: Boolean
    get() = false

actual val PlatformKind.isMacOS: Boolean
    get() = false
