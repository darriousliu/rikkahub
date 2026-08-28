package me.rerere.rikkahub.platform

import java.util.Locale
import java.util.TimeZone

internal actual object PlatformDeviceInfo {
    actual val deviceName: String
        get() = listOfNotNull(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
        ).joinToString(" ").ifBlank { "Desktop" }

    actual val systemVersion: String
        get() = listOfNotNull(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
        ).joinToString(" ").ifBlank { "Unknown" }

    actual val localeName: String
        get() = Locale.getDefault().displayName

    actual val timeZoneName: String
        get() = TimeZone.getDefault().displayName

    // The JDK exposes no battery API; desktop placeholders resolve to "unknown".
    actual fun batteryLevel(): Int? = null
}
