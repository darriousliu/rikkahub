package me.rerere.rikkahub.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect

fun createJvmAppDatabase(
    file: File = defaultJvmDatabaseFile(),
): AppDatabase {
    file.parentFile?.mkdirs()
    return buildAppDatabase(
        builder = Room.databaseBuilder<AppDatabase>(
            name = file.absolutePath,
            factory = AppDatabaseConstructor::initialize,
        ),
        driver = BundledSQLiteDriver(),
        ftsDialect = MessageFtsDialect.UNICODE61,
    )
}

fun defaultJvmDatabaseFile(): File =
    File(System.getProperty("user.home"), ".rikkahub/database/rikka_hub.db")
