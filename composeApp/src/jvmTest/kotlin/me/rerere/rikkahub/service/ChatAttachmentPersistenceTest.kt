package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.FilesRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.platform.FileKitFileCleaner
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.shared.template.createMessageTemplateEngine
import me.rerere.rikkahub.web.NotFoundException
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ChatAttachmentPersistenceTest {
    private val root = Files.createTempDirectory("cmp-attachment-persistence-").toFile()
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
    private val filesManager = FilesManager(
        Path(root.path), FilesRepository(database.managedFileDao()), scope,
        FileKitFileCleaner(database, settings), asyncFileIo = true,
    )
    private val repository = ConversationRepository(
        database.conversationDao(), database.messageNodeDao(), database.favoriteDao(), database,
        filesManager,
        MessageFtsManager(database, MessageFtsDialect.UNICODE61),
    )
    private val client = HttpClient(MockEngine { error("Attachment tests must not make network requests") })
    private val eventBus = AppEventBus()
    private val providers = ProviderManager(client)
    private val memory = MemoryRepository(database.memoryDao())
    private val runtime = ChatService(
        appScope = scope, appEventBus = eventBus, settingsStore = settings, conversationRepo = repository,
        memoryRepository = memory,
        generationHandler = me.rerere.rikkahub.data.ai.GenerationHandler(
            Path(root.path), providers, me.rerere.rikkahub.utils.JsonInstant, memory,
        ),
        providerManager = providers,
        folderRepository = FolderRepository(database.folderDao(), database.conversationDao()),
        booleanPreferenceStore = DataStoreBooleanPreferenceStore(preferences),
        stringPreferenceStore = DataStoreStringPreferenceStore(preferences),
        filesManager = filesManager,
        mcpManager = McpManager(settings, scope, filesManager,
            OAuthCallbackSessionFactory { error("No OAuth expected") }, client),
        templateTransformer = TemplateTransformer(createMessageTemplateEngine()),
        localTools = LocalTools(eventBus, settings, null), skillManager = SkillManager(Path(root.path), settings),
    )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        client.close()
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun `fork copies all alternatives and four local media kinds with the original metadata rules`() = runTest {
        val parts = listOf(
            UIMessagePart.Image(file("upload/source.png", 1)),
            UIMessagePart.Document(file("platform-files/attachments/中文 +.txt", 2), "Original.txt", "text/plain"),
            UIMessagePart.Video(file("upload/movie.mp4", 3)),
            UIMessagePart.Audio(file("upload/song.mp3", 4).replace("file://", "file:")),
        )
        val first = UIMessage.user("start").copy(parts = parts)
        val alternative = UIMessage.user("alternative").copy(parts = listOf(parts[0]))
        val target = UIMessage.assistant("target")
        val tail = UIMessage.assistant("must not be copied")
        val source = Conversation.ofId(Uuid.random(), messages = listOf(
            MessageNode(messages = listOf(first, alternative), selectIndex = 1),
            MessageNode(messages = listOf(target)), MessageNode(messages = listOf(tail)),
        )).copy(title = "Original title", isPinned = true, chatSuggestions = listOf("suggestion"),
            workspaceCwd = "/workspace", customSystemPrompt = "custom", modeInjectionIds = setOf(Uuid.random()),
            lorebookIds = setOf(Uuid.random()))
        load(source)
        val fork = runtime.forkConversationAtMessage(source.id, target.id)
        assertNotEquals(source.id, fork.id)
        assertEquals(2, fork.messageNodes.size)
        assertEquals(1, fork.messageNodes.first().selectIndex)
        assertEquals(listOf(first.id, alternative.id, target.id), fork.messageNodes.flatMap { it.messages }.map { it.id })
        source.messageNodes.zip(fork.messageNodes).forEach { (original, copied) -> assertNotEquals(original.id, copied.id) }
        assertEquals("", fork.title)
        assertFalse(fork.isPinned)
        assertEquals(emptyList(), fork.chatSuggestions)
        assertNull(fork.workspaceCwd)
        assertEquals(source.customSystemPrompt, fork.customSystemPrompt)
        assertEquals(source.modeInjectionIds, fork.modeInjectionIds)
        assertEquals(source.lorebookIds, fork.lorebookIds)
        assertEquals(source.assistantId, fork.assistantId)
        assertEquals(5, fork.files.toSet().size)
        assertEquals(4, source.files.size) // 原 files 集合只接受 file://；短 file: 仍可被 fork 的复制工具读取。
        fork.files.zip(listOf(1, 2, 3, 4, 1)).forEach { (copied, value) ->
            assertTrue(copied !in source.files)
            assertContentEquals(byteArrayOf(value.toByte()), File(copied.toLocalFilePath()).readBytes())
            assertEquals(File(root, "upload").path, File(copied.toLocalFilePath()).parent)
        }
        assertEquals(source.atDatabasePrecision(), repository.getConversationById(source.id))
        assertEquals(fork.atDatabasePrecision(), repository.getConversationById(fork.id))
    }

    @Test
    fun `deleting a light source conversation removes its files but the fork survives a database reload`() = runTest {
        val message = UIMessage.user("document").copy(parts = listOf(
            UIMessagePart.Document(file("upload/original.txt", 42), "original.txt", "text/plain"),
        ))
        val source = Conversation.ofId(Uuid.random(), messages = listOf(MessageNode(messages = listOf(message))))
        load(source)
        val fork = runtime.forkConversationAtMessage(source.id, message.id)
        repository.deleteConversation(source.copy(messageNodes = emptyList()))
        assertNull(repository.getConversationById(source.id))
        assertFalse(File(source.files.single().toLocalFilePath()).exists())
        val restoredFork = assertNotNull(repository.getConversationById(fork.id))
        assertContentEquals(byteArrayOf(42), File(restoredFork.files.single().toLocalFilePath()).readBytes())
        repository.deleteConversation(restoredFork)
        assertFalse(File(restoredFork.files.single().toLocalFilePath()).exists())
    }

    @Test
    fun `fork keeps remote URLs and the original URL when a local copy fails`() = runTest {
        val parts = listOf(UIMessagePart.Image("https://example.invalid/image.png"),
            UIMessagePart.Audio("data:audio/wav;base64,AAAA"),
            UIMessagePart.Document(PlatformFile(File(root, "missing.txt")).toFileUri(), "missing.txt", "text/plain"))
        val message = UIMessage.user("missing").copy(parts = parts)
        val source = Conversation.ofId(Uuid.random(), messages = listOf(MessageNode(messages = listOf(message))))
        load(source)
        assertEquals(parts, runtime.forkConversationAtMessage(source.id, message.id).currentMessages.single().parts)
    }

    @Test
    fun `fork of an unknown message preserves the original NotFound error`() = runTest {
        val source = Conversation.ofId(Uuid.random())
        load(source)
        val error = assertFailsWith<NotFoundException> { runtime.forkConversationAtMessage(source.id, Uuid.random()) }
        assertEquals("Message not found", error.message)
    }

    private suspend fun load(conversation: Conversation) {
        repository.insertConversation(conversation)
        runtime.initializeConversation(conversation.id)
    }

    private fun Conversation.atDatabasePrecision(): Conversation = copy(
        createAt = Instant.fromEpochMilliseconds(createAt.toEpochMilliseconds()),
        updateAt = Instant.fromEpochMilliseconds(updateAt.toEpochMilliseconds()),
    )

    private fun file(path: String, value: Int): String {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeBytes(byteArrayOf(value.toByte()))
        return PlatformFile(file).toFileUri()
    }
}
