package me.rerere.rikkahub.platform

/** Device facts that prompt placeholders expose to the model. */
internal expect object PlatformDeviceInfo {
    /** Human readable device name, for example `Google Pixel 8`. */
    val deviceName: String

    /** Human readable OS version, for example `Android SDK v35 (15)`. */
    val systemVersion: String

    /** Name of the current locale, localized where the platform supports it. */
    val localeName: String

    /** Name of the current time zone, localized where the platform supports it. */
    val timeZoneName: String

    /** Battery percentage, or null when the platform does not report one. */
    fun batteryLevel(): Int?
}
