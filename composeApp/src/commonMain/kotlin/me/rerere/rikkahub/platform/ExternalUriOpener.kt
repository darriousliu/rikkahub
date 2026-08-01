package me.rerere.rikkahub.platform

/** Opens an external URI using the current platform's user-facing browser. */
public fun interface ExternalUriOpener {
    public fun open(uri: String): Result<Unit>
}
