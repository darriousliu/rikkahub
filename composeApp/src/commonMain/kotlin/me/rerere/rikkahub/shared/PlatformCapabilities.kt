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
    CAMERA_QR("camera_qr", "Camera QR scanning"),
    WEB_SERVER("web_server", "Embedded web server"),
    FLOATING_WINDOW("floating_window", "Floating window"),
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
        PlatformCapability.SHARED_ENTRY -> CapabilityState.READY
        PlatformCapability.FLOATING_WINDOW,
        PlatformCapability.WEB_SERVER,
        -> CapabilityState.UNAVAILABLE

        PlatformCapability.PRODUCT_UI,
        PlatformCapability.CAMERA_QR,
        -> CapabilityState.PENDING
    }

    PlatformKind.DESKTOP -> when (capability) {
        PlatformCapability.SHARED_ENTRY -> CapabilityState.READY
        PlatformCapability.PRODUCT_UI,
        PlatformCapability.WEB_SERVER,
        -> CapabilityState.PENDING

        PlatformCapability.CAMERA_QR,
        PlatformCapability.FLOATING_WINDOW,
        -> CapabilityState.UNAVAILABLE
    }
}

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
