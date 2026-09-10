package me.rerere.rikkahub.data.files

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillManagerContractTest {
    private val root = Files.createTempDirectory("skill-manager-contract").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val preferences = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    private val settings = SettingsStore(preferences, scope)
    private val manager = SkillManager(kotlinx.io.files.Path(root.path), settings)
    private val skills get() = File(root, "skills")

    @AfterTest
    fun close() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun parsesOriginalMetadataAndBodyWithoutChangingDirectoryName() {
        val content = "---\r\nname: Display Name\r\ndescription: 描述\r\ncompatibility: shell\r\n" +
            "allowed-tools: Read  Bash\r\n---\r\n\r\n正文"
        val result = assertNotNull(manager.saveSkill("disk-name", content))
        assertEquals("Display Name", result.name)
        assertEquals("描述", result.description)
        assertEquals("shell", result.compatibility)
        assertEquals(listOf("Read", "Bash"), result.allowedTools)
        assertEquals("disk-name", result.skillDir.name)
        assertEquals(content, manager.readSkillContent("disk-name"))
        assertEquals("正文", manager.readSkillBody("disk-name"))
        assertNull(manager.readSkillBody("Display Name"))
    }

    @Test
    fun invalidMetadataIsSavedButNotListed() {
        assertNull(manager.saveSkill("invalid", "---\nname: invalid\n---\nbody"))
        assertTrue(File(skills, "invalid/SKILL.md").exists())
        assertTrue(manager.listSkills().isEmpty())
    }

    @Test
    fun ignoresDirectoriesWithoutValidSkillAndKeepsDuplicateDisplayNames() {
        manager.saveSkill("one", markdown("same"))
        manager.saveSkill("two", markdown("same"))
        File(skills, "missing").mkdirs()
        File(skills, "plain.txt").writeText("ignored")
        assertEquals(listOf("same", "same"), manager.listSkills().map { it.name })
        assertEquals(setOf("one", "two"), manager.listSkills().map { it.skillDir.name }.toSet())
    }

    @Test
    fun listKeepsFilesystemOrder() {
        listOf("z", "a", "m").forEach { manager.saveSkill(it, markdown(it)) }
        assertEquals(skills.listFiles()!!.map { it.name }, manager.listSkills().map { it.skillDir.name })
    }

    @Test
    fun overwriteReplacesWholeDirectoryAndKeepsBinaryBytes() {
        assertTrue(manager.saveSkillFilesAtomically("skill", mapOf("SKILL.md" to markdown(), "old.txt" to "old")))
        val binary = byteArrayOf(0, 1, -1, 10, 127, -128)
        assertTrue(manager.saveSkillFileBytesAtomically("skill", mapOf(
            "SKILL.md" to markdown(body = "new").encodeToByteArray(), "docs/data.bin" to binary,
        )))
        assertEquals("new", manager.readSkillBody("skill"))
        assertContentEquals(binary, File(skills, "skill/docs/data.bin").readBytes())
        assertFalse(File(skills, "skill/old.txt").exists())
        assertEquals(listOf("skill"), skills.listFiles()!!.map { it.name })
    }

    @Test
    fun failedStagingPathKeepsOldDirectoryAndCleansPartialFiles() {
        manager.saveSkill("skill", markdown(body = "old"))
        assertFalse(manager.saveSkillFilesAtomically("skill", linkedMapOf(
            "SKILL.md" to markdown(body = "new"), "docs/partial.txt" to "partial", "../escape.txt" to "bad",
        )))
        assertEquals("old", manager.readSkillBody("skill"))
        assertEquals(listOf("skill"), skills.listFiles()!!.map { it.name })
        assertFalse(File(skills, "escape.txt").exists())
    }

    @Test
    fun missingSkillFileDoesNotReplaceExistingDirectory() {
        manager.saveSkill("skill", markdown(body = "old"))
        assertFalse(manager.saveSkillFilesAtomically("skill", mapOf("guide.md" to "only")))
        assertEquals("old", manager.readSkillBody("skill"))
        assertEquals(listOf("skill"), skills.listFiles()!!.map { it.name })
    }

    @Test
    fun stagingAndBackupUseOriginalOneHundredAttempts() {
        manager.saveSkill("skill", markdown(body = "old"))
        repeat(100) { File(skills, ".skill.staging.$it.tmp").mkdirs() }
        assertFalse(manager.saveSkillFilesAtomically("skill", mapOf("SKILL.md" to markdown())))
        repeat(100) { File(skills, ".skill.staging.$it.tmp").delete(); File(skills, ".skill.backup.$it.tmp").mkdirs() }
        assertFalse(manager.saveSkillFilesAtomically("skill", mapOf("SKILL.md" to markdown())))
        assertEquals("old", manager.readSkillBody("skill"))
        assertFalse(File(skills, ".skill.staging.0.tmp").exists())
    }

    @Test
    fun fileInsteadOfRootReturnsFailureWithoutReplacingIt() {
        skills.writeText("root file")
        assertNull(manager.saveSkill("skill", markdown()))
        assertEquals("root file", skills.readText())
        assertTrue(manager.listSkills().isEmpty())
    }

    @Test
    fun directoryInsteadOfSkillFileRetainsOriginalExistsCheck() {
        assertTrue(manager.saveSkillFilesAtomically("skill", mapOf("SKILL.md/child" to "child")))
        assertTrue(File(skills, "skill/SKILL.md").isDirectory)
        assertTrue(manager.listSkills().isEmpty())
    }

    @Test
    fun singleFileSavePropagatesIoFailureAndDeleteNonemptyDirectoryReturnsFalse() {
        manager.saveSkill("skill", markdown())
        manager.saveSkillFile("skill", "blocker", "file")
        assertFailsWith<java.io.IOException> { manager.saveSkillFile("skill", "blocker/child", "body") }
        manager.saveSkillFile("skill", "docs/guide.md", "guide")
        assertFalse(manager.deleteSkillFile("skill", "docs"))
        assertTrue(manager.deleteSkillFile("skill", "docs/guide.md"))
        assertFalse(manager.deleteSkillFile("skill", "docs/guide.md"))
    }

    @Test
    fun namesKeepWhitespaceAndRelativePathsKeepOriginalCanonicalRules() {
        assertNotNull(manager.saveSkill(" skill ", markdown()))
        assertTrue(File(skills, " skill /SKILL.md").exists())
        assertFalse(File(skills, "skill").exists())
        assertTrue(manager.saveSkillFile(" skill ", "docs/../guide.md", "guide"))
        assertEquals("guide", File(skills, " skill /guide.md").readText())
        assertTrue(manager.saveSkillFile(" skill ", "back\\slash.md", "backslash"))
        assertTrue(File(skills, " skill /back\\slash.md").exists())
    }

    @Test
    fun rejectsNamesAndSiblingOrAbsoluteEscapes() {
        listOf("", " ", ".", "..", "../outside", "a/b", "a\\b").forEach {
            assertNull(manager.saveSkill(it, markdown()), it)
        }
        manager.saveSkill("skill", markdown())
        assertNull(manager.resolveSkillFile("skill", "../skillsibling/secret"))
        assertNull(manager.resolveSkillFile("skill", File(root, "outside").absolutePath))
        assertNotNull(manager.resolveSkillFile("skill", File(skills, "skill/inside").absolutePath))
    }

    @Test
    fun canonicalPathsResolveMissingChildrenAndRejectSymlinkEscape() {
        manager.saveSkill("skill", markdown())
        val outside = File(root, "outside").apply { mkdirs() }
        Files.createSymbolicLink(File(skills, "alias").toPath(), outside.toPath())
        Files.createSymbolicLink(File(skills, "skill/link").toPath(), outside.toPath())
        assertNull(manager.getSkillDir("alias"))
        assertNull(manager.resolveSkillFile("skill", "link/missing/child"))
        val inside = assertNotNull(manager.resolveSkillFile("skill", "missing/../guide.md"))
        assertEquals(File(skills, "skill/guide.md").canonicalPath, inside.toString())
    }

    @Test
    fun deletingMissingSkillStillCleansEnabledNameFromEveryAssistant() = runTest {
        settings.update { it.copy(assistants = listOf(
            Assistant(name = "one", enabledSkills = setOf("missing", "keep")),
            Assistant(name = "two", enabledSkills = setOf("missing")),
        )) }
        assertTrue(manager.deleteSkill("missing"))
        val updated = settings.settingsFlow.first { it.assistants.none { "missing" in it.enabledSkills } }
        assertEquals(
            listOf(setOf("keep"), emptySet()),
            updated.assistants.filter { it.name in setOf("one", "two") }.map { it.enabledSkills },
        )
    }

    @Test
    fun deletingExistingSkillCleansOnlyRequestedName() = runTest {
        manager.saveSkill("disk-name", markdown(name = "Display Name"))
        settings.update {
            it.copy(assistants = listOf(Assistant(
                name = "fixture", enabledSkills = setOf("disk-name", "Display Name"),
            )))
        }
        assertTrue(manager.deleteSkill("disk-name"))
        assertFalse(File(skills, "disk-name").exists())
        assertEquals(setOf("Display Name"), settings.settingsFlow.first {
            it.assistants.any { a ->
                a.name in setOf("fixture", "unchanged") && a.enabledSkills == setOf("Display Name")
            }
        }.assistants.first { it.name == "fixture" }.enabledSkills)
    }

    @Test
    fun pruneUsesFrontmatterNamesAndPreservesUnrelatedSettings() = runTest {
        manager.saveSkill("disk-name", markdown(name = "Display Name"))
        settings.update { it.copy(assistants = listOf(
            Assistant(name = "unchanged", enabledSkills = setOf("Display Name", "disk-name", "ghost")),
        )) }
        assertEquals(listOf("Display Name"), manager.pruneOrphanedEnabledSkills().map { it.name })
        val assistant = settings.settingsFlow.first {
            it.assistants.any { a ->
                a.name in setOf("fixture", "unchanged") && a.enabledSkills == setOf("Display Name")
            }
        }.assistants.first { it.name == "unchanged" }
        assertEquals("unchanged", assistant.name)
    }

    @Test
    fun rejectedDeletionDoesNotAlterSettings() = runTest {
        settings.update {
            it.copy(assistants = listOf(Assistant(name = "fixture", enabledSkills = setOf("../invalid"))))
        }
        assertFalse(manager.deleteSkill("../invalid"))
        assertEquals(setOf("../invalid"), settings.settingsFlow.first {
            it.assistants.any { a -> a.name == "fixture" && "../invalid" in a.enabledSkills }
        }.assistants.first { it.name == "fixture" }.enabledSkills)
    }

    private fun markdown(name: String = "skill", body: String = "body") =
        "---\nname: $name\ndescription: test description\n---\n$body"
}
