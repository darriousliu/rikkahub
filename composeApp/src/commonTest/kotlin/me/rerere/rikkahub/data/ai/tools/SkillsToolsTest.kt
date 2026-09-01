package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillStore
import me.rerere.rikkahub.data.files.SkillSummary
import me.rerere.rikkahub.data.files.StoredSkillFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Only the read paths matter here; anything else fails loudly if a test reaches it. */
private class FakeSkillStore(
    private val files: Map<Pair<String, String>, String>,
) : SkillStore {
    val reads = mutableListOf<Pair<String, String>>()

    override suspend fun readSkillFile(name: String, relativePath: String): String? {
        reads += name to relativePath
        return files[name to relativePath]
    }

    override suspend fun listSkills(): List<SkillSummary> = fail("unused")
    override suspend fun saveSkill(name: String, content: String): Boolean = fail("unused")
    override suspend fun saveSkillFiles(name: String, files: Map<String, String>): Boolean = fail("unused")
    override suspend fun saveSkillFileBytes(name: String, files: Map<String, ByteArray>): Boolean = fail("unused")
    override suspend fun deleteSkill(name: String): Boolean = fail("unused")
    override suspend fun listSkillFiles(name: String): List<StoredSkillFile> = fail("unused")
    override suspend fun saveSkillFile(name: String, relativePath: String, content: String): Boolean = fail("unused")
    override suspend fun deleteSkillFile(name: String, relativePath: String): Boolean = fail("unused")
}

private fun skill(name: String, directoryName: String) = SkillSummary(
    name = name,
    directoryName = directoryName,
    description = "Test skill",
)

class SkillsToolsTest {
    @Test
    fun testUseSkillReadsSkillDirectoryWhenDisplayNameDiffers() = runTest {
        val store = FakeSkillStore(
            mapOf(
                ("directory-name" to "SKILL.md") to """
                    ---
                    name: Display Name
                    description: Test skill
                    ---
                    Skill instructions
                """.trimIndent(),
            ),
        )
        val tool = createSkillTools(
            enabledSkills = setOf("Display Name"),
            allSkills = listOf(skill(name = "Display Name", directoryName = "directory-name")),
            skillStore = store,
        ).single()

        val result = tool.execute(buildJsonObject { put("name", "Display Name") })

        assertEquals("Skill instructions", (result.single() as UIMessagePart.Text).text)
        assertEquals(listOf("directory-name" to "SKILL.md"), store.reads)
    }

    @Test
    fun testUseSkillReadsExtraFileByRelativePath() = runTest {
        val store = FakeSkillStore(mapOf(("dir" to "docs/guide.md") to "Guide body"))
        val tool = createSkillTools(
            enabledSkills = setOf("Skill"),
            allSkills = listOf(skill(name = "Skill", directoryName = "dir")),
            skillStore = store,
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("name", "Skill")
                put("path", "docs/guide.md")
            },
        )

        assertEquals("Guide body", (result.single() as UIMessagePart.Text).text)
    }

    @Test
    fun testUseSkillRejectsSkillThatIsNotEnabled() = runTest {
        val store = FakeSkillStore(emptyMap())
        val tool = createSkillTools(
            enabledSkills = setOf("Enabled"),
            allSkills = listOf(
                skill(name = "Enabled", directoryName = "enabled"),
                skill(name = "Disabled", directoryName = "disabled"),
            ),
            skillStore = store,
        ).single()

        val error = runCatching {
            tool.execute(buildJsonObject { put("name", "Disabled") })
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("is not available") == true, "unexpected: ${error?.message}")
    }

    @Test
    fun testUseSkillSurfacesMissingFile() = runTest {
        val store = FakeSkillStore(emptyMap())
        val tool = createSkillTools(
            enabledSkills = setOf("Skill"),
            allSkills = listOf(skill(name = "Skill", directoryName = "dir")),
            skillStore = store,
        ).single()

        val error = runCatching {
            tool.execute(buildJsonObject { put("name", "Skill") })
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("not found") == true, "unexpected: ${error?.message}")
    }
}
