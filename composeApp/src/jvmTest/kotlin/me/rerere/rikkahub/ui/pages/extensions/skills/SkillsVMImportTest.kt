package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.lifecycle.ViewModelStore
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.data.files.SkillTestFixture
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.readText
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeBytes
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillsVMImportTest {
    private val fixture = SkillTestFixture()
    private val models = ViewModelStore()
    private val requests = mutableListOf<String>()
    private val responses = mutableMapOf<String, String>()
    private val client = HttpClient(MockEngine { request ->
        val url = request.url.toString()
        requests += url
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        responses[url]?.let { respond(it) } ?: respond("not found", HttpStatusCode.NotFound)
    })
    private lateinit var vm: SkillsVM

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = SkillsVM(fixture.manager, client).also { models.put("skills", it) }
    }

    @AfterTest
    fun close() {
        models.clear()
        Dispatchers.resetMain()
        client.close()
        fixture.close()
    }

    @Test
    fun markdownImportKeepsBodyMetadataAndReportsMissingDescription() = runTest {
        val content = markdown("unicode", "内容\n第二行").replace("\n", "\r\n")
        assertEquals(true to "unicode", import("source.md", content.encodeToByteArray()))
        assertEquals(content, fixture.manager.readSkillContent("unicode"))
        val failed = import("invalid.md", "---\nname: invalid\n---\nbody".encodeToByteArray())
        assertFalse(failed.first)
        assertTrue(failed.second.contains("description"))
        assertFalse(fixture.manager.getSkillDir("invalid")!!.exists())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun externalZipKeepsBinaryAndSeparatesNestedSkills() = runTest {
        val binary = byteArrayOf(0, -1, -128, 100)
        val archive = zip(linkedMapOf(
            "repo/skill.md" to markdown("outer").encodeToByteArray(),
            "repo/docs/data.bin" to binary,
            "repo/nested/SKILL.md" to markdown("inner").encodeToByteArray(),
            "repo/nested/guide.md" to "inside".encodeToByteArray(),
        ))
        assertEquals(true to "inner, outer", import("fixture.ZIP", archive))
        assertEquals(setOf("inner", "outer"), vm.skills.value.map { it.name }.toSet())
        val binaryFile = fixture.manager.getSkillDir("outer")!!.resolve("docs/data.bin")
        assertContentEquals(binary, java.io.File(binaryFile.toString()).readBytes())
        assertFalse(fixture.manager.getSkillDir("outer")!!.resolve("nested/SKILL.md").exists())
        assertEquals("inside", fixture.manager.getSkillDir("inner")!!.resolve("guide.md").readText())
    }

    @Test
    fun duplicateZipNamesOverwriteInOriginalSortedOrder() = runTest {
        val archive = zip(linkedMapOf(
            "a/SKILL.md" to markdown("same", "first").encodeToByteArray(),
            "a/old.md" to "old".encodeToByteArray(),
            "z/SKILL.md" to markdown("same", "last").encodeToByteArray(),
            "z/new.md" to "new".encodeToByteArray(),
        ))
        assertEquals(true to "same", import("source.bin", archive))
        assertEquals("last", fixture.manager.readSkillBody("same"))
        assertFalse(fixture.manager.getSkillDir("same")!!.resolve("old.md").exists())
        assertEquals("new", fixture.manager.getSkillDir("same")!!.resolve("new.md").readText())
    }

    @Test
    fun laterInvalidSkillRetainsEarlierImportAsInOriginalSequentialImport() = runTest {
        val archive = zip(linkedMapOf(
            "a/SKILL.md" to markdown("first").encodeToByteArray(),
            "z/SKILL.md" to "---\nname: invalid\n---\nbody".encodeToByteArray(),
        ))
        assertFalse(import("source.zip", archive).first)
        assertEquals("body", fixture.manager.readSkillBody("first"))
        assertFalse(fixture.manager.getSkillDir("invalid")!!.exists())
    }

    @Test
    fun zipTraversalAndMissingSkillLeaveExistingFilesUntouched() = runTest {
        fixture.manager.saveSkill("old", markdown("old"))
        assertFalse(import("traversal.zip", zip(mapOf("../escape.md" to "bad".encodeToByteArray()))).first)
        assertFalse(import("missing.zip", zip(mapOf("guide.md" to "only".encodeToByteArray()))).first)
        assertEquals("body", fixture.manager.readSkillBody("old"))
        assertFalse(fixture.root.resolve("escape.md").exists())
    }

    @Test
    fun githubImportKeepsRequestsAndSavesAllDownloadedFiles() = runTest {
        val base = "https://api.github.com/repos/owner/repo/contents/skills/demo?ref=main"
        responses[base] = """[
            {"type":"file","path":"skills/demo/SKILL.md","download_url":"https://fixture/SKILL.md"},
            {"type":"dir","path":"skills/demo/docs"}
        ]"""
        responses["https://api.github.com/repos/owner/repo/contents/skills/demo/docs?ref=main"] =
            """[{"type":"file","path":"skills/demo/docs/guide.md","download_url":"https://fixture/guide.md"}]"""
        responses["https://fixture/SKILL.md"] = markdown("github")
        responses["https://fixture/guide.md"] = "guide"
        assertEquals(true to "github", github("https://github.com/owner/repo/tree/main/skills/demo"))
        assertEquals(2, requests.count { it == "https://fixture/SKILL.md" })
        assertEquals("guide", fixture.manager.getSkillDir("github")!!.resolve("docs/guide.md").readText())
    }

    @Test
    fun githubDownloadFailureDoesNotPartiallyReplaceExistingSkill() = runTest {
        fixture.manager.saveSkill("github", markdown("github", "old"))
        responses["https://api.github.com/repos/owner/repo/contents/?ref=HEAD"] =
            """[
                {"type":"file","path":"SKILL.md","download_url":"https://fixture/SKILL.md"},
                {"type":"file","path":"missing.md","download_url":"https://fixture/missing.md"}
            ]"""
        responses["https://fixture/SKILL.md"] = markdown("github", "new")
        val result = github("https://github.com/owner/repo")
        assertFalse(result.first)
        assertTrue(result.second.contains("missing.md"))
        assertEquals("old", fixture.manager.readSkillBody("github"))
    }

    private suspend fun import(name: String, bytes: ByteArray): Pair<Boolean, String> {
        val path = fixture.root.resolve(name).apply { writeBytes(bytes) }
        val result = CompletableDeferred<Pair<Boolean, String>>()
        vm.importSkillFromFile(PlatformFile(path)) { success, message -> result.complete(success to message) }
        return withContext(Dispatchers.Default) { withTimeout(10_000) { result.await() } }
    }

    private suspend fun github(url: String): Pair<Boolean, String> {
        val result = CompletableDeferred<Pair<Boolean, String>>()
        vm.importSkillFromGitHub(url) { success, message -> result.complete(success to message) }
        return withContext(Dispatchers.Default) { withTimeout(10_000) { result.await() } }
    }

    private fun markdown(name: String, body: String = "body") = "---\nname: $name\ndescription: description\n---\n$body"

    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
