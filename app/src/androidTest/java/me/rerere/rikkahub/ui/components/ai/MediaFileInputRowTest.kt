package me.rerere.rikkahub.ui.components.ai

import me.rerere.rikkahub.utils.delete
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.data.files.toFileUri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.ui.hooks.ChatInputState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

class MediaFileInputRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val filesManager get() = GlobalContext.get().get<FilesManager>()
    private val repository get() = GlobalContext.get().get<FilesRepository>()
    private val createdFiles = mutableListOf<ManagedFileEntity>()

    @After
    fun removeTestFiles() = runBlocking {
        createdFiles.forEach { file ->
            filesManager.getFile(file).delete()
            repository.deleteById(file.id)
        }
    }

    @Test
    fun allMediaKindsShowManagedNamesAndDraftRemovalDeletesFilesAndMetadata() {
        val files = listOf(
            createFile("CMP25 image.png", "image/png"),
            createFile("CMP25 movie.mp4", "video/mp4"),
            createFile("CMP25 audio.mp3", "audio/mpeg"),
            createFile("CMP25 文档.txt", "text/plain"),
        )
        val parts = listOf(
            UIMessagePart.Image(url(files[0])),
            UIMessagePart.Video(url(files[1])),
            UIMessagePart.Audio(url(files[2])),
            document(files[3]),
        )
        val state = ChatInputState().apply { messageContent = parts }
        show(state)
        waitForNames(files.map { it.displayName })

        files.forEachIndexed { index, file ->
            compose.onAllNodes(hasClickAction())[0].performClick()
            compose.runOnIdle {
                assertEquals(parts.drop(index + 1), state.messageContent)
                assertFalse("Draft file must be removed synchronously on Android", filesManager.getFile(file).exists())
            }
            compose.waitUntil(5_000) { runBlocking { repository.getById(file.id) == null } }
        }
    }

    @Test
    fun editingRetainsOriginalAttachmentButDeletesNewlyAddedAttachment() {
        val original = createFile("CMP25 original.txt", "text/plain")
        val added = createFile("CMP25 added.txt", "text/plain")
        val state = ChatInputState().apply {
            setContents(listOf(document(original)))
            editingMessage = Uuid.random()
            messageContent += document(added)
        }
        show(state)
        waitForNames(listOf(original.displayName, added.displayName))

        compose.onAllNodes(hasClickAction())[0].performClick()
        compose.runOnIdle {
            assertEquals(listOf(document(added)), state.messageContent)
            assertTrue(filesManager.getFile(original).exists())
            assertNotNull(runBlocking { repository.getById(original.id) })
        }
        compose.onAllNodes(hasClickAction())[0].performClick()
        compose.runOnIdle {
            assertTrue(state.messageContent.isEmpty())
            assertTrue(filesManager.getFile(original).exists())
            assertFalse(filesManager.getFile(added).exists())
        }
        compose.waitUntil(5_000) { runBlocking { repository.getById(added.id) == null } }
    }

    @Test
    fun removingRemoteAttachmentLeavesTheLocalDraftUntouched() {
        val local = createFile("CMP25 local.txt", "text/plain")
        val remote = UIMessagePart.Document("https://example.invalid/remote.txt", "remote.txt", "text/plain")
        val state = ChatInputState().apply { messageContent = listOf(remote, document(local)) }
        show(state)
        waitForNames(listOf("remote.txt", local.displayName))

        compose.onAllNodes(hasClickAction())[0].performClick()
        compose.runOnIdle {
            assertEquals(listOf(document(local)), state.messageContent)
            assertTrue(filesManager.getFile(local).exists())
            assertNotNull(runBlocking { repository.getById(local.id) })
        }
    }

    @Test
    fun fileMetadataUpdatesRefreshTheDisplayedName() {
        val file = createFile("CMP25 before.txt", "text/plain")
        val state = ChatInputState().apply { messageContent = listOf(document(file)) }
        show(state)
        waitForNames(listOf(file.displayName))

        runBlocking { repository.update(file.copy(displayName = "CMP25 after.txt")) }
        waitForNames(listOf("CMP25 after.txt"))
        compose.onNodeWithText(file.displayName).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(document(file)), state.messageContent) }
    }

    private fun show(state: ChatInputState) {
        compose.setContent { MaterialTheme { MediaFileInputRow(state) } }
    }

    private fun waitForNames(names: List<String>) {
        compose.waitUntil(5_000) {
            names.all { compose.onAllNodesWithText(it).fetchSemanticsNodes().size == 1 }
        }
        names.forEach { compose.onNodeWithText(it).assertExists() }
    }

    private fun createFile(name: String, mime: String): ManagedFileEntity = runBlocking {
        filesManager.saveManagedFromBytes(FileFolders.UPLOAD, "CMP25 fixture".encodeToByteArray(), name, mime)
            .also(createdFiles::add)
    }

    private fun url(file: ManagedFileEntity): String = filesManager.getFile(file).toFileUri()

    private fun document(file: ManagedFileEntity) = UIMessagePart.Document(url(file), file.displayName, file.mimeType)
}
