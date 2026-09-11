package me.rerere.rikkahub.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.toFileUri
import me.rerere.rikkahub.service.toLocalFilePath
import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FileKitFileCleanerTest {
    private val root = Files.createTempDirectory("cmp-14b-cleaner-").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val preferences = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    private val settings = SettingsStore(preferences, scope)
    private val database = buildAppDatabase(
        Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
        BundledSQLiteDriver(), MessageFtsDialect.UNICODE61,
    )
    private val cleaner = FileKitFileCleaner(database, settings)
    private val repository = ConversationRepository(
        database.conversationDao(), database.messageNodeDao(), database.favoriteDao(), database, cleaner,
        MessageFtsManager(database, MessageFtsDialect.UNICODE61),
    )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun `old fork retains shared files in every alternative and nested tool result until its deletion`() = runTest {
        val shared = file("platform-files/attachments/中文 + %2F.txt")
        val unique = listOf(file("upload/image.png"), file("upload/audio.wav"), file("upload/video.mp4"))
        val source = conversation(listOf(document(shared)), listOf(
            UIMessagePart.Image(unique[0]), UIMessagePart.Audio(unique[1]),
            UIMessagePart.Tool("id", "tool", "{}", output = listOf(UIMessagePart.Video(unique[2])))))
        val fork = conversation(listOf(UIMessagePart.Text("selected")), listOf(
            UIMessagePart.Tool("fork-tool", "tool", "{}", output = listOf(document(shared)))))
        repository.insertConversation(source)
        repository.insertConversation(fork)
        val originalFork = assertNotNull(repository.getConversationById(fork.id))
        repository.deleteConversation(source.copy(messageNodes = emptyList()))
        assertNull(repository.getConversationById(source.id))
        assertContentEquals(byteArrayOf(1, 2, 3), File(shared.toLocalFilePath()).readBytes())
        unique.forEach { assertFalse(File(it.toLocalFilePath()).exists()) }
        assertEquals(originalFork, repository.getConversationById(fork.id))
        repository.deleteConversation(originalFork)
        assertFalse(File(shared.toLocalFilePath()).exists())
    }

    @Test
    fun `shared files in upload are also preserved and ordinary independent files are removed`() = runTest {
        val shared = file("upload/restored-from-backup.txt")
        val independent = file("upload/independent.txt")
        val source = conversation(listOf(document(shared), document(independent)))
        val fork = conversation(listOf(document(shared.replace("file://", "file:"))))
        repository.insertConversation(source)
        repository.insertConversation(fork)
        repository.deleteConversation(source)
        assertTrue(File(shared.toLocalFilePath()).exists())
        assertFalse(File(independent.toLocalFilePath()).exists())
    }

    @Test
    fun `favorite snapshot protects the file when its conversation is deleted`() = runTest {
        val uri = file("upload/favorite.png")
        val source = conversation(listOf(UIMessagePart.Image(uri)))
        repository.insertConversation(source)
        database.favoriteDao().upsert(FavoriteEntity("favorite", "node", "node:${source.id}:test", "{}",
            JsonInstant.encodeToString(source.messageNodes), null, 1, 1))
        repository.deleteConversation(source)
        assertTrue(File(uri.toLocalFilePath()).exists())
        assertNotNull(database.favoriteDao().getByRefKey("node:${source.id}:test"))
    }

    @Test
    fun `avatar and background cleanup removes only the supplied reference before settings update`() = runTest {
        val uri = file("platform-files/images/shared.png")
        val assistant = Assistant(name = "source", avatar = Avatar.Image(uri), background = uri.toLocalFilePath())
        settings.update { it.copy(assistants = listOf(assistant)) }
        cleaner.deleteLocalAssets(listOf(uri))
        assertTrue(File(uri.toLocalFilePath()).exists())
        settings.update { it.copy(assistants = listOf(assistant.copy(avatar = Avatar.Dummy))) }
        cleaner.deleteLocalAssets(listOf(uri.toLocalFilePath()))
        assertFalse(File(uri.toLocalFilePath()).exists())
    }

    @Test
    fun `removing an assistant with duplicate asset locations deletes its unique file`() = runTest {
        val uri = file("upload/both.png")
        settings.update { it.copy(assistants = listOf(Assistant(avatar = Avatar.Image(uri), background = uri))) }
        cleaner.deleteLocalAssets(listOf(uri, uri))
        assertFalse(File(uri.toLocalFilePath()).exists())
    }

    @Test
    fun `assistant deletion through the original VM preserves another assistant and its background`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = AssistantVM(settings, MemoryRepository(database.memoryDao()), repository, cleaner)
        try {
            val shared = file("platform-files/images/background.png")
            val avatar = file("upload/avatar.png")
            val first = Assistant(name = "first", avatar = Avatar.Image(avatar), background = shared)
            val second = first.copy(id = Uuid.random(), name = "retained", avatar = Avatar.Dummy)
            settings.update { it.copy(assistants = listOf(first, second)) }
            vm.settings.first { it.assistants.any { assistant -> assistant.id == first.id } }
            vm.removeAssistant(first)
            withContext(Dispatchers.Default) { withTimeout(5_000) {
                settings.settingsFlowRaw.first { it.assistants.none { assistant -> assistant.id == first.id } }
                vm.settings.first { it.assistants.none { assistant -> assistant.id == first.id } }
            } }
            assertFalse(File(avatar.toLocalFilePath()).exists())
            assertTrue(File(shared.toLocalFilePath()).exists())
            assertEquals(second, settings.settingsFlow.value.assistants.single { it.id == second.id })
            vm.removeAssistant(second)
            withContext(Dispatchers.Default) { withTimeout(5_000) {
                settings.settingsFlowRaw.first { it.assistants.none { assistant -> assistant.id == second.id } }
            } }
            assertFalse(File(shared.toLocalFilePath()).exists())
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `conversation deletion preserves files still used by assistant presets or the user avatar`() = runTest {
        val avatar = file("upload/user.png")
        val preset = file("upload/preset.pdf")
        settings.update { it.copy(displaySetting = it.displaySetting.copy(userAvatar = Avatar.Image(avatar)),
            assistants = listOf(Assistant(presetMessages = listOf(UIMessage.user("preset").copy(parts = listOf(document(preset))))))) }
        val source = conversation(listOf(UIMessagePart.Image(avatar), document(preset)))
        repository.insertConversation(source)
        repository.deleteConversation(source)
        assertTrue(File(avatar.toLocalFilePath()).exists())
        assertTrue(File(preset.toLocalFilePath()).exists())
    }

    @Test
    fun `missing files and failed nonempty directory deletes keep original best effort behavior`() = runTest {
        val child = file("upload/directory/child.txt")
        cleaner.deleteChatFiles(listOf(PlatformFile(File(root, "missing.txt")).toFileUri(),
            PlatformFile(File(root, "upload/directory")).toFileUri(),
            "https://example.invalid/file.txt", "content://files/123", "data:text/plain;base64,AA=="))
        assertTrue(File(child.toLocalFilePath()).exists())
    }

    private fun conversation(vararg alternatives: List<UIMessagePart>): Conversation = Conversation.ofId(
        Uuid.random(), messages = listOf(MessageNode(messages = alternatives.map {
            UIMessage.user("retained metadata").copy(parts = it)
        }, selectIndex = 0)),
    )

    private fun document(uri: String) = UIMessagePart.Document(uri, "unchanged name.txt", "text/plain")

    private fun file(relative: String): String {
        val file = File(root, relative)
        file.parentFile.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return PlatformFile(file).toFileUri()
    }
}
