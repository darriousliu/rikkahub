package me.rerere.rikkahub.ui.pages.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.repository.BackupLocalFileService
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FileKitBackupLocalFileService
import me.rerere.rikkahub.data.sync.BackupArchiveService
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.S3BackupTransport
import me.rerere.rikkahub.data.sync.WebDavBackupTransport
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UiState
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

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
    fun `initial lists load once and only WebDAV adds stable descending sorting`() = runTest(dispatcher) {
        val f = fixture()
        val first = webItem("first", 20)
        val tied = webItem("tied", 20)
        val old = webItem("old", 10)
        f.webItems = listOf(old, first, tied)
        f.s3Items = listOf(s3Item("old", 10), s3Item("new", 20))
        val gate = CompletableDeferred<Unit>()
        f.listGate = gate
        val vm = f.vm()
        assertSame(UiState.Idle, vm.webDavBackupItems.value)
        assertSame(UiState.Idle, vm.s3BackupItems.value)
        runCurrent()
        assertSame(UiState.Loading, vm.webDavBackupItems.value)
        assertSame(UiState.Loading, vm.s3BackupItems.value)
        assertEquals(listOf("web-list", "s3-list"), f.calls.map { it.first })
        gate.complete(Unit)
        runCurrent()
        assertEquals(UiState.Success(listOf(first, tied, old)), vm.webDavBackupItems.value)
        assertEquals(UiState.Success(f.s3Items), vm.s3BackupItems.value)
    }

    @Test
    fun `list errors including cancellation are exposed unchanged and a reload can succeed`() = runTest(dispatcher) {
        val f = fixture()
        val failure = IllegalStateException("list failed")
        val cancelled = CancellationException("list cancelled")
        f.webFailure = failure
        f.s3Failure = cancelled
        val vm = f.vm()
        runCurrent()
        assertSame(failure, assertIs<UiState.Error>(vm.webDavBackupItems.value).error)
        assertSame(cancelled, assertIs<UiState.Error>(vm.s3BackupItems.value).error)
        f.webFailure = null
        f.s3Failure = null
        vm.loadBackupFileItems()
        vm.loadS3BackupFileItems()
        runCurrent()
        assertEquals(UiState.Success(emptyList()), vm.webDavBackupItems.value)
        assertEquals(UiState.Success(emptyList()), vm.s3BackupItems.value)
        assertEquals(4, f.calls.size)
        assertEquals(0, f.preferences.writes)
    }

    @Test
    fun `settings update remains launched and persists existing keys readable by a new store`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        val old = vm.settings.value
        val changed = old.copy(webDavConfig = webConfig, s3Config = s3Config)
        vm.updateSettings(changed)
        assertEquals(old, vm.settings.value)
        assertEquals(0, f.preferences.writes)
        runCurrent()
        assertEquals(webConfig, vm.settings.value.webDavConfig)
        assertEquals(s3Config, vm.settings.value.s3Config)
        assertEquals(webConfig, JsonInstant.decodeFromString<WebDavConfig>(
            f.preferences.data.value[SettingsStore.WEBDAV_CONFIG]!!,
        ))
        assertEquals(s3Config, JsonInstant.decodeFromString<S3Config>(
            f.preferences.data.value[SettingsStore.S3_CONFIG]!!,
        ))
        val reopened = SettingsStore(f.preferences, f.scope)
        assertEquals(webConfig, reopened.settingsFlow.value.webDavConfig)
        assertEquals(s3Config, reopened.settingsFlow.value.s3Config)
        assertEquals("webdav_config", SettingsStore.WEBDAV_CONFIG.name)
        assertEquals("s3_config", SettingsStore.S3_CONFIG.name)
    }

    @Test
    fun `all network entry points read current configs and preserve items status and option order`() =
        runTest(dispatcher) {
            val f = fixture()
            val vm = f.vm()
            runCurrent()
            f.calls.clear()
            f.store.update { it.copy(webDavConfig = webConfig, s3Config = s3Config) }
            val web = webItem("chosen", 5)
            val s3 = s3Item("chosen", 5)
            assertEquals(71, vm.testWebDav())
            assertEquals(72, vm.restore(web))
            assertEquals(73, vm.deleteWebDavBackupFile(web))
            assertEquals(81, vm.testS3())
            assertEquals(82, vm.restoreFromS3(s3))
            assertEquals(83, vm.deleteS3BackupFile(s3))
            assertEquals(
                listOf("web-test", "web-restore", "web-delete", "s3-test", "s3-restore", "s3-delete"),
                f.calls.map { it.first },
            )
            assertEquals(listOf(webConfig, webConfig, webConfig, s3Config, s3Config, s3Config),
                f.calls.map { it.second })
            assertEquals(listOf(web, web, s3, s3), f.items)
            assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
        }

    @Test
    fun `both true and false backup returns record completion as in the original VM`() = runTest(dispatcher) {
        for (result in listOf(true, false)) {
            val f = fixture()
            f.backupResult = result
            val vm = f.vm()
            runCurrent()
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
    fun `backup timestamp is taken after completion and uses the latest unrelated settings`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        runCurrent()
        f.store.update { it.copy(webDavConfig = webConfig) }
        val gate = CompletableDeferred<Unit>()
        f.backupGate = gate
        val pending = async { vm.backup() }
        runCurrent()
        assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
        f.now += 999
        f.store.update { it.copy(launchCount = 61, webDavConfig = webConfig.copy(path = "changed")) }
        gate.complete(Unit)
        pending.await()
        assertEquals(webConfig, f.calls.last().second)
        assertEquals(61, vm.settings.value.launchCount)
        assertEquals("changed", vm.settings.value.webDavConfig.path)
        assertEquals(f.now, vm.settings.value.backupReminderConfig.lastBackupTime)
    }

    @Test
    fun `backup failures and cancellation propagate without timestamp writes or automatic retries`() =
        runTest(dispatcher) {
            for (error in listOf(IllegalStateException("failed"), CancellationException("cancelled"))) {
                val f = fixture()
                val vm = f.vm()
                runCurrent()
                f.webFailure = error
                f.s3Failure = error
                assertSame(error, runCatching { vm.backup() }.exceptionOrNull())
                assertSame(error, runCatching { vm.backupToS3() }.exceptionOrNull())
                assertEquals(0, f.preferences.writes)
                assertEquals(0, vm.settings.value.backupReminderConfig.lastBackupTime)
                assertEquals(1, f.calls.count { it.first == "web-backup" })
                assertEquals(1, f.calls.count { it.first == "s3-backup" })
            }
        }

    @Test
    fun `test restore and delete propagate the exact transport errors`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        runCurrent()
        val error = IllegalArgumentException("transport")
        f.webFailure = error
        f.s3Failure = error
        val operations: List<suspend () -> Any> = listOf(
            { vm.testWebDav() }, { vm.restore(webItem("x", 0)) }, { vm.deleteWebDavBackupFile(webItem("x", 0)) },
            { vm.testS3() }, { vm.restoreFromS3(s3Item("x", 0)) }, { vm.deleteS3BackupFile(s3Item("x", 0)) },
        )
        operations.forEach { assertSame(error, runCatching { it() }.exceptionOrNull()) }
        assertEquals(0, f.preferences.writes)
    }

    @Test
    fun `concurrent backup calls remain independent`() = runTest(dispatcher) {
        val f = fixture()
        val vm = f.vm()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        f.backupGate = gate
        val first = async { vm.backup() }
        val second = async { vm.backup() }
        runCurrent()
        assertEquals(2, f.calls.count { it.first == "web-backup" })
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
        assertTrue(f.calls.isEmpty())
        assertSame(UiState.Idle, vm.webDavBackupItems.value)
        assertSame(UiState.Idle, vm.s3BackupItems.value)
    }

    @Test
    fun `timestamp persistence failure still propagates after the transport and keeps the old memory behavior`() =
        runTest(dispatcher) {
            val f = fixture()
            val vm = f.vm()
            runCurrent()
            val error = IllegalStateException("disk failure")
            f.preferences.failure = error
            assertSame(error, runCatching { vm.backup() }.exceptionOrNull())
            assertEquals(1, f.calls.count { it.first == "web-backup" })
            // SettingsStore changes its StateFlow before the write. This rollback must not silently fix that.
            assertEquals(f.now, vm.settings.value.backupReminderConfig.lastBackupTime)
            assertEquals(null, f.preferences.data.value[SettingsStore.BACKUP_REMINDER_CONFIG])
        }

    @Test
    fun `real FileKit export archives settings before recording completion and restores stored settings`() =
        runTest(dispatcher) {
            val f = fixture()
            f.store.update { it.copy(webDavConfig = webConfig, s3Config = s3Config) }
            val local = f.localFiles()
            val archive = local.prepareExport()
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
            local.restoreBackup(archive)
            assertEquals(webConfig, f.store.settingsFlow.value.webDavConfig)
            assertEquals(s3Config, f.store.settingsFlow.value.s3Config)
            assertEquals(0, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
        }

    @Test
    fun `archive creation failure does not record a backup time`() = runTest(dispatcher) {
        val f = fixture()
        f.cache.writeText("not a directory")
        assertTrue(runCatching { f.localFiles().prepareExport() }.isFailure)
        assertEquals(0, f.preferences.writes)
        assertEquals(0, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
    }

    @Test
    fun `FileKit export preserves completed archive when timestamp persistence fails`() = runTest(dispatcher) {
        val f = fixture()
        val error = IllegalStateException("disk failure")
        f.preferences.failure = error
        assertSame(error, runCatching { f.localFiles().prepareExport() }.exceptionOrNull())
        assertTrue(f.cache.listFiles()!!.any { it.extension == "zip" })
        assertEquals(f.now, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
    }

    private fun fixture() = Fixture().also { fixtures += it }

    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = MemoryPreferences()
        val store = SettingsStore(preferences, scope)
        val viewModels = ViewModelStore()
        var now = 1_789_000_000_123L
        val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(now) }
        val calls = mutableListOf<Pair<String, Any>>()
        val items = mutableListOf<Any>()
        var webItems = emptyList<WebDavBackupItem>()
        var s3Items = emptyList<S3BackupItem>()
        var listGate: CompletableDeferred<Unit>? = null
        var backupGate: CompletableDeferred<Unit>? = null
        var webFailure: Throwable? = null
        var s3Failure: Throwable? = null
        var backupResult = true
        val root: File = Files.createTempDirectory("backup-vm-contract-").toFile()
        val cache = File(root, "cache")
        private var database: AppDatabase? = null

        private fun webCall(name: String, config: WebDavConfig, item: Any? = null) {
            calls += name to config
            if (item != null) items += item
            webFailure?.let { throw it }
        }

        private fun s3Call(name: String, config: S3Config, item: Any? = null) {
            calls += name to config
            if (item != null) items += item
            s3Failure?.let { throw it }
        }

        val web = object : WebDavBackupTransport {
            override suspend fun listBackupFiles(config: WebDavConfig): List<WebDavBackupItem> {
                webCall("web-list", config)
                listGate?.await()
                return webItems
            }
            override suspend fun backup(config: WebDavConfig): Boolean {
                webCall("web-backup", config)
                backupGate?.await()
                return backupResult
            }
            override suspend fun testConnection(config: WebDavConfig): Int =
                71.also { webCall("web-test", config) }
            override suspend fun restore(config: WebDavConfig, item: WebDavBackupItem): Int =
                72.also { webCall("web-restore", config, item) }
            override suspend fun deleteBackupFile(config: WebDavConfig, item: WebDavBackupItem): Int =
                73.also { webCall("web-delete", config, item) }
        }
        val s3 = object : S3BackupTransport {
            override suspend fun listBackupFiles(config: S3Config): List<S3BackupItem> {
                s3Call("s3-list", config)
                listGate?.await()
                return s3Items
            }
            override suspend fun backupToS3(config: S3Config): Boolean {
                s3Call("s3-backup", config)
                backupGate?.await()
                return backupResult
            }
            override suspend fun testS3(config: S3Config): Int = 81.also { s3Call("s3-test", config) }
            override suspend fun restoreFromS3(config: S3Config, item: S3BackupItem): Int =
                82.also { s3Call("s3-restore", config, item) }
            override suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem): Int =
                83.also { s3Call("s3-delete", config, item) }
        }

        fun vm() = BackupVM(store, web, s3, object : BackupLocalFileService {
            override suspend fun prepareExport(): PlatformFile = error("not used")
            override suspend fun restoreBackup(source: PlatformFile): Unit = error("not used")
            override suspend fun restoreChatbox(source: PlatformFile): ChatboxRestoreResult = error("not used")
            override suspend fun restoreCherryStudio(source: PlatformFile): Unit = error("not used")
        }, clock).also { viewModels.put("backup", it) }

        fun localFiles(): FileKitBackupLocalFileService {
            val db = buildAppDatabase(
                Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
                BundledSQLiteDriver(), MessageFtsDialect.UNICODE61,
            ).also { database = it }
            val conversations = ConversationRepository(
                db.conversationDao(), db.messageNodeDao(), db.favoriteDao(), db, ConversationFileStore {},
                MessageFtsManager(db, MessageFtsDialect.UNICODE61),
            )
            val archives = BackupArchiveService(
                store, JsonInstant,
                BackupFileLayout(PlatformFile(File(root, "files")), PlatformFile(cache)),
            )
            return FileKitBackupLocalFileService(archives, store, conversations, clock)
        }

        fun close() {
            viewModels.clear()
            scope.cancel()
            database?.close()
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
