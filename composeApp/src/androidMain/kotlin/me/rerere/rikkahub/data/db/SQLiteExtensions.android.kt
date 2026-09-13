package me.rerere.rikkahub.data.db

import android.database.sqlite.SQLiteBlobTooBigException

internal actual fun Throwable.isSQLiteBlobTooBigException(): Boolean = this is SQLiteBlobTooBigException
