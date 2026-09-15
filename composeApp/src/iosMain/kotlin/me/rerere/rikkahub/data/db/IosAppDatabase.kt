package me.rerere.rikkahub.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.files.LegacyIosFileMigration
import platform.Foundation.NSBundle

@OptIn(ExperimentalForeignApi::class)
fun createIosAppDatabase(
    file: PlatformFile = FileKit.databaseFile,
): AppDatabase {
    file.parent()?.createDirectories()
    return buildAppDatabase(
        builder = Room.databaseBuilder<AppDatabase>(
            name = file.toKotlinxIoPath().toString(),
            factory = AppDatabaseConstructor::initialize,
        ),
        driver = createIosSQLiteDriver(),
        ftsDialect = MessageFtsDialect.SIMPLE,
        platformOnOpen = LegacyIosFileMigration(FileKit.filesDir.toKotlinxIoPath())::migrateDatabase,
    )
}

@OptIn(ExperimentalForeignApi::class)
internal fun createIosSQLiteDriver(): BundledSQLiteDriver = BundledSQLiteDriver().apply {
    addExtension("${NSBundle.mainBundle.privateFrameworksPath}/simple.framework/simple", "sqlite3_simple_init")
}
