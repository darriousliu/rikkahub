package me.rerere.rikkahub.shared

public actual val currentPlatformKind: PlatformKind = PlatformKind.DESKTOP

actual val PlatformKind.isLinux: Boolean
    get() = System.getProperty("os.name").lowercase().contains("linux")
