package me.rerere.rikkahub.shared

import me.rerere.rikkahub.BuildKonfig

public data class PlatformBuildInfo(
    public val versionName: String,
    public val versionCode: String,
    public val debug: Boolean,
    public val applicationId: String,
    public val systemDescription: String,
)

public fun createPlatformBuildInfo(
    debug: Boolean,
    applicationId: String,
    versionName: String = BuildKonfig.VERSION_NAME,
    versionCode: String = BuildKonfig.VERSION_CODE,
    systemDescription: String = currentPlatformKind.displayName,
): PlatformBuildInfo = PlatformBuildInfo(
    versionName = versionName,
    versionCode = versionCode,
    debug = debug,
    applicationId = applicationId,
    systemDescription = systemDescription,
)

public val PlatformBuildInfo.displayVersion: String
    get() = "$versionName / $versionCode"

public val PlatformBuildInfo.updateCheckUserAgent: String
    get() = "RikkaHub $versionName #$versionCode"

public fun PlatformBuildInfo.apiUserAgent(platform: String): String =
    "RikkaHub-$platform/$versionName"
