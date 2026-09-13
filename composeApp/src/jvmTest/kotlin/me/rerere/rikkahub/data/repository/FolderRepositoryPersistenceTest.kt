package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.files.testFilesManager
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.async.step
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.entity.FolderEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.model.MessageNode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FolderRepositoryPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var folders: FolderRepository
    private lateinit var conversations: ConversationRepository

    @BeforeTest
    fun setUp() {
        database = buildAppDatabase(
            Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            BundledSQLiteDriver(),
            MessageFtsDialect.UNICODE61,
        )
        folders = FolderRepository(database.folderDao(), database.conversationDao())
        conversations = ConversationRepository(
            database.conversationDao(),
            database.messageNodeDao(),
            database.favoriteDao(),
            database,
            testFilesManager(),
            MessageFtsManager(database, MessageFtsDialect.UNICODE61),
        )
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `creation preserves names and defaults without adding repository validation`() = runTest {
        val assistantId = Uuid.random()
        val ids = mutableSetOf<Uuid>()
        for (name in listOf("文件夹 \"引用\"\n第二行", "", "  ", "重复", "重复")) {
            val before = Clock.System.now()
            val created = folders.createFolder(assistantId, name)
            val after = Clock.System.now()

            assertTrue(ids.add(created.id))
            assertEquals(assistantId, created.assistantId)
            assertEquals(name, created.name)
            assertEquals(0, created.sortIndex)
            assertTrue(created.createAt in before..after)
            val persisted = assertNotNull(folders.getFolderById(created.id))
            assertEquals(
                created.copy(createAt = Instant.fromEpochMilliseconds(created.createAt.toEpochMilliseconds())),
                persisted,
            )
        }
        assertEquals(ids, folders.getFoldersOfAssistant(assistantId).first().map { it.id }.toSet())
    }

    @Test
    fun `legacy folder fields and assistant scoped ordering survive reads`() = runTest {
        val assistantId = Uuid.random()
        val older = Folder(
            id = Uuid.random(), assistantId = assistantId, name = "较早", sortIndex = 1,
            createAt = Instant.fromEpochMilliseconds(-1),
        )
        val newer = older.copy(id = Uuid.random(), name = "较晚", createAt = Instant.fromEpochMilliseconds(0))
        val first = newer.copy(id = Uuid.random(), name = "排序优先", sortIndex = -1)
        val otherAssistant = first.copy(id = Uuid.random(), assistantId = Uuid.random(), name = "其他助手")
        for (folder in listOf(newer, otherAssistant, older, first)) {
            database.folderDao().insert(
                FolderEntity(
                    id = folder.id.toString(), assistantId = folder.assistantId.toString(), name = folder.name,
                    sortIndex = folder.sortIndex, createAt = folder.createAt.toEpochMilliseconds(),
                )
            )
            assertEquals(folder, folders.getFolderById(folder.id))
        }

        assertEquals(listOf(first, older, newer), folders.getFoldersOfAssistant(assistantId).first())
        assertEquals(listOf(otherAssistant), folders.getFoldersOfAssistant(otherAssistant.assistantId).first())
        assertNull(folders.getFolderById(Uuid.random()))
    }

    @Test
    fun `rename changes only the target name and missing ids remain a no op`() = runTest {
        val assistantId = Uuid.random()
        val targetId = folders.createFolder(assistantId, "旧名称").id
        val otherId = folders.createFolder(assistantId, "另一个").id
        val original = assertNotNull(folders.getFolderById(targetId))
        val other = assertNotNull(folders.getFolderById(otherId))
        for (name in listOf(" 重命名\n保留空格 ", "")) {
            folders.renameFolder(targetId, name)
            assertEquals(original.copy(name = name), folders.getFolderById(targetId))
            assertEquals(other, folders.getFolderById(otherId))
        }
        folders.renameFolder(Uuid.random(), "不存在")
        assertEquals(2, folders.getFoldersOfAssistant(assistantId).first().size)
    }

    @Test
    fun `deleting a folder unfiles its conversations without deleting content or unrelated data`() = runTest {
        val assistantId = Uuid.random()
        val target = folders.createFolder(assistantId, "待删除")
        val other = folders.createFolder(assistantId, "保留")
        val first = conversation(assistantId, target.id)
        val second = conversation(assistantId, target.id)
        val unrelated = conversation(assistantId, other.id)
        val unfiled = conversation(assistantId, null)
        for (conversation in listOf(first, second, unrelated, unfiled)) {
            conversations.insertConversation(conversation)
        }

        folders.deleteFolder(target.id)

        assertNull(folders.getFolderById(target.id))
        assertEquals(listOf(other.id), folders.getFoldersOfAssistant(assistantId).first().map { it.id })
        assertEquals(first.copy(folderId = null), conversations.getConversationById(first.id))
        assertEquals(second.copy(folderId = null), conversations.getConversationById(second.id))
        assertEquals(unrelated, conversations.getConversationById(unrelated.id))
        assertEquals(unfiled, conversations.getConversationById(unfiled.id))
        assertEquals(4, conversations.countConversations())
        assertEquals(
            setOf(first.id, second.id, unfiled.id),
            conversations.getUnfiledConversationsOfAssistantPage(assistantId, 0, 10).items.map { it.id }.toSet(),
        )
        assertEquals(
            listOf(unrelated.id),
            conversations.getConversationsOfFolderPage(other.id, 0, 10).items.map { it.id },
        )
    }

    @Test
    fun `deleting a missing folder still clears existing orphaned references`() = runTest {
        val missingFolderId = Uuid.random()
        val original = conversation(Uuid.random(), missingFolderId)
        conversations.insertConversation(original)

        folders.deleteFolder(missingFolderId)
        folders.deleteFolder(missingFolderId)

        assertEquals(original.copy(folderId = null), conversations.getConversationById(original.id))
    }

    @Test
    fun `failure while clearing references leaves the folder and conversations intact`() = runTest {
        val folder = folders.createFolder(Uuid.random(), "不能清空关联")
        val original = conversation(folder.assistantId, folder.id)
        conversations.insertConversation(original)
        executeSql(
            """
            CREATE TRIGGER block_folder_clear BEFORE UPDATE OF folder_id ON conversationentity
            WHEN OLD.folder_id = '${folder.id}'
            BEGIN SELECT RAISE(ABORT, 'folder clear blocked'); END
            """.trimIndent()
        )

        val failure = assertFails { folders.deleteFolder(folder.id) }

        assertContains(failure.toString(), "folder clear blocked")
        assertNotNull(folders.getFolderById(folder.id))
        assertEquals(original, conversations.getConversationById(original.id))
    }

    @Test
    fun `failure deleting the folder preserves the original earlier unfiling write`() = runTest {
        val folder = folders.createFolder(Uuid.random(), "删除失败")
        val original = conversation(folder.assistantId, folder.id)
        conversations.insertConversation(original)
        executeSql(
            """
            CREATE TRIGGER block_folder_delete BEFORE DELETE ON conversation_folder
            WHEN OLD.id = '${folder.id}'
            BEGIN SELECT RAISE(ABORT, 'folder delete blocked'); END
            """.trimIndent()
        )

        val failure = assertFails { folders.deleteFolder(folder.id) }

        assertContains(failure.toString(), "folder delete blocked")
        assertNotNull(folders.getFolderById(folder.id))
        assertEquals(original.copy(folderId = null), conversations.getConversationById(original.id))
    }

    private suspend fun executeSql(sql: String) {
        database.useWriterConnection { connection ->
            connection.usePrepared(sql) { it.step() }
        }
    }

    private fun conversation(assistantId: Uuid, folderId: Uuid?) = Conversation(
        id = Uuid.random(),
        assistantId = assistantId,
        title = "文件夹测试会话",
        messageNodes = listOf(
            MessageNode(messages = listOf(UIMessage.assistant("原分支"), UIMessage.assistant("选中分支")), selectIndex = 1),
        ),
        chatSuggestions = listOf("保留建议"),
        isPinned = true,
        createAt = Instant.fromEpochMilliseconds(1_600_000_000_123),
        updateAt = Instant.fromEpochMilliseconds(1_700_000_000_456),
        customSystemPrompt = "保留提示词",
        modeInjectionIds = setOf(Uuid.random()),
        lorebookIds = setOf(Uuid.random()),
        workspaceCwd = "/workspace/keep",
        folderId = folderId,
    )
}
