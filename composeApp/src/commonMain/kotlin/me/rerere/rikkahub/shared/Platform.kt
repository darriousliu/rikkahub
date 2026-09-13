package me.rerere.rikkahub.shared

/** Platforms that host the shared Compose application. */
public enum class PlatformKind(
    public val displayName: String,
) {
    ANDROID("Android"),
    IOS("iOS"),
    DESKTOP("Desktop"),
}

public expect val currentPlatformKind: PlatformKind
