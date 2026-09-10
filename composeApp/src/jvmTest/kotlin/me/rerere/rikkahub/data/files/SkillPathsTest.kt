package me.rerere.rikkahub.data.files

import java.io.File
import kotlinx.io.files.Path
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SkillPathsTest {
    @Test
    fun `parse supports CRLF frontmatter`() {
        val content = "---\r\nname: test-skill\r\ndescription: test\r\n---\r\n\r\nbody"

        val frontmatter = SkillFrontmatterParser.parse(content)

        assertEquals("test-skill", frontmatter["name"])
        assertEquals("test", frontmatter["description"])
        assertEquals("body", SkillFrontmatterParser.extractBody(content))
    }

    @Test
    fun `resolve skill dir rejects traversal and nested names`() {
        val skillsRoot = Files.createTempDirectory("skills-root").toFile()

        try {
            assertNull(SkillPaths.resolveSkillDir(Path(skillsRoot.path), "../upload"))
            assertNull(SkillPaths.resolveSkillDir(Path(skillsRoot.path), "foo/bar"))
            assertNull(SkillPaths.resolveSkillDir(Path(skillsRoot.path), "foo\\bar"))
            assertNotNull(SkillPaths.resolveSkillDir(Path(skillsRoot.path), "valid-skill"))
        } finally {
            skillsRoot.deleteRecursively()
        }
    }

    @Test
    fun `resolve skill file rejects sibling prefix escape`() {
        val skillsRoot = Files.createTempDirectory("skills-root").toFile()
        val skillDir = File(skillsRoot, "foo").apply { mkdirs() }
        File(skillsRoot, "foobar").apply { mkdirs() }

        try {
            val safeFile = SkillPaths.resolveSkillFile(Path(skillDir.path), "notes.md")
            val escapedFile = SkillPaths.resolveSkillFile(Path(skillDir.path), "../foobar/secret.md")

            assertEquals(File(skillDir, "notes.md").canonicalPath, safeFile.toString())
            assertNull(escapedFile)
        } finally {
            skillsRoot.deleteRecursively()
        }
    }
}
