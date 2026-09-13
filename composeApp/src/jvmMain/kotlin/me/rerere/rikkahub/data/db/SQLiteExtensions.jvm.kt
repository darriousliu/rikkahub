package me.rerere.rikkahub.data.db

internal actual fun Throwable.isSQLiteBlobTooBigException(): Boolean = false
