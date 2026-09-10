package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import me.rerere.rikkahub.utils.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DISPLAY_NAME = "Display Skill"
private const val DIRECTORY_NAME = "my-skill"

class SkillDetailVMTest {
    private val fixture = SkillTestFixture()
    private val models = ViewModelStore()
    private lateinit var vm: SkillDetailVM
    private val original = "---\nname: $DISPLAY_NAME\ndescription: d\n---\nbody"
    private val directory get() = fixture.manager.getSkillDir(DIRECTORY_NAME)!!

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fixture.manager.saveSkill(DIRECTORY_NAME, original)
        vm = SkillDetailVM(fixture.manager).also { models.put("skill", it) }
        vm.init(DISPLAY_NAME)
    }

    @AfterTest
    fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
        fixture.close()
    }

    private suspend fun ready() = withContext(Dispatchers.Default) {
        withTimeout(10_000) { vm.tree.first { it.isNotEmpty() } }
    }

    private fun skillFile() = SkillFile(directory.resolve("SKILL.md"), "SKILL.md")

    @Test
    fun testListsFilesFromDirectoryWhenDisplayNameDiffers() = runTest {
        val tree = ready()
        assertEquals(1, tree.size)
        assertEquals(directory.resolve("SKILL.md"), (tree.single() as SkillFileNode.FileNode).skillFile.file)
    }

    @Test
    fun testReadUsesDirectoryName() = runTest {
        ready()
        val content = CompletableDeferred<String?>()
        vm.readFile(skillFile()) { content.complete(it) }
        assertEquals(original, withContext(Dispatchers.Default) { withTimeout(10_000) { content.await() } })
    }

    @Test
    fun testReadStillRejectsFileOutsideTheSkill() = runTest {
        ready()
        val outside = fixture.root.resolve("outside.md").apply { writeText("outside") }
        val content = CompletableDeferred<String?>()
        vm.readFile(SkillFile(outside, "../../outside.md")) { content.complete(it) }
        assertNull(withContext(Dispatchers.Default) { withTimeout(10_000) { content.await() } })
    }

    @Test
    fun testSaveUsesDirectoryName() = runTest {
        ready()
        val result = CompletableDeferred<String?>()
        vm.saveFile("docs/guide.md", "content") { result.complete(it) }
        assertNull(withContext(Dispatchers.Default) { withTimeout(10_000) { result.await() } })
        assertEquals("content", directory.resolve("docs/guide.md").readText())
        assertFalse(fixture.root.resolve("skills/$DISPLAY_NAME").exists())
    }

    @Test
    fun testDeleteUsesDirectoryName() = runTest {
        ready()
        val result = CompletableDeferred<Boolean>()
        vm.deleteFile(skillFile()) { result.complete(it) }
        assertTrue(withContext(Dispatchers.Default) { withTimeout(10_000) { result.await() } })
        assertFalse(directory.resolve("SKILL.md").exists())
    }

    @Test
    fun testRenamingTheSkillInFrontmatterIsStillRejected() = runTest {
        ready()
        val result = CompletableDeferred<String?>()
        vm.saveFile("SKILL.md", "---\nname: Renamed\ndescription: d\n---\nbody") { result.complete(it) }
        val error = withContext(Dispatchers.Default) { withTimeout(10_000) { result.await() } }
        assertTrue(error?.contains(DISPLAY_NAME) == true, "unexpected: $error")
        assertEquals(original, directory.resolve("SKILL.md").readText())
    }
}
