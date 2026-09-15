package me.rerere.rikkahub.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.files.LegacyIosFileMigration
import platform.Foundation.NSBundle
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
        driver = createIosSQLiteDriver(),
        ftsDialect = MessageFtsDialect.SIMPLE,
        platformOnOpen = LegacyIosFileMigration(FileKit.filesDir.toKotlinxIoPath())::migrateDatabase,
    )
}

fun defaultIosDatabaseDirectory(): String =
    "${NSHomeDirectory()}/Library/Application Support/RikkaHub/database"

fun defaultIosDatabaseFilePath(): String = "${defaultIosDatabaseDirectory()}/rikka_hub.db"

@OptIn(ExperimentalForeignApi::class)
internal fun createIosSQLiteDriver(): BundledSQLiteDriver = BundledSQLiteDriver().apply {
    addExtension("${NSBundle.mainBundle.privateFrameworksPath}/simple.framework/simple", "sqlite3_simple_init")
}
