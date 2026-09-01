package me.rerere.rikkahub.ui.pages.extensions.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.rerere.rikkahub.data.files.SkillStore
import me.rerere.rikkahub.data.files.SkillSummary
import me.rerere.rikkahub.data.files.StoredSkillFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val DISPLAY_NAME = "Display Skill"
private const val DIRECTORY_NAME = "my-skill"

/** Records the skill key every call receives so tests can assert it is the directory name. */
private class RecordingSkillStore : SkillStore {
    val listedKeys = mutableListOf<String>()
    val readKeys = mutableListOf<String>()
    val savedKeys = mutableListOf<String>()
    val deletedKeys = mutableListOf<String>()

    override suspend fun listSkills(): List<SkillSummary> = listOf(
        SkillSummary(name = DISPLAY_NAME, directoryName = DIRECTORY_NAME, description = "d"),
    )

    override suspend fun listSkillFiles(name: String): List<StoredSkillFile> {
        listedKeys += name
        if (name != DIRECTORY_NAME) return emptyList()
        return listOf(
            StoredSkillFile(name = "SKILL.md", relativePath = "SKILL.md", size = 12, isDirectory = false),
        )
    }

    override suspend fun readSkillFile(name: String, relativePath: String): String? {
        readKeys += name
        return if (name == DIRECTORY_NAME) "body" else null
    }

    override suspend fun saveSkillFile(name: String, relativePath: String, content: String): Boolean {
        savedKeys += name
        return name == DIRECTORY_NAME
    }

    override suspend fun deleteSkillFile(name: String, relativePath: String): Boolean {
        deletedKeys += name
        return name == DIRECTORY_NAME
    }

    override suspend fun saveSkill(name: String, content: String): Boolean = fail("unused")
    override suspend fun saveSkillFiles(name: String, files: Map<String, String>): Boolean = fail("unused")
    override suspend fun saveSkillFileBytes(name: String, files: Map<String, ByteArray>): Boolean = fail("unused")
    override suspend fun deleteSkill(name: String): Boolean = fail("unused")
}

class SkillDetailVMTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun skillFile() = SkillFile(name = "SKILL.md", relativePath = "SKILL.md", size = 12)

    @Test
    fun testListsFilesFromDirectoryWhenDisplayNameDiffers() = runTest(dispatcher) {
        val store = RecordingSkillStore()
        val vm = SkillDetailVM(store)

        vm.init(DISPLAY_NAME)
        advanceUntilIdle()

        assertEquals(listOf(DIRECTORY_NAME), store.listedKeys)
        assertEquals(1, vm.tree.value.size)
    }

    @Test
    fun testReadUsesDirectoryName() = runTest(dispatcher) {
        val store = RecordingSkillStore()
        val vm = SkillDetailVM(store)
        vm.init(DISPLAY_NAME)
        advanceUntilIdle()

        var content: String? = null
        vm.readFile(skillFile()) { content = it }
        advanceUntilIdle()

        assertEquals(listOf(DIRECTORY_NAME), store.readKeys)
        assertEquals("body", content)
    }

    @Test
    fun testSaveUsesDirectoryName() = runTest(dispatcher) {
        val store = RecordingSkillStore()
        val vm = SkillDetailVM(store)
        vm.init(DISPLAY_NAME)
        advanceUntilIdle()

        var error: String? = "unset"
        vm.saveFile("docs/guide.md", "content") { error = it }
        advanceUntilIdle()

        assertEquals(listOf(DIRECTORY_NAME), store.savedKeys)
        assertNull(error)
    }

    @Test
    fun testDeleteUsesDirectoryName() = runTest(dispatcher) {
        val store = RecordingSkillStore()
        val vm = SkillDetailVM(store)
        vm.init(DISPLAY_NAME)
        advanceUntilIdle()

        var success = false
        vm.deleteFile(skillFile()) { success = it }
        advanceUntilIdle()

        assertEquals(listOf(DIRECTORY_NAME), store.deletedKeys)
        assertTrue(success)
    }

    @Test
    fun testRenamingTheSkillInFrontmatterIsStillRejected() = runTest(dispatcher) {
        val store = RecordingSkillStore()
        val vm = SkillDetailVM(store)
        vm.init(DISPLAY_NAME)
        advanceUntilIdle()

        var error: String? = null
        vm.saveFile("SKILL.md", "---\nname: Renamed\ndescription: d\n---\nbody") { error = it }
        advanceUntilIdle()

        assertTrue(error?.contains(DISPLAY_NAME) == true, "unexpected: $error")
        assertTrue(store.savedKeys.isEmpty())
    }
}
