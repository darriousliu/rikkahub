package me.rerere.rikkahub.ui.pages.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.sync.S3Sync
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UiState
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class BackupVMContractTest {
    private val dispatcher = StandardTestDispatcher()
    private val fixtures = mutableListOf<Fixture>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        fixtures.forEach(Fixture::close)
        fixtures.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial lists load once and preserve stable descending sorting through real clients`() = runTest(dispatcher) {
        val f = fixture()
        val gate = CompletableDeferred<Unit>()
        val reached = CompletableDeferred<Unit>()
        f.beforeRequest = { request ->
            if (f.requests.size >= 2) reached.complete(Unit)
            gate.await()
        }
        val vm = f.vm()
        assertSame(UiState.Idle, vm.webDavBackupItems.value)
        runCurrent()
        reached.await()
        assertSame(UiState.Loading, vm.webDavBackupItems.value)
        assertSame(UiState.Loading, vm.s3BackupItems.value)
        gate.complete(Unit)
        f.awaitLists(vm)
        assertEquals(listOf("backup_new.zip", "backup_tied.zip", "backup_old.zip"),
            assertIs<UiState.Success<List<WebDavBackupItem>>>(vm.webDavBackupItems.value).data.map { it.displayName })
        assertEquals(listOf("backup_new.zip", "backup_old.zip"),
            assertIs<UiState.Success<List<S3BackupItem>>>(vm.s3BackupItems.value).data.map { it.displayName })
        assertEquals(2, f.requests.count { it.method.value == "PROPFIND" })
        assertEquals(1, f.requests.count { it.url.parameters["list-type"] == "2" })
    }

    @Test
    fun `list failure and cancellation are exposed and reload can succeed`() = runTest(dispatcher) {
        val f = fixture()
        f.beforeRequest = { request ->
            if (request.url.host.startsWith("test-bucket")) throw CancellationException("list cancelled")
            throw IllegalStateException("list failed")
        }
        val vm = f.vm()
        f.awaitLists(vm)
        assertEquals("list failed", assertIs<UiState.Error>(vm.webDavBackupItems.value).error.message)
        assertIs<CancellationException>(assertIs<UiState.Error>(vm.s3BackupItems.value).error)
        f.beforeRequest = {}
        vm.loadBackupFileItems()
        vm.loadS3BackupFileItems()
        runCurrent()
        f.awaitLists(vm)
        assertIs<UiState.Success<*>>(vm.webDavBackupItems.value)
        assertIs<UiState.Success<*>>(vm.s3BackupItems.value)
        assertEquals(0, f.preferences.writes)
    }

    @Test
    fun `settings update remains launched and persists existing keys readable by a new store`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        val old = vm.settings.value
        val changed = old.copy(webDavConfig = webConfig.copy(path = "changed"), s3Config = s3Config.copy(region = "other"))
        vm.updateSettings(changed)
        assertEquals(old, vm.settings.value)
        assertEquals(0, f.preferences.writes)
        runCurrent()
        assertEquals(changed.webDavConfig, vm.settings.value.webDavConfig)
        assertEquals(changed.s3Config, vm.settings.value.s3Config)
        val reopened = SettingsStore(f.preferences, f.scope)
        assertEquals(changed.webDavConfig, reopened.settingsFlow.value.webDavConfig)
        assertEquals(changed.s3Config, reopened.settingsFlow.value.s3Config)
        assertEquals("webdav_config", SettingsStore.WEBDAV_CONFIG.name)
        assertEquals("s3_config", SettingsStore.S3_CONFIG.name)
    }

    @Test
    fun `network entry points use current config and original object selectors`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        f.awaitLists(vm)
        f.requests.clear()
        f.store.update { it.copy(webDavConfig = webConfig.copy(path = "new-path"), s3Config = s3Config.copy(bucket = "new-bucket")) }
        val web = webItem("backup_chosen.zip", 5)
        val s3 = s3Item("backup_chosen.zip", 5)
        assertEquals(0, vm.testWebDav())
        assertEquals(0, vm.restore(web))
        assertEquals(0, vm.deleteWebDavBackupFile(web))
        assertEquals(0, vm.testS3())
        assertEquals(0, vm.restoreFromS3(s3))
        assertEquals(0, vm.deleteS3BackupFile(s3))
        assertEquals(listOf("PROPFIND", "GET", "DELETE", "GET", "GET", "DELETE"), f.requests.map { it.method.value })
        assertTrue(f.requests.take(3).all { "/dav/new-path" in it.url.encodedPath })
        assertTrue(f.requests.takeLast(3).all { it.url.host == "new-bucket.unit.invalid" })
        assertEquals("/nested/backup_chosen.zip", f.requests.last().url.encodedPath)
        assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
    }

    @Test
    fun `both successful and unsuccessful temp deletion record backup completion`() = runTest(dispatcher) {
        for (deleteBeforeSync in listOf(false, true)) {
            val f = fixture()
            val vm = f.vm()
            f.awaitLists(vm)
            f.deleteUploadedArchive = deleteBeforeSync
            vm.backup()
            assertEquals(f.now, vm.settings.value.backupReminderConfig.lastBackupTime)
            f.now += 123
            vm.backupToS3()
            assertEquals(f.now, vm.settings.value.backupReminderConfig.lastBackupTime)
            assertEquals(2, f.preferences.writes)
            assertEquals("backup_reminder_config", SettingsStore.BACKUP_REMINDER_CONFIG.name)
        }
    }

    @Test
    fun `backup timestamp is taken after upload and uses latest unrelated settings`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        f.awaitLists(vm)
        val reached = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        f.beforeRequest = { if (it.method.value == "PUT") { reached.complete(Unit); gate.await() } }
        val pending = async { vm.backup() }
        reached.await()
        assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
        f.now += 999
        f.store.update { it.copy(launchCount = 61, webDavConfig = webConfig.copy(path = "changed")) }
        gate.complete(Unit)
        pending.await()
        assertEquals(61, vm.settings.value.launchCount)
        assertEquals("changed", vm.settings.value.webDavConfig.path)
        assertEquals(f.now, vm.settings.value.backupReminderConfig.lastBackupTime)
        assertFalse("changed" in f.requests.last().url.encodedPath)
    }

    @Test
    fun `upload failures and cancellation propagate without time writes or upload retries`() = runTest(dispatcher) {
        for (error in listOf(IllegalStateException("failed"), CancellationException("cancelled"))) {
            val f = fixture()
            val vm = f.vm()
            f.awaitLists(vm)
            f.beforeRequest = { if (it.method.value == "PUT") throw error }
            assertEquals(error.message, runCatching { vm.backup() }.exceptionOrNull()?.message)
            assertEquals(error.message, runCatching { vm.backupToS3() }.exceptionOrNull()?.message)
            assertEquals(0, f.preferences.writes)
            assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
            assertEquals(2, f.requests.count { it.method.value == "PUT" })
            assertTrue(f.cache.listFiles()!!.any { it.extension == "zip" })
        }
    }

    @Test
    fun `test restore and delete propagate original client failures`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        f.awaitLists(vm)
        f.beforeRequest = { throw IllegalArgumentException("transport") }
        val operations: List<suspend () -> Any> = listOf(
            { vm.testWebDav() }, { vm.restore(webItem("x", 0)) }, { vm.deleteWebDavBackupFile(webItem("x", 0)) },
            { vm.testS3() }, { vm.restoreFromS3(s3Item("x", 0)) }, { vm.deleteS3BackupFile(s3Item("x", 0)) },
        )
        operations.forEach { assertEquals("transport", runCatching { it() }.exceptionOrNull()?.message) }
        assertEquals(0, f.preferences.writes)
        assertFalse(File(f.cache, "x").exists())
    }

    @Test
    fun `concurrent backup calls reach the network independently`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        f.awaitLists(vm)
        val reached = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val puts = java.util.concurrent.atomic.AtomicInteger()
        f.afterUpload = { if (puts.incrementAndGet() == 2) reached.complete(Unit); gate.await() }
        val first = async { vm.backup() }
        val second = async { vm.backup() }
        reached.await()
        assertEquals(0, f.preferences.writes)
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(2, f.preferences.writes)
    }

    @Test
    fun `cleared VM does not start initial list requests`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        f.viewModels.clear()
        runCurrent()
        assertTrue(f.requests.isEmpty())
        assertSame(UiState.Idle, vm.webDavBackupItems.value)
        assertSame(UiState.Idle, vm.s3BackupItems.value)
    }

    @Test
    fun `time persistence failure propagates after upload and preserves old store memory behavior`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        f.awaitLists(vm)
        val persistedBefore = f.preferences.data.value[SettingsStore.BACKUP_REMINDER_CONFIG]
        val error = IllegalStateException("disk failure")
        f.preferences.failure = error
        assertSame(error, runCatching { vm.backup() }.exceptionOrNull())
        assertEquals(1, f.requests.count { it.method.value == "PUT" })
        assertEquals(f.now, vm.settings.value.backupReminderConfig.lastBackupTime)
        assertEquals(persistedBefore, f.preferences.data.value[SettingsStore.BACKUP_REMINDER_CONFIG])
    }

    @Test
    fun `real FileKit export archives settings before recording completion and restores stored settings`() =
        runTest(dispatcher) {
            val f = fixture()
            f.store.update { it.copy(webDavConfig = webConfig, s3Config = s3Config) }
            val local = f.localVm()
            val archive = local.exportToFile()
            val zip = f.cache.listFiles()!!.single { it.extension == "zip" }
            val archived = ZipFile(zip).use { file ->
                JsonInstant.decodeFromString<Settings>(file.getInputStream(file.getEntry("settings.json"))
                    .bufferedReader().readText())
            }
            assertEquals(webConfig, archived.webDavConfig)
            assertEquals(s3Config, archived.s3Config)
            assertEquals(0, archived.backupReminderConfig.lastBackupTime)
            assertEquals(f.now, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
            f.store.update { it.copy(webDavConfig = WebDavConfig(), s3Config = S3Config()) }
            local.restoreFromLocalFile(archive)
            assertEquals(webConfig, f.store.settingsFlow.value.webDavConfig)
            assertEquals(s3Config, f.store.settingsFlow.value.s3Config)
            assertEquals(0, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
        }

    @Test
    fun `archive creation failure does not record a backup time`() = runTest(dispatcher) {
        val f = fixture()
        f.cache.delete()
        f.cache.writeText("not a directory")
        assertTrue(runCatching { f.localVm().exportToFile() }.isFailure)
        assertEquals(0, f.preferences.writes)
        assertEquals(0, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
    }

    @Test
    fun `FileKit export preserves completed archive when timestamp persistence fails`() = runTest(dispatcher) {
        val f = fixture()
        val error = IllegalStateException("disk failure")
        f.preferences.failure = error
        assertSame(error, runCatching { f.localVm().exportToFile() }.exceptionOrNull())
        assertTrue(f.cache.listFiles()!!.any { it.extension == "zip" })
        assertEquals(f.now, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
    }

    @Test
    fun `local VM forces every backup item and preserves the source archive`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        f.awaitLists(vm)
        f.store.update { it.copy(webDavConfig = webConfig.copy(items = emptyList())) }
        File(f.root, "files/upload/forced.txt").apply { parentFile.mkdirs(); writeText("forced") }
        File(f.root, "database/rikka_hub").apply { parentFile.mkdirs(); writeText("database bytes") }
        val exported = vm.exportToFile()
        val zip = f.cache.listFiles()!!.single { it.extension == "zip" }
        ZipFile(zip).use {
            assertTrue(it.getEntry("upload/forced.txt") != null)
            assertTrue(it.getEntry("rikka_hub.db") != null)
        }
        assertEquals(f.now, vm.settings.value.backupReminderConfig.lastBackupTime)
        File(f.root, "files/upload/forced.txt").delete()
        File(f.root, "database/rikka_hub").delete()
        vm.restoreFromLocalFile(exported)
        assertEquals("forced", File(f.root, "files/upload/forced.txt").readText())
        assertEquals("database bytes", File(f.root, "database/rikka_hub").readText())
        assertTrue(zip.exists())
        assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
    }

    @Test
    fun `Chatbox import preserves original mapping counters timestamps and selected assistant settings`() =
        runTest(dispatcher) {
            val f = fixture()
            f.configureImportSettings()
            val vm = f.vm()
            val result = vm.restoreFromChatBox(f.chatboxFile())
            assertEquals(ChatboxRestoreResult(1, 1, 0, 2, 1), result)
            val conversation = requireNotNull(f.conversations.getConversationById(chatboxConversationId))
            assertEquals("CMP15 fixed conversation", conversation.title)
            assertEquals(assistantA.id, conversation.assistantId)
            assertEquals("CMP15 system", conversation.customSystemPrompt)
            assertEquals(1_700_000_000_000, conversation.createAt.toEpochMilliseconds())
            assertEquals(1_700_000_004_000, conversation.updateAt.toEpochMilliseconds())
            assertEquals(2, conversation.messageNodes.size)
            val user = conversation.messageNodes[0].currentMessage
            val answer = conversation.messageNodes[1].currentMessage
            assertEquals(MessageRole.USER, user.role)
            assertEquals(listOf(UIMessagePart.Text("CMP15 question 中文")), user.parts)
            assertEquals(MessageRole.ASSISTANT, answer.role)
            assertEquals("CMP15 answer", assertIs<UIMessagePart.Text>(answer.parts.last()).text)
            assertEquals(7, answer.usage?.promptTokens)
            assertEquals(11, answer.usage?.completionTokens)
            val imported = vm.settings.value.providers.filterIsInstance<ProviderSetting.OpenAI>()
                .single { it.baseUrl == "https://cmp15.invalid/v1" }
            assertEquals("https://cmp15.invalid/v1", imported.baseUrl)
            assertEquals(imported.models.single().id, answer.modelId)
            assertEquals(existingProvider, vm.settings.value.providers.single { it.id == existingProvider.id })
            assertTrue(vm.settings.value.assistants.single { it.id == assistantA.id }.allowConversationSystemPrompt)
            assertEquals(assistantB, vm.settings.value.assistants.single { it.id == assistantB.id })
            assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
        }

    @Test
    fun `reimporting Chatbox skips existing conversation without merging or changing its messages`() =
        runTest(dispatcher) {
            val f = fixture()
            f.configureImportSettings()
            val vm = f.vm()
            val file = f.chatboxFile()
            val providerCount = vm.settings.value.providers.size
            vm.restoreFromChatBox(file)
            val first = requireNotNull(f.conversations.getConversationById(chatboxConversationId))
            f.conversations.updateConversation(first.copy(title = "keep original saved title"))
            val result = vm.restoreFromChatBox(file)
            assertEquals(ChatboxRestoreResult(1, 0, 1, 2, 1), result)
            val second = requireNotNull(f.conversations.getConversationById(chatboxConversationId))
            assertEquals("keep original saved title", second.title)
            assertEquals(first.messageNodes, second.messageNodes)
            // 原导入仅对会话去重，提供商继续追加。
            assertEquals(providerCount + 2, vm.settings.value.providers.size)
        }

    @Test
    fun `Chatbox keeps settings changed while the original repository insertion is waiting`() = runTest(dispatcher) {
        val f = fixture()
        f.configureImportSettings()
        val vm = f.vm()
        val reached = CountDownLatch(1)
        val resume = CountDownLatch(1)
        f.beforeStatement = { sql ->
            if (sql.contains("SELECT EXISTS", ignoreCase = true) &&
                sql.contains("conversationentity", ignoreCase = true)) {
                f.beforeStatement = null
                reached.countDown()
                check(resume.await(10, TimeUnit.SECONDS))
            }
        }
        val pending = async { vm.restoreFromChatBox(f.chatboxFile()) }
        runCurrent()
        try {
            withContext(Dispatchers.IO) { assertTrue(reached.await(10, TimeUnit.SECONDS)) }
            f.store.update { it.copy(assistantId = assistantB.id, launchCount = 77) }
        } finally {
            resume.countDown()
        }
        pending.await()
        assertEquals(assistantA.id, f.conversations.getConversationById(chatboxConversationId)?.assistantId)
        assertEquals(assistantB.id, vm.settings.value.assistantId)
        assertEquals(77, vm.settings.value.launchCount)
        assertFalse(vm.settings.value.assistants.single { it.id == assistantA.id }.allowConversationSystemPrompt)
        assertTrue(vm.settings.value.assistants.single { it.id == assistantB.id }.allowConversationSystemPrompt)
    }

    @Test
    fun `invalid Chatbox input leaves conversations and settings unchanged`() = runTest(dispatcher) {
        val f = fixture()
        f.configureImportSettings()
        val vm = f.vm()
        val before = vm.settings.value
        val writes = f.preferences.writes
        val file = File(f.root, "bad.json").apply { writeText("{broken") }
        assertTrue(runCatching { vm.restoreFromChatBox(PlatformFile(file)) }.isFailure)
        assertFalse(f.conversations.existsConversationById(chatboxConversationId))
        assertEquals(before, vm.settings.value)
        assertEquals(writes, f.preferences.writes)
    }

    @Test
    fun `Cherry import uses original launched settings update and deduplicates only inside the input`() =
        runTest(dispatcher) {
            val f = fixture()
            f.configureImportSettings()
            val vm = f.vm()
            val before = vm.settings.value
            vm.restoreFromCherryStudio(f.cherryFile())
            assertEquals(before, vm.settings.value)
            runCurrent()
            val providers = vm.settings.value.providers
            assertEquals(before.providers.size + 1, providers.size)
            val imported = assertIs<ProviderSetting.OpenAI>(providers.single { it.name == "CMP15 Cherry" })
            assertEquals("CMP15 Cherry", imported.name)
            assertEquals("https://cmp15.invalid/v1", imported.baseUrl)
            assertEquals("cmp15-model", imported.models.single().modelId)
            assertTrue(imported.useResponseApi)
            assertFalse(imported.enabled)
            assertEquals(existingProvider, providers.single { it.id == existingProvider.id })
            assertEquals(before.assistants, vm.settings.value.assistants)
            assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
        }

    @Test
    fun `empty or malformed Cherry backup preserves original errors without settings writes`() = runTest(dispatcher) {
        val f = fixture()
        f.configureImportSettings()
        val vm = f.vm()
        val before = vm.settings.value
        val writes = f.preferences.writes
        val empty = f.cherryFile(empty = true)
        val failure = assertFailsWith<IllegalArgumentException> { vm.restoreFromCherryStudio(empty) }
        assertEquals("No importable providers found in Cherry Studio backup", failure.message)
        val missing = File(f.root, "missing-data.zip")
        ZipOutputStream(missing.outputStream()).use { it.putNextEntry(ZipEntry("unrelated.txt")); it.closeEntry() }
        val invalid = assertFailsWith<IllegalArgumentException> {
            vm.restoreFromCherryStudio(PlatformFile(missing))
        }
        assertEquals("Invalid Cherry Studio backup: data.json not found", invalid.message)
        runCurrent()
        assertEquals(before, vm.settings.value)
        assertEquals(writes, f.preferences.writes)
    }

    @Test
    fun `real local restore writes archive files and export includes them without changing caller source`() =
        runTest(dispatcher) {
            val f = fixture()
            val source = File(f.root, "native.zip")
            val payload = "CMP15 native bytes 中文".encodeToByteArray()
            ZipOutputStream(source.outputStream()).use {
                it.putNextEntry(ZipEntry("upload/cmp15-native.txt"))
                it.write(payload)
                it.closeEntry()
            }
            val bytes = source.readBytes()
            val vm = f.localVm()
            vm.restoreFromLocalFile(PlatformFile(source))
            assertTrue(payload.contentEquals(File(f.root, "files/upload/cmp15-native.txt").readBytes()))
            assertTrue(bytes.contentEquals(source.readBytes()))
            val exported = vm.exportToFile()
            val zip = f.cache.listFiles()!!.single { it.extension == "zip" }
            ZipFile(zip).use {
                assertTrue(payload.contentEquals(it.getInputStream(it.getEntry("upload/cmp15-native.txt")).readBytes()))
                assertTrue(it.getEntry("settings.json") != null)
            }
            vm.restoreFromLocalFile(exported)
            assertTrue(payload.contentEquals(File(f.root, "files/upload/cmp15-native.txt").readBytes()))
        }

    private fun fixture() = Fixture().also { fixtures += it }

    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = MemoryPreferences()
        val store = SettingsStore(preferences, scope)
        val viewModels = ViewModelStore()
        var now = 1_789_000_000_123L
        val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(now) }
        val requests = java.util.Collections.synchronizedList(mutableListOf<HttpRequestData>())
        var beforeRequest: suspend (HttpRequestData) -> Unit = {}
        var afterUpload: suspend () -> Unit = {}
        var deleteUploadedArchive = false
        val root: File = Files.createTempDirectory("backup-vm-contract-").toFile()
        val cache = File(root, "cache")
        private var database: AppDatabase? = null
        var beforeStatement: ((String) -> Unit)? = null

        suspend fun configureImportSettings() {
            store.update { it.copy(assistantId = assistantA.id, assistants = listOf(assistantA, assistantB),
                providers = listOf(existingProvider)) }
        }

        fun chatboxFile(): PlatformFile = PlatformFile(File(root, "chatbox.json").apply {
            writeText(requireNotNull(BackupVMContractTest::class.java.getResourceAsStream("/backup/cmp15-chatbox.json"))
                .bufferedReader().use { it.readText() })
        })

        fun cherryFile(empty: Boolean = false): PlatformFile = PlatformFile(File(root, "cherry.zip").apply {
            val providers = if (empty) "[]" else """[
                {"name":"CMP15 Cherry","type":"openai-response","apiHost":"https://cmp15.invalid/v1/",
                 "apiKey":"cmp15-offline-only","enabled":false,"models":[{"id":"cmp15-model"}]},
                {"name":"duplicate","type":"openai-response","apiHost":"https://cmp15.invalid/v1/",
                 "apiKey":"cmp15-offline-only","models":[{"id":"cmp15-model"}]}
            ]"""
            val llm = "{\"providers\":$providers}"
            val persisted = "{\"llm\":${JsonInstant.encodeToString(llm)}}"
            val data = "{\"localStorage\":{\"persist:cherry-studio\":${JsonInstant.encodeToString(persisted)}}}"
            ZipOutputStream(outputStream()).use {
                it.putNextEntry(ZipEntry("data.json"))
                it.write(data.encodeToByteArray())
                it.closeEntry()
            }
        })

        private val httpClient = HttpClient(MockEngine { request ->
            requests += request
            beforeRequest(request)
            when {
                request.method.value == "PROPFIND" -> respond(webListing(), HttpStatusCode.MultiStatus)
                request.method.value == "MKCOL" -> respond("", HttpStatusCode.Created)
                request.url.parameters["list-type"] == "2" -> respond(s3Listing())
                request.method.value == "PUT" -> {
                    request.body.toByteArray()
                    afterUpload()
                    if (deleteUploadedArchive) cache.listFiles()?.filter { it.extension == "zip" }?.forEach { it.delete() }
                    respond("", HttpStatusCode.Created)
                }
                request.method.value == "GET" -> {
                    val bytes = java.io.ByteArrayOutputStream().apply {
                        ZipOutputStream(this).use { zip ->
                            zip.putNextEntry(ZipEntry("ignored.txt")); zip.write(byteArrayOf(1)); zip.closeEntry()
                        }
                    }.toByteArray()
                    respond(bytes)
                }
                else -> respond("")
            }
        })

        private val layout = BackupFileLayout(
            PlatformFile(File(root, "files").apply { mkdirs() }),
            PlatformFile(cache.apply { mkdirs() }),
            mapOf("rikka_hub.db" to PlatformFile(File(root, "database/rikka_hub"))),
        )
        private val web = WebDavSync(store, JsonInstant, layout, httpClient)
        private val s3 = S3Sync(store, JsonInstant, layout, httpClient)

        init {
            runBlocking { store.update { it.copy(webDavConfig = webConfig, s3Config = s3Config) } }
            preferences.writes = 0
        }

        fun vm() = BackupVM(store, web, s3, conversations, clock).also { viewModels.put("backup", it) }

        suspend fun awaitLists(vm: BackupVM) {
            vm.webDavBackupItems.first { it is UiState.Success || it is UiState.Error }
            vm.s3BackupItems.first { it is UiState.Success || it is UiState.Error }
        }

        private fun webListing(): String = "<D:multistatus xmlns:D=\"DAV:\">" +
            "<D:response><D:href>/dav/</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat></D:response>" +
            listOf("old" to 10, "new" to 20, "tied" to 20).joinToString("") { (name, seconds) ->
                "<D:response><D:href>/dav/backup_$name.zip</D:href><D:propstat><D:prop>" +
                    "<D:displayname>backup_$name.zip</D:displayname><D:getcontentlength>1024</D:getcontentlength>" +
                    "<D:getlastmodified>Thu, 10 Sep 2026 00:00:$seconds GMT</D:getlastmodified>" +
                    "</D:prop></D:propstat></D:response>"
            } + "</D:multistatus>"

        private fun s3Listing(): String = "<ListBucketResult>" +
            listOf("old" to 10, "new" to 20).joinToString("") { (name, seconds) ->
                "<Contents><Key>rikkahub_backups/backup_$name.zip</Key><Size>2048</Size>" +
                    "<LastModified>2026-09-10T00:00:${seconds}Z</LastModified></Contents>"
            } + "</ListBucketResult>"

        val conversations: ConversationRepository by lazy {
            val driver = BundledSQLiteDriver()
            val db = buildAppDatabase(
                Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
                object : SQLiteDriver by driver {
                    override fun open(fileName: String): SQLiteConnection {
                        val connection = driver.open(fileName)
                        return object : SQLiteConnection by connection {
                            override fun prepare(sql: String): SQLiteStatement {
                                beforeStatement?.invoke(sql)
                                return connection.prepare(sql)
                            }
                        }
                    }
                }, MessageFtsDialect.UNICODE61,
            ).also { database = it }
            ConversationRepository(
                db.conversationDao(), db.messageNodeDao(), db.favoriteDao(), db, ConversationFileStore {},
                MessageFtsManager(db, MessageFtsDialect.UNICODE61),
            )
        }

        fun localVm(): BackupVM = vm()

        fun close() {
            viewModels.clear()
            scope.cancel()
            database?.close()
            httpClient.close()
            root.deleteRecursively()
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var writes = 0
        var failure: Throwable? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            failure?.let { throw it }
            return transform(data.value).also { writes++; data.value = it }
        }
    }

    private companion object {
        val assistantA = Assistant(id = Uuid.parse("15000000-0000-4000-8000-000000000001"),
            name = "CMP15 A", allowConversationSystemPrompt = false)
        val assistantB = Assistant(id = Uuid.parse("15000000-0000-4000-8000-000000000002"),
            name = "CMP15 B", allowConversationSystemPrompt = false)
        val existingProvider = ProviderSetting.OpenAI(name = "CMP15 existing", baseUrl = "https://existing.invalid/v1")
        val chatboxConversationId = Uuid.parse("cc1f24b1-4cc0-3f65-adac-ce33ee775e25")
        val webConfig = WebDavConfig("https://unit.invalid/dav", "test-user", "test-only", "folder / 中文",
            listOf(WebDavConfig.BackupItem.FILES, WebDavConfig.BackupItem.DATABASE))
        val s3Config = S3Config("https://unit.invalid", "test-id", "test-only", "test-bucket", "region-x", false,
            listOf(S3Config.BackupItem.FILES))
        fun webItem(name: String, time: Long) = WebDavBackupItem(
            "/$name", name, 1024, Instant.fromEpochMilliseconds(time),
        )
        fun s3Item(name: String, time: Long) = S3BackupItem(
            "nested/$name", name, 2048, Instant.fromEpochMilliseconds(time),
        )
    }
}
