package me.rerere.rikkahub.ui.components.ai

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.service.FileKitChatFileStore
import me.rerere.rikkahub.service.SharedChatAttachmentStore
import me.rerere.rikkahub.service.toFileUri
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChatInputFilesTest {
    private val root = Path(SystemTemporaryDirectory, "cmp-input-files-${Uuid.random()}")
        .canonicalFile.apply { mkdirs() }
    private val appScope = CoroutineScope(SupervisorJob())
    private val filesManager = FileKitChatFileStore(
        appScope,
        SharedChatAttachmentStore(FileKitPlatformFileStore(PlatformFile(root.toString()))),
    )

    @AfterTest
    fun cleanUp() { appScope.cancel(); root.deleteRecursively() }

    @Test
    fun deletionUsesTheInputScopeAndOnlyRemovesTheSelectedFile() = runTest {
        val selected = root.resolve("selected.txt").apply { writeBytes(byteArrayOf(1)) }
        val retained = root.resolve("retained.txt").apply { writeBytes(byteArrayOf(2)) }
        val inputScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            filesManager.deleteChatFiles(listOf(PlatformFile(selected.toString()).toFileUri()), inputScope)
            assertTrue(selected.exists())
            inputScope.coroutineContext.job.children.toList().joinAll()
            assertFalse(selected.exists())
            assertTrue(retained.exists())
        } finally {
            inputScope.cancel()
        }
    }

    @Test
    fun cancellingTheInputScopeCancelsAQueuedDeletion() = runTest {
        val selected = root.resolve("selected.txt").apply { writeBytes(byteArrayOf(1)) }
        val inputScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        filesManager.deleteChatFiles(listOf(PlatformFile(selected.toString()).toFileUri()), inputScope)
        inputScope.cancel()
        advanceUntilIdle()
        assertTrue(selected.exists())
    }
}
