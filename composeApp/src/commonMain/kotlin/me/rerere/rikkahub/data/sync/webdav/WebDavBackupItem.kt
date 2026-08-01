package me.rerere.rikkahub.data.sync.webdav

import kotlin.time.Instant

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
