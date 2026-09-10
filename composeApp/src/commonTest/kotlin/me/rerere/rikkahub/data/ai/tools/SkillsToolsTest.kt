package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillTestFixture
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.resolve
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillsToolsTest {
    private val fixture = SkillTestFixture()
    private val store = fixture.manager

    @AfterTest
    fun close() = fixture.close()

    private fun skill(name: String, directoryName: String) = SkillMetadata(
        name = name, description = "Test skill", skillDir = store.getSkillDir(directoryName)!!,
    )

    @Test
    fun testUseSkillReadsSkillDirectoryWhenDisplayNameDiffers() = runTest {
        store.saveSkill("directory-name", "---\nname: Display Name\ndescription: Test skill\n---\nSkill instructions")
        val tool = createSkillTools(
            enabledSkills = setOf("Display Name"),
            allSkills = listOf(skill(name = "Display Name", directoryName = "directory-name")),
            skillManager = store,
        ).single()

        val result = tool.execute(buildJsonObject { put("name", "Display Name") })

        assertEquals("Skill instructions", (result.single() as UIMessagePart.Text).text)
        assertFalse(fixture.root.resolve("skills/Display Name").exists())
    }

    @Test
    fun testUseSkillReadsExtraFileByRelativePath() = runTest {
        store.saveSkillFile("dir", "docs/guide.md", "Guide body")
        val tool = createSkillTools(
            enabledSkills = setOf("Skill"),
            allSkills = listOf(skill(name = "Skill", directoryName = "dir")),
            skillManager = store,
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
        val tool = createSkillTools(
            enabledSkills = setOf("Enabled"),
            allSkills = listOf(
                skill(name = "Enabled", directoryName = "enabled"),
                skill(name = "Disabled", directoryName = "disabled"),
            ),
            skillManager = store,
        ).single()

        val error = runCatching {
            tool.execute(buildJsonObject { put("name", "Disabled") })
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("is not available") == true, "unexpected: ${error?.message}")
    }

    @Test
    fun testUseSkillSurfacesMissingFile() = runTest {
        val tool = createSkillTools(
            enabledSkills = setOf("Skill"),
            allSkills = listOf(skill(name = "Skill", directoryName = "dir")),
            skillManager = store,
        ).single()

        val error = runCatching {
            tool.execute(buildJsonObject { put("name", "Skill") })
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("not found") == true, "unexpected: ${error?.message}")
    }
}
