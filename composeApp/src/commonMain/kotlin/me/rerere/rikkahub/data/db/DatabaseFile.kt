package me.rerere.rikkahub.data.db

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.div

val FileKit.databaseFile: PlatformFile
    get() = databasesDir / "rikka_hub"
