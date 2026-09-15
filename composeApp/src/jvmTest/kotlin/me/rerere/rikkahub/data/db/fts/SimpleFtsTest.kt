package me.rerere.rikkahub.data.db.fts

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.db.createJvmSQLiteDriver
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SimpleFtsTest {
    private val directory = Files.createTempDirectory("simple-fts-").toFile()

    @BeforeTest
    fun prepareFileKit() {
        FileKit.init(filesDir = directory.resolve("files"), cacheDir = directory.resolve("cache"))
    }

    @AfterTest
    fun cleanup() {
        FileKit.init("RikkaHub")
        directory.deleteRecursively()
    }

    @Test
    fun chinesePinyinAndSnippets() = runTest { verifySimpleQueries(createJvmSQLiteDriver()) }

    @Test
    fun oldUnicodeIndexIsConvertedWithoutLosingRows() = runTest { verifyUnicodeMigration(createJvmSQLiteDriver()) }

    @Test
    fun failedConversionPreservesOldIndex() = runTest { verifyMigrationRollback(BundledSQLiteDriver()) }
}
