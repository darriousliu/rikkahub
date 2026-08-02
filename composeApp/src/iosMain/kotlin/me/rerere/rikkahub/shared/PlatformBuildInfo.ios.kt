package me.rerere.rikkahub.shared

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

public const val IOS_APPLICATION_ID_FALLBACK: String = "me.rerere.rikkahub.ios"

@OptIn(ExperimentalNativeApi::class)
public fun currentIosPlatformBuildInfo(
    applicationId: String? = NSBundle.mainBundle.bundleIdentifier,
    debug: Boolean = Platform.isDebugBinary,
): PlatformBuildInfo = createPlatformBuildInfo(
    debug = debug,
    applicationId = applicationId ?: IOS_APPLICATION_ID_FALLBACK,
    systemDescription = NSProcessInfo.processInfo.operatingSystemVersionString,
)
