package me.rerere.rikkahub.platform

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.Locale
import java.util.TimeZone

internal actual object PlatformDeviceInfo : KoinComponent {
    actual val deviceName: String
        get() = "${Build.BRAND} ${Build.MODEL}"

    actual val systemVersion: String
        get() = "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"

    actual val localeName: String
        get() = Locale.getDefault().displayName

    actual val timeZoneName: String
        get() = TimeZone.getDefault().displayName

    actual fun batteryLevel(): Int? = runCatching {
        val manager = get<Context>().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrNull()
}
