package me.rerere.rikkahub.data.db.fts

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleFtsTest {
    @Test
    fun packagedExtensionPreservesChinesePinyinAndSnippets(): Unit = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dictionary = SimpleDictManager.extractDict().toString()
        val actual = search("${context.applicationInfo.nativeLibraryDir}/libsimple.so", dictionary)
        listOf("[中华]", "[中华]人民共和国[国歌]", "[南京]", "[长江大桥]").forEachIndexed { i, expected ->
            assertTrue(actual[i].contains(expected))
        }
        // Optional original 2.4.5 binary for migration verification, without shipping it in the APK.
        InstrumentationRegistry.getArguments().getString("originalSimpleExtension")?.let {
            assertEquals(search(it, dictionary), actual)
        }
    }

    private suspend fun search(library: String, dictionary: String): List<String> {
        val driver = BundledSQLiteDriver().apply { addExtension(library, "sqlite3_simple_init") }
        return driver.open(":memory:").use { connection ->
            connection.execute("SELECT jieba_dict(?)", dictionary)
            connection.execute("CREATE VIRTUAL TABLE ft USING fts5(text, tokenize='simple')")
            connection.execute("INSERT INTO ft VALUES(?)", "中华人民共和国国歌 南京市长江大桥")
            listOf("中华", "中华国歌", "nanjing", "长江大桥").map { query ->
                connection.prepare(
                    "SELECT simple_snippet(ft,0,'[',']','...',30) FROM ft WHERE text MATCH jieba_query(?)"
                ).use {
                    it.bindText(1, query)
                    assertTrue(it.step())
                    it.getText(0)
                }
            }
        }
    }

    private suspend fun SQLiteConnection.execute(sql: String, value: String? = null) {
        prepare(sql).use {
            if (value != null) it.bindText(1, value)
            it.step()
        }
    }
}
