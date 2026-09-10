package me.rerere.rikkahub.data.files

import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.isDirectory
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.readText
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillFileSystemTest {
    private val fixture = SkillTestFixture()
    private val manager = fixture.manager
    private val content = "---\nname: Display Name\ndescription: 描述\n---\nbody"

    @AfterTest
    fun close() = fixture.close()

    @Test
    fun resolvesMissingChildrenAndDotSegments() {
        val directory = manager.getSkillDir("disk")!!
        val file = manager.resolveSkillFile("disk", "missing/../guide.md")!!
        assertEquals(directory.resolve("guide.md"), file)
        assertTrue(file.isAbsolute)
        assertEquals(file, file.canonicalFile)
    }

    @Test
    fun rejectsEscapesButAllowsAbsolutePathInsideSkill() {
        val directory = manager.getSkillDir("disk")!!
        assertNull(manager.resolveSkillFile("disk", "../disk-sibling/file"))
        assertNull(manager.resolveSkillFile("disk", fixture.root.resolve("outside").toString()))
        assertEquals(
            directory.resolve("inside"), manager.resolveSkillFile("disk", directory.resolve("inside").toString()),
        )
        assertEquals(directory, manager.resolveSkillFile("disk", "."))
    }

    @Test
    fun keepsWhitespaceAndBackslashOnUnix() {
        assertNotNull(manager.saveSkill(" disk ", content))
        assertTrue(manager.saveSkillFile(" disk ", "a\\b.md", "bytes"))
        assertEquals("bytes", manager.getSkillDir(" disk ")!!.resolve("a\\b.md").readText())
        assertFalse(manager.getSkillDir("disk")!!.exists())
        assertEquals("Display Name", manager.listSkills().single().name)
    }

    @Test
    fun replacesExistingDirectoryUsingRenameAndCleansStaging() {
        assertTrue(manager.saveSkillFilesAtomically("disk", mapOf("SKILL.md" to content, "old.txt" to "old")))
        assertTrue(manager.saveSkillFilesAtomically("disk", mapOf("SKILL.md" to content, "docs/new.txt" to "new")))
        assertFalse(manager.getSkillDir("disk")!!.resolve("old.txt").exists())
        assertEquals("new", manager.getSkillDir("disk")!!.resolve("docs/new.txt").readText())
        assertEquals(listOf("disk"), manager.getSkillsDir().listFiles()!!.map { it.name })
    }

    @Test
    fun failedSaveLeavesOriginalAndRemovesPartialStaging() {
        manager.saveSkill("disk", content)
        assertFalse(manager.saveSkillFilesAtomically("disk", linkedMapOf("SKILL.md" to "new", "../escape" to "bad")))
        assertEquals(content, manager.readSkillContent("disk"))
        assertEquals(listOf("disk"), manager.getSkillsDir().listFiles()!!.map { it.name })
        assertFalse(manager.getSkillsDir().resolve("escape").exists())
    }

    @Test
    fun rootFileAndNonemptyFileDeletionFollowOriginalBooleanResults() {
        fixture.root.resolve("skills").writeText("root")
        assertNull(manager.saveSkill("disk", content))
        assertTrue(manager.listSkills().isEmpty())
        assertEquals("root", fixture.root.resolve("skills").readText())
    }

    @Test
    fun existenceCheckForSkillFileDoesNotAddRegularFileGuard() {
        assertTrue(manager.saveSkillFilesAtomically("disk", mapOf("SKILL.md/child" to "child")))
        assertTrue(manager.getSkillDir("disk")!!.resolve("SKILL.md").isDirectory)
        assertTrue(manager.listSkills().isEmpty())
    }

    @Test
    fun keepsOriginalTemporaryDirectoryCollisionLimit() {
        manager.saveSkill("disk", content)
        repeat(100) { manager.getSkillsDir().resolve(".disk.staging.$it.tmp").mkdirs() }
        assertFalse(manager.saveSkillFilesAtomically("disk", mapOf("SKILL.md" to "new")))
        assertEquals(content, manager.readSkillContent("disk"))
    }
}
