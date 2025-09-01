package me.rerere.common.utils

import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

actual fun exitProcess(status: Int) {
    kotlin.system.exitProcess(status)
}

actual const val DEVICE_MANUFACTURER: String = "Apple"

actual val DEVICE_MODEL: String = UIDevice.currentDevice.modelName

actual val OS_VERSION: String = "iOS ${UIDevice.currentDevice.systemVersion}"

actual val SDK_VERSION: String = ""

private val UIDevice.modelName: String
    get() {
        val identifier = memScoped {
            val systemInfo = alloc<utsname>()
            uname(systemInfo.ptr)

            // 将 machine 字段转换为字符串
            val machineBytes = systemInfo.machine
            buildString {
                for (i in 0 until 256) { // utsname.machine 最大长度通常是 256
                    val byte = machineBytes[i]
                    if (byte == 0.toByte()) break
                    append(byte.toInt().toChar())
                }
            }
        }
        return when (identifier) {
            "iPhone5,1", "iPhone5,2" -> "iPhone 5"
            "iPhone5,3", "iPhone5,4" -> "iPhone 5C"
            "iPhone6,1", "iPhone6,2" -> "iPhone 5S"
            "iPhone7.2" -> "iPhone 6"
            "iPhone7,1" -> "iPhone 6 Plus"
            "iPhone8,1" -> "iPhone 6s"
            "iPhone8,2" -> "iPhone 6 Plus"
            "iPhone8,4" -> "iPhone SE1"
            "iPhone9,1", "iPhone9,3" -> "iPhone 7"
            "iPhone9,2", "iPhone9,4" -> "iPhone 7 Plus"
            "iPhone10,1", "iPhone10,4" -> "iPhone 8"
            "iPhone10,2", "iPhone10,5" -> "iPhone 8 Plus"
            "iPhone10,3", "iPhone10,6" -> "iPhone X"
            "iPhone11,8" -> "iPhone XR"
            "iPhone11,2" -> "iPhone XS"
            "iPhone11,6", "iPhone11,4" -> "iPhone XS Max"
            "iPhone12,1" -> "iPhone 11"
            "iPhone12,3" -> "iPhone 11 Pro"
            "iPhone12,5" -> "iPhone 11 Pro Max"
            "iPhone12,8" -> "iPhone SE2"
            "iPhone13,1" -> "iPhone 12 Mini"
            "iPhone13,2" -> "iPhone 12"
            "iPhone13,3" -> "iPhone 12 Pro"
            "iPhone13,4" -> "iPhone 12 Pro Max"
            "iPhone14,4" -> "iPhone 13 Mini"
            "iPhone14,5" -> "iPhone 13"
            "iPhone14,2" -> "iPhone 13 Pro"
            "iPhone14,3" -> "iPhone 13 Pro Max"
            "iPhone14,6" -> "iPhone SE3"
            "iPhone14,7" -> "iPhone 14"
            "iPhone14,8" -> "iPhone 14 Plus"
            "iPhone15,2" -> "iPhone 14 Pro"
            "iPhone15,3" -> "iPhone 14 Pro Max"
            "iPhone15,4" -> "iPhone 15"
            "iPhone15,5" -> "iPhone 15 Plus"
            "iPhone16,1" -> "iPhone 15 Pro"
            "iPhone16,2" -> "iPhone 15 Pro Max"
            "iPhone17,3" -> "iPhone 16"
            "iPhone17,4" -> "iPhone 16 Plus"
            "iPhone17,1" -> "iPhone 16 Pro"
            "iPhone17,2" -> "iPhone 16 Pro Max"
            "iPhone17,5" -> "iPhone 16e"

            // Simulator
            "i386", "x86_64" -> "X86 simulator"
            "arm64" -> "Arm64 Simulator"
            else -> "unknown"
        }
    }
