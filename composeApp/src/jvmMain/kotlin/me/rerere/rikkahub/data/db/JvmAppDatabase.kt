package me.rerere.rikkahub.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import java.io.File
import java.security.MessageDigest
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
        driver = createJvmSQLiteDriver(),
        ftsDialect = MessageFtsDialect.SIMPLE,
    )
}

fun defaultJvmDatabaseFile(): File =
    File(System.getProperty("user.home"), ".rikkahub/database/rikka_hub.db")

internal fun createJvmSQLiteDriver(): BundledSQLiteDriver = BundledSQLiteDriver().apply {
    addExtension(simpleExtensionFile.absolutePath, "sqlite3_simple_init")
}

private val simpleExtensionFile: File get() {
    val name = System.mapLibraryName("simple")
    val bytes = checkNotNull(AppDatabase::class.java.getResourceAsStream("/native/simple/$name")) {
        "Missing Simple SQLite extension: $name"
    }.use { it.readBytes() }
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    val directory = File(FileKit.cacheDir.file, "simple/$hash").apply { mkdirs() }
    return File(directory, name).also { if (!it.exists()) it.writeBytes(bytes) }
}
