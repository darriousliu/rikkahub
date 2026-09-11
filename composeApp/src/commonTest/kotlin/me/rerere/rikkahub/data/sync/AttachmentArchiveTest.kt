package me.rerere.rikkahub.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes as readFileBytes
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.common.archive.PlatformZipArchive
import me.rerere.common.archive.readBytes
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AttachmentArchiveTest {
    private val root = Path(SystemTemporaryDirectory, "cmp-attachment-archive-${Uuid.random()}")
        .canonicalFile.apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val preferences = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    private val settings = SettingsStore(preferences, scope)
    private val originalPaths = listOf("upload/original.txt", "fonts/font.ttf", "skills/example/SKILL.md")
    private val legacyPaths = listOf("platform-files/attachments/old.txt", "platform-files/images/avatar.png")

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun backupIncludesOriginalAndMigratedAttachmentsAndNewImports() = runTest {
        (originalPaths + legacyPaths + listOf("private/ignored.txt", "upload/nested/ignored.txt"))
            .forEach { write(it) }
        val stored = FileKitPlatformFileStore(platform(root)).writeIntoSandbox(byteArrayOf(3, 2, 1), "new.png")
        val entries = entries(service(root).prepareArchive(includeDatabase = false, includeFiles = true))
        assertEquals((originalPaths + legacyPaths + listOf("settings.json", "upload/${stored.name}")).toSet(), entries.keys)
        (originalPaths + legacyPaths).forEach { assertContentEquals(it.encodeToByteArray(), entries.getValue(it)) }
        assertContentEquals(byteArrayOf(3, 2, 1), entries.getValue("upload/${stored.name}"))
    }

    @Test
    fun restoreRetainsLegacyRelativeNamesAndBytesWithoutMovingExistingFiles() = runTest {
        (originalPaths + legacyPaths).forEach { write(it) }
        val archive = service(root).prepareArchive(includeDatabase = false, includeFiles = true)
        val restored = root.resolve("restored")
        service(restored).restoreArchive(archive, includeDatabase = false, includeFiles = true)
        (originalPaths + legacyPaths).forEach { path ->
            assertTrue(root.resolve(path).exists())
            assertContentEquals(path.encodeToByteArray(), platform(restored.resolve(path)).readFileBytes())
        }
    }

    @Test
    fun excludingFilesLeavesBothOriginalAndMigratedAttachmentSetsOutOfTheBackup() = runTest {
        (originalPaths + legacyPaths).forEach { write(it) }
        assertEquals(setOf("settings.json"), entries(service(root).prepareArchive(false, false)).keys)
    }

    @Test
    fun excludingFilesOnRestoreDoesNotWriteEitherAttachmentDirectory() = runTest {
        (originalPaths + legacyPaths).forEach { write(it) }
        val archive = service(root).prepareArchive(false, true)
        val restored = root.resolve("restored")
        service(restored).restoreArchive(archive, false, false)
        (originalPaths + legacyPaths).forEach { assertFalse(restored.resolve(it).exists()) }
    }

    private fun service(files: Path) = BackupArchiveService(
        settings, JsonInstant, BackupFileLayout(platform(files), platform(root.resolve("cache"))),
    )

    private fun write(path: String) {
        val file = root.resolve(path)
        file.parent!!.mkdirs()
        file.writeBytes(path.encodeToByteArray())
    }

    private suspend fun entries(file: PlatformFile): Map<String, ByteArray> = buildMap {
        PlatformZipArchive.read(file.source().buffered()) { entry -> put(entry.name, entry.readBytes()) }
    }

    private fun platform(path: Path) = PlatformFile(path.toString())
}
