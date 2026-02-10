package me.rerere.common.utils

import android.os.Build

actual fun exitProcess(status: Int) {
    kotlin.system.exitProcess(status)
}

actual val DEVICE_MANUFACTURER: String = Build.MANUFACTURER

actual val DEVICE_MODEL: String = Build.MODEL

actual const val OS_NAME: String = "Android"

actual val OS_VERSION: String = "Android ${Build.VERSION.RELEASE}"

actual val SDK_VERSION: String = "SDK ${Build.VERSION.SDK_INT}"
