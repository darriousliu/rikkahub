package me.rerere.rikkahub.ui.theme

import io.github.vinceglb.filekit.PlatformFile
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalResourceApi::class)
class ChatFontRuntimeTest {
    @Test
    fun importLoadsTheFontAndReplacementRetainsOnlyTheNewCustomFont() = runTest {
        val root = Files.createTempDirectory("cmp69-font-").toFile()
        try {
            val filesDir = root.resolve("files").apply { mkdirs() }
            val runtime = ChatFontRuntime(Path(filesDir.path))
            val bytes = Res.readBytes("font/jetbrains_mono.ttf")
            val source = root.resolve("中文 Font.TTF").apply { writeBytes(bytes) }
            val first = runtime.import(PlatformFile(source)).getOrThrow()
            assertEquals(source.name, first.displayName)
            assertTrue(first.relativePath.matches(Regex("fonts/chat_font\\.[0-9]+\\.ttf")))
            assertContentEquals(bytes, filesDir.resolve(first.relativePath).readBytes())
            assertNotNull(runtime.load(first.relativePath))

            val unrelated = filesDir.resolve("fonts/keep.txt").apply { writeText("keep") }
            val secondSource = root.resolve("Second.OTF").apply { writeBytes(bytes) }
            val second = runtime.import(PlatformFile(secondSource)).getOrThrow()
            assertTrue(second.relativePath.endsWith(".otf"))
            assertFalse(filesDir.resolve(first.relativePath).exists())
            assertNotNull(runtime.load(second.relativePath))
            assertEquals("keep", unrelated.readText())
            assertEquals(setOf(filesDir.resolve(second.relativePath).name, "keep.txt"),
                filesDir.resolve("fonts").listFiles()!!.map { it.name }.toSet())
            assertContentEquals(bytes, source.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidImportPreservesThePreviousFontAndRemovesTheTemporaryFile() = runTest {
        val root = Files.createTempDirectory("cmp69-font-").toFile()
        try {
            val filesDir = root.resolve("files").apply { mkdirs() }
            val runtime = ChatFontRuntime(Path(filesDir.path))
            val source = root.resolve("valid.ttf").apply { writeBytes(Res.readBytes("font/jetbrains_mono.ttf")) }
            val imported = runtime.import(PlatformFile(source)).getOrThrow()
            val invalid = root.resolve("invalid.ttf").apply { writeText("This is not a font") }
            assertIs<IllegalArgumentException>(runtime.import(PlatformFile(invalid)).exceptionOrNull())
            assertContentEquals(source.readBytes(), filesDir.resolve(imported.relativePath).readBytes())
            assertNotNull(runtime.load(imported.relativePath))
            assertEquals(listOf(filesDir.resolve(imported.relativePath).name),
                filesDir.resolve("fonts").listFiles()!!.map { it.name })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletionRestoresDefaultAndPathsOutsideFilesDirRemainUntouched() = runTest {
        val root = Files.createTempDirectory("cmp69-font-").toFile()
        try {
            val filesDir = root.resolve("files").apply { mkdirs() }
            val runtime = ChatFontRuntime(Path(filesDir.path))
            val source = root.resolve("outside.ttf").apply { writeBytes(Res.readBytes("font/jetbrains_mono.ttf")) }
            assertNull(runtime.load("../outside.ttf"))
            runtime.delete("../outside.ttf").getOrThrow()
            assertTrue(source.exists())

            val imported = runtime.import(PlatformFile(source)).getOrThrow()
            runtime.delete(imported.relativePath).getOrThrow()
            assertFalse(filesDir.resolve(imported.relativePath).exists())
            assertNull(runtime.load(imported.relativePath))
            val setting = DisplaySetting(chatFontFamily = ChatFontFamily.CUSTOM, chatCustomFontPath = imported.relativePath)
            assertSame(androidx.compose.ui.text.font.FontFamily.Default, setting.resolveChatFontFamily(runtime))
        } finally {
            root.deleteRecursively()
        }
    }
}
