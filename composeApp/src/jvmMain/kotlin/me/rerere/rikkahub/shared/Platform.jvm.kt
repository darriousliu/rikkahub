package me.rerere.rikkahub.shared

public actual val currentPlatformKind: PlatformKind = PlatformKind.DESKTOP

actual val PlatformKind.isLinux: Boolean
    get() = System.getProperty("os.name").lowercase().contains("linux")

actual val PlatformKind.isWindows: Boolean
    get() = this == PlatformKind.DESKTOP && System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

actual val PlatformKind.isMacOS: Boolean
    get() = this == PlatformKind.DESKTOP && System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
