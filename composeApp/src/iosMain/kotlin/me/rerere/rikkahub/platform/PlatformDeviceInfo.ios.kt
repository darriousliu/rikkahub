package me.rerere.rikkahub.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.localTimeZone
import platform.UIKit.UIDevice
import kotlin.math.roundToInt

internal actual object PlatformDeviceInfo {
    actual val deviceName: String
        get() = UIDevice.currentDevice.model

    actual val systemVersion: String
        get() = with(UIDevice.currentDevice) { "$systemName $systemVersion" }

    actual val localeName: String
        get() = NSLocale.currentLocale.localeIdentifier

    actual val timeZoneName: String
        get() = NSTimeZone.localTimeZone.name

    actual fun batteryLevel(): Int? {
        val device = UIDevice.currentDevice
        val wasEnabled = device.batteryMonitoringEnabled
        if (!wasEnabled) device.batteryMonitoringEnabled = true
        // UIKit reports -1 while the level is unknown.
        val level = device.batteryLevel
        if (!wasEnabled) device.batteryMonitoringEnabled = false
        return if (level < 0f) null else (level * 100f).roundToInt()
    }
}
