package me.rerere.rikkahub.shared

public const val DESKTOP_APPLICATION_ID: String = "me.rerere.rikkahub.desktop"
public const val DESKTOP_DEBUG_PROPERTY: String = "rikkahub.debug"

public fun currentDesktopPlatformBuildInfo(
    debugProperty: String? = System.getProperty(DESKTOP_DEBUG_PROPERTY),
): PlatformBuildInfo = createPlatformBuildInfo(
    debug = debugProperty?.toBooleanStrictOrNull() ?: false,
    applicationId = DESKTOP_APPLICATION_ID,
    systemDescription = listOfNotNull(
        System.getProperty("os.name"),
        System.getProperty("os.version"),
        System.getProperty("os.arch")?.let { "($it)" },
    ).joinToString(" "),
)
