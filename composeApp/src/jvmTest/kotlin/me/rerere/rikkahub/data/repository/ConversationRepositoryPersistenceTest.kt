package me.rerere.rikkahub.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConversationRepositoryPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository

    @BeforeTest
    fun setUp() {
        database = buildAppDatabase(
            Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            BundledSQLiteDriver(),
            MessageFtsDialect.UNICODE61,
        )
        repository = ConversationRepository(
            database.conversationDao(),
            database.messageNodeDao(),
            database.favoriteDao(),
            database,
            ConversationFileStore {},
            MessageFtsManager(database, MessageFtsDialect.UNICODE61),
        )
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `insert and update preserve persisted metadata and message branches`() = runTest {
        val original = conversation().copy(newConversation = true)
        repository.insertConversation(original)

        assertEquals(original.copy(newConversation = false), repository.getConversationById(original.id))
        assertEquals("[]", database.conversationDao().getConversationById(original.id.toString())!!.nodes)

        val updated = original.copy(
            title = "更新后的标题",
            updateAt = Instant.fromEpochMilliseconds(1_800_000_000_789),
            chatSuggestions = listOf("下一步？"),
            isPinned = false,
            customSystemPrompt = "更新后的提示词",
            modeInjectionIds = setOf(Uuid.random()),
            lorebookIds = setOf(Uuid.random()),
            workspaceCwd = "/workspace/updated",
            folderId = Uuid.random(),
            messageNodes = original.messageNodes.map { it.copy(selectIndex = 0) },
        )
        repository.updateConversation(updated)

        assertEquals(updated.copy(newConversation = false), repository.getConversationById(original.id))
    }

    @Test
    fun `legacy rows decode without losing branches while empty nodes are omitted`() = runTest {
        val id = Uuid.random()
        val assistantId = Uuid.random()
        val modeId = Uuid.random()
        val lorebookId = Uuid.random()
        val folderId = Uuid.random()
        val node = MessageNode(
            messages = listOf(UIMessage.assistant("旧版本"), UIMessage.assistant("选中的版本")),
            selectIndex = 1,
        )
        database.conversationDao().insert(
            ConversationEntity(
                id = id.toString(),
                assistantId = assistantId.toString(),
                title = "旧数据",
                nodes = "[]",
                createAt = -1L,
                updateAt = 0L,
                chatSuggestions = "[\"继续\",\"第二行\\n内容\"]",
                isPinned = true,
                customSystemPrompt = "原提示词",
                modeInjectionIds = "[\"$modeId\"]",
                lorebookIds = "[\"$lorebookId\"]",
                workspaceCwd = "/workspace/旧目录",
                folderId = folderId.toString(),
            )
        )
        database.messageNodeDao().insertAll(
            listOf(
                MessageNodeEntity(Uuid.random().toString(), id.toString(), 0, "[]", 0),
                MessageNodeEntity(node.id.toString(), id.toString(), 1, JsonInstant.encodeToString(node.messages), 1),
            )
        )

        assertEquals(
            Conversation(
                id = id,
                assistantId = assistantId,
                title = "旧数据",
                messageNodes = listOf(node),
                chatSuggestions = listOf("继续", "第二行\n内容"),
                isPinned = true,
                createAt = Instant.fromEpochMilliseconds(-1L),
                updateAt = Instant.fromEpochMilliseconds(0L),
                customSystemPrompt = "原提示词",
                modeInjectionIds = setOf(modeId),
                lorebookIds = setOf(lorebookId),
                workspaceCwd = "/workspace/旧目录",
                folderId = folderId,
            ),
            repository.getConversationById(id),
        )
    }

    @Test
    fun `null and empty optional values retain the legacy storage representation`() = runTest {
        for (optionalValue in listOf(null, "")) {
            val original = conversation().copy(
                customSystemPrompt = optionalValue,
                workspaceCwd = optionalValue,
                folderId = null,
                chatSuggestions = emptyList(),
                modeInjectionIds = emptySet(),
                lorebookIds = emptySet(),
            )
            repository.insertConversation(original)
            val row = assertNotNull(database.conversationDao().getConversationById(original.id.toString()))
            assertEquals("", row.customSystemPrompt)
            assertEquals("", row.workspaceCwd)
            assertEquals("", row.folderId)
            assertEquals("[]", row.chatSuggestions)
            assertEquals("[]", row.modeInjectionIds)
            assertEquals("[]", row.lorebookIds)
            assertEquals(
                original.copy(customSystemPrompt = null, workspaceCwd = null),
                repository.getConversationById(original.id),
            )
        }
    }

    @Test
    fun `timestamps keep millisecond precision including dates before the epoch`() = runTest {
        val original = conversation().copy(
            createAt = Instant.parse("1969-12-31T23:59:59.999999999Z"),
            updateAt = Instant.parse("2026-09-09T00:00:00.123456789Z"),
        )
        repository.insertConversation(original)
        val row = assertNotNull(database.conversationDao().getConversationById(original.id.toString()))
        assertEquals(-1L, row.createAt)
        assertEquals(1_788_912_000_123L, row.updateAt)
        assertEquals(
            original.copy(
                createAt = Instant.parse("1969-12-31T23:59:59.999Z"),
                updateAt = Instant.parse("2026-09-09T00:00:00.123Z"),
            ),
            repository.getConversationById(original.id),
        )
    }

    @Test
    fun `base64 insert and update rejection leave the database unchanged`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        val invalid = original.copy(
            title = "不能保存",
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(
                        UIMessage.user("").copy(parts = listOf(UIMessagePart.Image("data:image/png;base64,AA=="))),
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> { repository.updateConversation(invalid) }
        assertEquals(original, repository.getConversationById(original.id))
        val newId = Uuid.random()
        assertFailsWith<IllegalArgumentException> { repository.insertConversation(invalid.copy(id = newId)) }
        assertNull(repository.getConversationById(newId))
        assertEquals(1, repository.countConversations())
    }

    @Test
    fun `malformed stored metadata still propagates its decoding error`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        val row = assertNotNull(database.conversationDao().getConversationById(original.id.toString()))
        database.conversationDao().update(row.copy(chatSuggestions = "not json"))

        assertFailsWith<SerializationException> { repository.getConversationById(original.id) }
    }

    @Test
    fun `paged summaries preserve list fields and omit full conversation content`() = runTest {
        val pinned = conversation()
        val unfiled = conversation().copy(assistantId = pinned.assistantId, isPinned = false, folderId = null)
        repository.insertConversation(pinned)
        repository.insertConversation(unfiled)
        repository.insertConversation(conversation())
        val pinnedSummary = Conversation(
            id = pinned.id,
            assistantId = pinned.assistantId,
            title = pinned.title,
            messageNodes = emptyList(),
            isPinned = true,
            createAt = pinned.createAt,
            updateAt = pinned.updateAt,
            folderId = pinned.folderId,
        )
        val unfiledSummary = pinnedSummary.copy(id = unfiled.id, isPinned = false, folderId = null)

        val firstPage = repository.getConversationsOfAssistantPage(pinned.assistantId, offset = 0, limit = 1)
        assertEquals(listOf(pinnedSummary), firstPage.items)
        val secondPage = repository.getConversationsOfAssistantPage(
            pinned.assistantId, offset = assertNotNull(firstPage.nextOffset), limit = 1,
        )
        assertEquals(listOf(unfiledSummary), secondPage.items)
        assertNull(secondPage.nextOffset)
        assertEquals(
            listOf(pinnedSummary),
            repository.getConversationsOfFolderPage(assertNotNull(pinned.folderId), offset = 0, limit = 10).items,
        )
        assertEquals(
            listOf(unfiledSummary),
            repository.getUnfiledConversationsOfAssistantPage(pinned.assistantId, offset = 0, limit = 10).items,
        )
        assertEquals(
            listOf(pinnedSummary, unfiledSummary),
            repository.searchConversationsOfAssistantPage(pinned.assistantId, "会话", offset = 0, limit = 10).items,
        )
    }

    private fun conversation() = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        title = "会话标题：\"引用\"\n第二行",
        messageNodes = listOf(
            MessageNode(
                messages = listOf(UIMessage.assistant("旧分支"), UIMessage.assistant("当前分支")),
                selectIndex = 1,
            ),
        ),
        chatSuggestions = listOf("继续？", "包含\"引号\"和\n换行"),
        isPinned = true,
        createAt = Instant.fromEpochMilliseconds(1_600_000_000_123),
        updateAt = Instant.fromEpochMilliseconds(1_700_000_000_456),
        customSystemPrompt = "系统提示词\n第二行",
        modeInjectionIds = setOf(Uuid.random(), Uuid.random()),
        lorebookIds = setOf(Uuid.random(), Uuid.random()),
        workspaceCwd = "/workspace/带空格的 目录",
        folderId = Uuid.random(),
    )
}
