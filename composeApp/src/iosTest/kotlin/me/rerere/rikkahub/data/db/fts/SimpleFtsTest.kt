package me.rerere.rikkahub.data.db.fts

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.db.createIosSQLiteDriver
import kotlin.test.Test

class SimpleFtsTest {
    @Test
    fun chinesePinyinAndSnippets() = runTest { verifySimpleQueries(createIosSQLiteDriver()) }

    @Test
    fun oldUnicodeIndexIsConvertedWithoutLosingRows() = runTest { verifyUnicodeMigration(createIosSQLiteDriver()) }

    @Test
    fun failedConversionPreservesOldIndex() = runTest { verifyMigrationRollback(BundledSQLiteDriver()) }
}
