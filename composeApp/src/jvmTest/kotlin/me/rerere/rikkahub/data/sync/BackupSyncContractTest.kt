package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.di.commonModule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BackupSyncContractTest {
    @Test
    fun `real sqlite WAL archive restores uncheckpointed rows for both database basenames`() = runTest {
        for (protocol in Protocol.entries) for (basename in listOf("rikka_hub", "rikka_hub.db")) {
            Fixture(basename).use { f ->
                val driver = BundledSQLiteDriver()
                val database = f.databaseFiles.getValue("rikka_hub.db")
                val source = driver.open(database.path)
                try {
                    source.execSQL("PRAGMA journal_mode=WAL")
                    source.execSQL("PRAGMA wal_autocheckpoint=0")
                    source.execSQL("CREATE TABLE restore_probe (id INTEGER PRIMARY KEY, content TEXT NOT NULL)")
                    source.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                    source.execSQL("INSERT INTO restore_probe VALUES (1, 'CMP16 中文 WAL')")
                    val archive = f.prepare(protocol)
                    val entries = readZip(archive)
                    assertTrue(entries.getValue("rikka_hub-wal").isNotEmpty())
                    val mainOnly = File(f.root, "main-only.db").apply { writeBytes(entries.getValue("rikka_hub.db")) }
                    driver.open(mainOnly.path).use { db ->
                        db.prepare("SELECT COUNT(*) FROM restore_probe").use {
                            assertTrue(it.step()); assertEquals(0, it.getLong(0))
                        }
                    }
                    f.download = archive.readBytes()
                } finally {
                    source.close()
                }
                f.databaseFiles.values.forEach { it.delete() }
                f.restore(protocol)
                driver.open(database.path).use { db ->
                    db.prepare("SELECT content FROM restore_probe WHERE id=1").use {
                        assertTrue(it.step()); assertEquals("CMP16 中文 WAL", it.getText(0)); assertFalse(it.step())
                    }
                }
            }
        }
    }

    @Test
    fun `both protocols preserve original entry order and backup item selection`() = runTest {
        for (protocol in Protocol.entries) for (database in listOf(false, true)) for (files in listOf(false, true)) {
            Fixture().use { f ->
                f.seed()
                f.configure(database, files)
                f.store.update { it.copy(launchCount = 41) }
                val entries = readZip(f.prepare(protocol))
                assertEquals("settings.json", entries.keys.first())
                assertEquals(41, JsonInstant.decodeFromString<Settings>(entries.getValue("settings.json").decodeToString()).launchCount)
                assertEquals(buildSet {
                    add("settings.json")
                    if (database) addAll(databaseEntries)
                    if (files) addAll(applicationEntries)
                }, entries.keys)
                if (database) assertEquals(databaseEntries, entries.keys.drop(1).take(3))
                (if (files) applicationEntries else emptyList()).forEach {
                    assertContentEquals(it.encodeToByteArray(), entries.getValue(it))
                }
            }
        }
    }

    @Test
    fun `restore maps database sidecars to each physical basename and keeps all attachment bytes`() = runTest {
        for (protocol in Protocol.entries) for (basename in listOf("rikka_hub", "rikka_hub.db")) {
            Fixture(basename).use { f ->
                f.seed()
                f.store.update { it.copy(launchCount = 23) }
                val archive = f.prepare(protocol).readBytes()
                f.store.update { it.copy(launchCount = 99) }
                applicationEntries.forEach { File(f.files, it).delete() }
                f.databaseFiles.values.forEach { it.delete() }
                f.download = archive
                f.restore(protocol)
                assertEquals(23, f.store.settingsFlow.value.launchCount)
                applicationEntries.forEach { assertEquals(it, File(f.files, it).readText()) }
                f.databaseFiles.forEach { (entry, file) -> assertEquals(entry, file.readText()) }
                assertFalse(File(f.cache, backupName).exists())
                assertTrue(File(f.files, "upload/nested/ignored.txt").exists())
            }
        }
    }

    @Test
    fun `restore flags leave excluded database and attachment files untouched but always restore settings`() = runTest {
        for (protocol in Protocol.entries) for (database in listOf(false, true)) for (files in listOf(false, true)) {
            Fixture().use { f ->
                f.seed()
                f.store.update { it.copy(launchCount = 23) }
                f.download = f.prepare(protocol).readBytes()
                f.store.update { it.copy(launchCount = 99) }
                f.configure(database, files)
                f.databaseFiles.values.forEach { it.writeText("changed") }
                applicationEntries.forEach { File(f.files, it).writeText("changed") }
                f.restore(protocol)
                assertEquals(23, f.store.settingsFlow.value.launchCount)
                f.databaseFiles.forEach { (entry, file) -> assertEquals(if (database) entry else "changed", file.readText()) }
                applicationEntries.forEach { assertEquals(if (files) it else "changed", File(f.files, it).readText()) }
            }
        }
    }

    @Test
    fun `upload uses original paths streamed bytes content headers and independent signature verification`() = runTest {
        for (protocol in Protocol.entries) Fixture().use { f ->
            f.seed()
            File(f.files, "upload/large.bin").writeBytes(ByteArray(1_048_609) { (it % 251).toByte() })
            assertTrue(f.backup(protocol))
            val request = f.requests.single { it.method.value == "PUT" }
            val bytes = assertNotNull(f.upload)
            assertEquals(bytes.size.toLong(), request.body.contentLength)
            assertEquals("application/zip", request.body.contentType.toString())
            assertTrue(request.url.encodedPath.matches(Regex(
                if (protocol == Protocol.S3) "/bucket/rikkahub_backups/backup_\\d{8}_\\d{6}\\.zip"
                else "/dav/folder/backup_\\d{8}_\\d{6}\\.zip",
            )))
            val archive = File(f.root, "uploaded.zip").apply { writeBytes(bytes) }
            assertContentEquals(File(f.files, "upload/large.bin").readBytes(), readZip(archive).getValue("upload/large.bin"))
            assertTrue(f.cache.listFiles()!!.isEmpty())
            if (protocol == Protocol.S3) {
                assertEquals(sha256(bytes), request.headers["x-amz-content-sha256"])
                verifySignature(request)
            } else {
                assertEquals("Basic Y21wMTY6dGVzdC1vbmx5", request.headers["Authorization"])
                assertEquals(listOf("PROPFIND", "PUT"), f.requests.map { it.method.value })
                assertEquals("0", f.requests.first().headers["Depth"])
            }
        }
    }

    @Test
    fun `failed and cancelled uploads retain prepared archive as the original Sync did`() = runTest {
        for (protocol in Protocol.entries) for (error in listOf(IllegalStateException("offline"), CancellationException("cancelled"))) {
            Fixture().use { f ->
                f.beforeRequest = { if (it.method.value == "PUT") throw error }
                assertEquals(error.message, runCatching { f.backup(protocol) }.exceptionOrNull()?.message)
                assertEquals(1, f.requests.count { it.method.value == "PUT" })
                assertEquals(setOf("settings.json"), readZip(f.cache.listFiles()!!.single()).keys)
                assertEquals(0, f.store.settingsFlow.value.backupReminderConfig.lastBackupTime)
            }
        }
    }

    @Test
    fun `webdav collection creation preserves propfind mkcol put ordering and errors`() = runTest {
        Fixture().use { f ->
            f.collectionExists = false
            assertTrue(f.backup(Protocol.WEBDAV))
            assertEquals(listOf("PROPFIND", "MKCOL", "PUT"), f.requests.map { it.method.value })
        }
        Fixture().use { f ->
            f.collectionExists = false
            f.beforeRequest = { if (it.method.value == "MKCOL") throw IllegalStateException("mkcol failed") }
            assertEquals("mkcol failed", runCatching { f.backup(Protocol.WEBDAV) }.exceptionOrNull()?.message)
            assertFalse(f.requests.any { it.method.value == "PUT" })
            assertTrue(f.cache.listFiles()!!.single().exists())
        }
    }

    @Test
    fun `list filtering fallback dates sorting and request parameters preserve the original protocol rules`() = runTest {
        Fixture().use { f ->
            val web = f.web.listBackupFiles(f.webConfig)
            assertEquals(listOf("backup_new.zip", "backup_tied.zip", "backup_old.zip", "backup_unknown.zip"), web.map { it.displayName })
            assertEquals(0, web.last().lastModified.toEpochMilliseconds())
            assertEquals("/retained/backup_new.zip", web.first().href)
            assertEquals(listOf("0", "1"), f.requests.map { it.headers["Depth"] })
            f.requests.clear()
            val s3 = f.s3.listBackupFiles(f.s3Config)
            assertEquals(listOf("backup_new.zip", "backup_old.zip", "backup_unknown.zip"), s3.map { it.displayName })
            assertEquals(0, s3.last().lastModified.toEpochMilliseconds())
            val request = f.requests.single()
            assertEquals("rikkahub_backups/", request.url.parameters["prefix"])
            assertEquals("1000", request.url.parameters["max-keys"])
            verifySignature(request)
        }
    }

    @Test
    fun `test and delete use original depth max keys and item selectors without local writes`() = runTest {
        Fixture().use { f ->
            f.web.testConnection(f.webConfig)
            f.s3.testS3(f.s3Config)
            f.web.deleteBackupFile(f.webConfig, WebDavBackupItem("/unused/href", backupName, 7, Instant.fromEpochMilliseconds(0)))
            f.s3.deleteS3BackupFile(f.s3Config, S3BackupItem("nested/key.zip", "unused-name", 7, Instant.fromEpochMilliseconds(0)))
            assertEquals("0", f.requests[0].headers["Depth"])
            assertEquals("1", f.requests[1].url.parameters["max-keys"])
            assertEquals(null, f.requests[1].url.parameters["prefix"])
            assertEquals("/dav/folder/$backupName", f.requests[2].url.encodedPath)
            assertEquals("/bucket/nested/key.zip", f.requests[3].url.encodedPath)
            assertTrue(f.cache.listFiles()!!.isEmpty())
            assertEquals(0, f.preferences.writes)
        }
    }

    @Test
    fun `download failure cancellation and no ZIP entries always clean original named temp file`() = runTest {
        for (protocol in Protocol.entries) for (error in listOf(IllegalStateException("download failed"), CancellationException("cancelled"), null)) {
            Fixture().use { f ->
                f.download = "not a zip".encodeToByteArray()
                f.beforeRequest = {
                    assertTrue(File(f.cache, backupName).exists())
                    if (error != null) throw error
                }
                val actual = runCatching { f.restore(protocol) }.exceptionOrNull()
                // 2.4.5's ZipInputStream treats input without a local entry as an empty stream.
                if (error == null) assertNull(actual) else assertNotNull(actual)
                assertTrue(f.cache.listFiles()!!.isEmpty())
                assertEquals(1, f.requests.size)
                assertEquals(0, f.preferences.writes)
            }
        }
    }

    @Test
    fun `settings failures preserve original wrapping and stop before later files`() = runTest {
        for (protocol in Protocol.entries) for (cancel in listOf(false, true)) Fixture().use { f ->
            f.download = zipBytes(linkedMapOf("settings.json" to JsonInstant.encodeToString(f.store.settingsFlow.value).encodeToByteArray(),
                "upload/not-written.txt" to byteArrayOf(1)))
            f.preferences.failure = if (cancel) CancellationException("store cancelled") else IllegalStateException("store failed")
            val error = assertNotNull(runCatching { f.restore(protocol) }.exceptionOrNull())
            assertEquals("Failed to restore settings: ${f.preferences.failure!!.message}", error.message)
            assertFalse(error is CancellationException)
            // Coroutine debug recovery may copy the wrapper and attach the original wrapper as its cause.
            assertEquals(error.message, generateSequence(error) { it.cause }.last().message)
            assertFalse(File(f.files, "upload/not-written.txt").exists())
            assertTrue(f.cache.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun `malformed settings preserve local restore wrapper and leave caller file unchanged`() = runTest {
        Fixture().use { f ->
            val bytes = zipBytes(mapOf("settings.json" to "{invalid".encodeToByteArray()))
            val source = File(f.root, "caller.zip").apply { writeBytes(bytes) }
            val error = assertNotNull(runCatching { f.web.restoreFromLocalFile(PlatformFile(source), f.webConfig) }.exceptionOrNull())
            assertTrue(error.message!!.startsWith("Restore failed: Failed to restore settings:"))
            assertContentEquals(bytes, source.readBytes())
            assertTrue(f.cache.listFiles()!!.isEmpty())
            val missing = assertNotNull(runCatching {
                f.web.restoreFromLocalFile(Path(f.root.path, "missing.zip"), f.webConfig)
            }.exceptionOrNull())
            assertEquals("Backup file does not exist", missing.message)
        }
    }

    @Test
    fun `archive creation failure retains incomplete archive and never uploads`() = runTest {
        for (protocol in Protocol.entries) Fixture().use { f ->
            // java.io.File.exists was the original check; an unreadable database is not silently omitted.
            f.databaseFiles.getValue("rikka_hub.db").mkdirs()
            assertNotNull(runCatching { f.backup(protocol) }.exceptionOrNull())
            assertTrue(f.cache.listFiles()!!.single().exists())
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test
    fun `zip guards reject traversal and escaping symlinks without changing original direct and recursive roots`() = runTest {
        for (protocol in Protocol.entries) Fixture().use { f ->
            f.seed()
            val outside = File(f.root, "outside.txt").apply { writeText("outside") }
            Files.createSymbolicLink(File(f.files, "upload/escape.txt").toPath(), outside.toPath())
            Files.createSymbolicLink(File(f.files, "skills/example/loop").toPath(), File(f.files, "skills").toPath())
            val entries = readZip(f.prepare(protocol))
            assertFalse("upload/escape.txt" in entries)
            assertFalse(entries.keys.any { "/loop/" in it })
            f.download = zipBytes(mapOf("../outside.txt" to "changed".encodeToByteArray()))
            val traversal = assertNotNull(runCatching { f.restore(protocol) }.exceptionOrNull())
            assertTrue(traversal.message!!.contains("Unsafe ZIP entry path"))
            assertEquals("outside", outside.readText())
            f.download = zipBytes(mapOf("upload/escape.txt" to "changed".encodeToByteArray()))
            assertIs<IllegalArgumentException>(runCatching { f.restore(protocol) }.exceptionOrNull())
            assertEquals("outside", outside.readText())
        }
    }

    @Test
    fun `cancelled in flight download is cleaned without retry or restored settings`() = runTest {
        for (protocol in Protocol.entries) Fixture().use { f ->
            val reached = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            f.beforeRequest = { reached.complete(Unit); gate.await() }
            val pending = async { f.restore(protocol) }
            reached.await()
            pending.cancel(CancellationException("user stop"))
            runCatching { pending.await() }
            pending.join()
            assertTrue(f.cache.listFiles()!!.isEmpty())
            assertEquals(1, f.requests.size)
            assertEquals(0, f.preferences.writes)
        }
    }

    private enum class Protocol { WEBDAV, S3 }

    private class Fixture(basename: String = "rikka_hub") : AutoCloseable {
        val root = Files.createTempDirectory("cmp16-sync-").toFile()
        val files = File(root, "files").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        val databaseFiles = databaseEntries.zip(listOf(basename, "$basename-wal", "$basename-shm"))
            .associate { (entry, physical) -> entry to File(root, "database/$physical").also { it.parentFile.mkdirs() } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = MemoryPreferences()
        val store = SettingsStore(preferences, scope)
        val requests = mutableListOf<HttpRequestData>()
        var upload: ByteArray? = null
        var download = zipBytes(emptyMap())
        var beforeRequest: suspend (HttpRequestData) -> Unit = {}
        var collectionExists = true
        var webConfig = WebDavConfig("https://test.invalid/dav", "cmp16", "test-only", "folder", WebDavConfig.BackupItem.entries)
        var s3Config = S3Config("https://test.invalid", "cmp16", "test-only", "bucket", "region-test", true, S3Config.BackupItem.entries)
        val http = HttpClient(MockEngine { request ->
            requests += request
            beforeRequest(request)
            when {
                request.method.value == "PROPFIND" -> if (collectionExists) respond(webList, HttpStatusCode.MultiStatus)
                    else respond("missing", HttpStatusCode.NotFound)
                request.method.value == "MKCOL" -> respond("", HttpStatusCode.Created).also { collectionExists = true }
                request.method.value == "PUT" -> respond("", HttpStatusCode.Created).also { upload = request.body.toByteArray() }
                request.url.parameters["list-type"] == "2" -> respond(s3List)
                request.method.value == "GET" -> respond(download)
                else -> respond("")
            }
        })
        val layout = BackupFileLayout(PlatformFile(files), PlatformFile(cache), databaseFiles.mapValues { PlatformFile(it.value) })
        private val application = koinApplication(createEagerInstances = false) {
            modules(commonModule, module {
                single { store }
                single { layout }
                single { http }
            })
        }
        val web: WebDavSync = application.koin.get()
        val s3: S3Sync = application.koin.get()

        fun configure(database: Boolean, files: Boolean) {
            webConfig = webConfig.copy(items = WebDavConfig.BackupItem.entries.filter {
                if (it == WebDavConfig.BackupItem.DATABASE) database else files
            })
            s3Config = s3Config.copy(items = S3Config.BackupItem.entries.filter {
                if (it == S3Config.BackupItem.DATABASE) database else files
            })
        }

        fun seed() {
            (applicationEntries + listOf("upload/nested/ignored.txt", "fonts/nested/ignored.ttf", "private/ignored.txt"))
                .forEach { path -> File(files, path).apply { parentFile.mkdirs(); writeText(path) } }
            databaseFiles.forEach { (entry, file) -> file.writeText(entry) }
        }

        suspend fun prepare(protocol: Protocol): File = File(if (protocol == Protocol.S3)
            s3.prepareBackupFile(s3Config).toString() else web.prepareBackupFile(webConfig).toKotlinxIoPath().toString())

        suspend fun backup(protocol: Protocol): Boolean = if (protocol == Protocol.S3) s3.backupToS3(s3Config) else web.backup(webConfig)

        suspend fun restore(protocol: Protocol): Int = if (protocol == Protocol.S3)
            s3.restoreFromS3(s3Config, S3BackupItem("rikkahub_backups/$backupName", backupName, download.size.toLong(), Instant.fromEpochMilliseconds(0)))
        else web.restore(webConfig, WebDavBackupItem("/unused-href", backupName, download.size.toLong(), Instant.fromEpochMilliseconds(0)))

        override fun close() { application.close(); http.close(); scope.cancel(); root.deleteRecursively() }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var failure: Throwable? = null
        var writes = 0
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            failure?.let { throw it }
            return transform(data.value).also { writes++; data.value = it }
        }
    }

    private companion object {
        const val backupName = "backup_selected.zip"
        val databaseEntries = listOf("rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm")
        val applicationEntries = listOf("upload/photo.txt", "skills/example/SKILL.md", "skills/example/scripts/nested.txt",
            "fonts/font.ttf", "platform-files/attachments/legacy.txt", "platform-files/images/avatar.png")

        fun readZip(file: File): Map<String, ByteArray> = ZipFile(file).use { zip ->
            zip.entries().asSequence().associate { it.name to zip.getInputStream(it).use { source -> source.readBytes() } }
        }

        fun zipBytes(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip -> entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
            } }
        }.toByteArray()

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

        fun verifySignature(request: HttpRequestData) {
            val auth = assertNotNull(request.headers["Authorization"])
            val scope = auth.substringAfter("Credential=cmp16/").substringBefore(',')
            val signedNames = auth.substringAfter("SignedHeaders=").substringBefore(',')
            val signature = auth.substringAfter("Signature=")
            val headers = signedNames.split(';').joinToString("") { name ->
                val value = request.headers[name] ?: when (name) {
                    "content-length" -> request.body.contentLength.toString()
                    "content-type" -> request.body.contentType.toString()
                    else -> error("Missing signed header $name")
                }
                "$name:${value.trim()}\n"
            }
            val canonical = listOf(request.method.value, request.url.encodedPath, request.url.encodedQuery,
                headers, signedNames, request.headers["x-amz-content-sha256"]!!).joinToString("\n")
            val text = "AWS4-HMAC-SHA256\n${request.headers["x-amz-date"]}\n$scope\n${sha256(canonical.encodeToByteArray())}"
            var key = "AWS4test-only".encodeToByteArray()
            scope.split('/').forEach { component -> key = hmac(key, component) }
            assertEquals(hmac(key, text).toHexString(), signature)
        }

        fun hmac(key: ByteArray, data: String): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256")); doFinal(data.encodeToByteArray())
        }

        val listItems = listOf("backup_old.zip" to "2026-09-09T00:00:00Z", "backup_new.zip" to "2026-09-10T00:00:00Z",
            "backup_tied.zip" to "2026-09-10T00:00:00Z", "backup_unknown.zip" to "invalid", "readme.txt" to "2026-09-11T00:00:00Z")
        val webList = "<D:multistatus xmlns:D=\"DAV:\">" +
            "<D:response><D:href>/dav/folder/</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat></D:response>" +
            listItems.joinToString("") { (name, time) -> "<D:response><D:href>/retained/$name</D:href><D:propstat><D:prop>" +
                "<D:displayname>$name</D:displayname><D:getcontentlength>17</D:getcontentlength><D:getlastmodified>$time</D:getlastmodified>" +
                "</D:prop></D:propstat></D:response>" } +
            "<D:response><D:href>/backup_collection.zip</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat></D:response></D:multistatus>"
        val s3List = "<ListBucketResult>" + listItems.filter { it.first != "backup_tied.zip" }.joinToString("") { (name, time) ->
            "<Contents><Key>rikkahub_backups/$name</Key><Size>17</Size><LastModified>$time</LastModified></Contents>"
        } + "<Contents><Key>other/backup_ignored.zip</Key><Size>19</Size></Contents></ListBucketResult>"
    }
}
