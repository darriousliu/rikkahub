package me.rerere.rikkahub.shared

/** Platforms that host the shared Compose application. */
public enum class PlatformKind(
    public val displayName: String,
) {
    ANDROID("Android"),
    IOS("iOS"),
    DESKTOP("Desktop"),
}

/** A platform-facing feature whose availability can affect shared UI. */
public enum class PlatformCapability(
    public val id: String,
    public val displayName: String,
) {
    SHARED_ENTRY("shared_entry", "Shared entry"),
    PRODUCT_UI("product_ui", "Product UI"),
    NOTIFICATIONS("notifications", "Notifications"),
    EXTERNAL_URI("external_uri", "External URI"),
    OAUTH("oauth", "OAuth callback"),
    MONITORING("monitoring", "Analytics and crash reporting"),
    CAMERA_QR("camera_qr", "Camera QR scanning"),
    QR_IMAGE("qr_image", "QR image decoding"),
    QR_RENDER("qr_render", "QR code rendering"),
    AUDIO_PLAYBACK("audio_playback", "Audio playback"),
    IMAGE_CROP("image_crop", "Image cropping"),
    WEB_SERVER("web_server", "Embedded web server"),
    FLOATING_WINDOW("floating_window", "Floating window"),
    GIF_ANIMATION("gif_animation", "Animated GIF"),
    CHARACTER_CARD_METADATA("character_card_metadata", "Character card metadata"),
    MDNS("mdns", "Local service discovery"),
    FILE_IMPORT("file_import", "Sandboxed file import"),
    TERMINAL("terminal", "Terminal"),
    WORKSPACE("workspace", "Workspace"),
    DOCUMENT("document", "Document processing"),
}

/** Bootstrap progress of a platform capability. */
public enum class CapabilityState(
    public val displayName: String,
) {
    READY("Ready"),
    PENDING("Pending"),
    UNAVAILABLE("Unavailable"),
}

/** Thin platform hook used by the shared entry point. */
public expect val currentPlatformKind: PlatformKind

/**
 * Returns the current migration state without touching a platform API.
 * PENDING means the platform is expected to support the capability, but its
 * shared implementation is not ready yet.
 */
public fun capabilityState(
    platform: PlatformKind,
    capability: PlatformCapability,
): CapabilityState = when (platform) {
    PlatformKind.ANDROID -> CapabilityState.READY
    PlatformKind.IOS -> when (capability) {
        PlatformCapability.SHARED_ENTRY,
        PlatformCapability.NOTIFICATIONS,
        PlatformCapability.EXTERNAL_URI,
        PlatformCapability.OAUTH,
        PlatformCapability.MONITORING,
        PlatformCapability.CAMERA_QR,
        PlatformCapability.QR_IMAGE,
        PlatformCapability.AUDIO_PLAYBACK,
        PlatformCapability.CHARACTER_CARD_METADATA,
        PlatformCapability.MDNS,
        PlatformCapability.FILE_IMPORT,
        -> CapabilityState.READY

        PlatformCapability.PRODUCT_UI -> CapabilityState.PENDING

        PlatformCapability.QR_RENDER,
        PlatformCapability.IMAGE_CROP,
        PlatformCapability.FLOATING_WINDOW,
        PlatformCapability.WEB_SERVER,
        PlatformCapability.GIF_ANIMATION,
        PlatformCapability.TERMINAL,
        PlatformCapability.WORKSPACE,
        PlatformCapability.DOCUMENT,
        -> CapabilityState.UNAVAILABLE
    }

    PlatformKind.DESKTOP -> when (capability) {
        PlatformCapability.SHARED_ENTRY,
        PlatformCapability.NOTIFICATIONS,
        PlatformCapability.EXTERNAL_URI,
        PlatformCapability.OAUTH,
        PlatformCapability.MONITORING,
        PlatformCapability.QR_IMAGE,
        PlatformCapability.QR_RENDER,
        PlatformCapability.AUDIO_PLAYBACK,
        PlatformCapability.WEB_SERVER,
        PlatformCapability.CHARACTER_CARD_METADATA,
        PlatformCapability.MDNS,
        PlatformCapability.FILE_IMPORT,
        -> CapabilityState.READY

        PlatformCapability.PRODUCT_UI -> CapabilityState.PENDING

        PlatformCapability.CAMERA_QR,
        PlatformCapability.IMAGE_CROP,
        PlatformCapability.FLOATING_WINDOW,
        PlatformCapability.GIF_ANIMATION,
        PlatformCapability.TERMINAL,
        PlatformCapability.WORKSPACE,
        PlatformCapability.DOCUMENT,
        -> CapabilityState.UNAVAILABLE
    }
}

/** Shared pages use this gate before registering or presenting a platform-only entry. */
public fun hasCapability(
    platform: PlatformKind,
    capability: PlatformCapability,
): Boolean = capabilityState(platform = platform, capability = capability) == CapabilityState.READY

/** Returns every capability in stable declaration order. */
public fun capabilityMatrix(platform: PlatformKind): Map<PlatformCapability, CapabilityState> =
    PlatformCapability.entries.associateWith { capability ->
        capabilityState(platform = platform, capability = capability)
    }

/** Stable semantics contract used by all three shell smoke tests. */
public object SharedEntryTestTags {
    public const val Root: String = "shared_entry_root"
    public const val Platform: String = "shared_entry_platform"

    public fun capability(id: PlatformCapability): String = "shared_entry_capability_${id.id}"
}
