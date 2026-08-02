package me.rerere.rikkahub.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

@OptIn(ExperimentalForeignApi::class)
fun createIosAppDatabase(
    directory: String = defaultIosDatabaseDirectory(),
): AppDatabase {
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return buildAppDatabase(
        builder = Room.databaseBuilder<AppDatabase>(
            name = "$directory/rikka_hub.db",
            factory = AppDatabaseConstructor::initialize,
        ),
        driver = BundledSQLiteDriver(),
        ftsDialect = MessageFtsDialect.UNICODE61,
    )
}

fun defaultIosDatabaseDirectory(): String =
    "${NSHomeDirectory()}/Library/Application Support/RikkaHub/database"

fun defaultIosDatabaseFilePath(): String = "${defaultIosDatabaseDirectory()}/rikka_hub.db"
