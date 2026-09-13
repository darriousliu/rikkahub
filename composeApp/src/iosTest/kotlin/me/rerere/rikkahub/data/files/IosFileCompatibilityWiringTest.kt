package me.rerere.rikkahub.data.files

import me.rerere.rikkahub.data.files.testFilesManager
import androidx.datastore.preferences.core.edit
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.createIosSettingsDataStore
import me.rerere.rikkahub.data.datastore.createSettingsDataStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.createIosAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class IosFileCompatibilityWiringTest {
    private val root = Path(SystemTemporaryDirectory, "cmp-14b-wiring-${Uuid.random()}")
        .canonicalFile.apply { mkdirs() }
    private val file = FileKit.filesDir.toKotlinxIoPath().resolve("upload/${Uuid.random()}.png")
        .also { it.parent!!.mkdirs(); it.writeBytes(byteArrayOf(2, 4, 6)) }
    private val currentUri = "file://${file.toString().encodeURLPath()}"
    private val oldUri = "file://${("file:///private/var/mobile/Containers/Data/Application/" +
        "${Uuid.random()}/Documents/upload/${file.name}").encodeURLPath()}"

    @AfterTest
    fun cleanup() {
        file.deleteRecursively()
        root.deleteRecursively()
    }

    @Test
    fun actualIosDatabaseOpenRepairsPersistedSourceAndForkWithoutChangingIdsOrSelection() = runTest {
        val node = MessageNode(messages = listOf(UIMessage.user("same text").copy(
            parts = listOf(UIMessagePart.Image(oldUri)))), selectIndex = 0)
        val source = Conversation.ofId(Uuid.random(), messages = listOf(node)).copy(title = "old source")
        val fork = source.copy(id = Uuid.random(), title = "old fork", messageNodes = listOf(node.copy(id = Uuid.random())))
        createIosAppDatabase(root.resolve("database").toString()).let { db ->
            try {
                val repository = repository(db)
                repository.insertConversation(source)
                repository.insertConversation(fork)
            } finally { db.close() }
        }
        createIosAppDatabase(root.resolve("database").toString()).let { db ->
            try {
                val repository = repository(db)
                listOf(source, fork).forEach { before ->
                    val after = assertNotNull(repository.getConversationById(before.id))
                    assertEquals(before.title, after.title)
                    assertEquals(before.messageNodes.map { it.id }, after.messageNodes.map { it.id })
                    assertEquals(before.currentMessages.map { it.id }, after.currentMessages.map { it.id })
                    assertEquals(0, after.messageNodes.single().selectIndex)
                    assertEquals(listOf(currentUri), after.files)
                }
            } finally { db.close() }
        }
    }

    @Test
    fun actualIosSettingsFactoryRepairsOldAssetsAndPreservesOtherPreferencesOnDisk() = runTest {
        val path = root.resolve("datastore/settings.preferences_pb")
        path.parent!!.mkdirs()
        val assistant = Assistant(name = "legacy", avatar = Avatar.Image(oldUri), background = oldUri)
        val firstJob = SupervisorJob()
        val first = createSettingsDataStore(CoroutineScope(firstJob + Dispatchers.Unconfined)) { path.toString() }
        first.edit {
            it[SettingsStore.ASSISTANTS] = JsonInstant.encodeToString(listOf(assistant))
            it[SettingsStore.TITLE_PROMPT] = "unchanged prompt"
        }
        firstJob.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val reopened = createIosSettingsDataStore(CoroutineScope(secondJob + Dispatchers.Unconfined), path.parent.toString())
            val preferences = reopened.data.first()
            val restored = JsonInstant.decodeFromString<List<Assistant>>(preferences[SettingsStore.ASSISTANTS]!!).single()
            assertEquals(assistant.copy(avatar = Avatar.Image(currentUri), background = currentUri), restored)
            assertEquals("unchanged prompt", preferences[SettingsStore.TITLE_PROMPT])
            assertEquals(3, preferences[SettingsStore.VERSION])
        } finally { secondJob.cancelAndJoin() }
    }

    private fun repository(database: AppDatabase) = ConversationRepository(
        database.conversationDao(), database.messageNodeDao(), database.favoriteDao(), database,
        testFilesManager(), MessageFtsManager(database, MessageFtsDialect.UNICODE61),
    )
}
