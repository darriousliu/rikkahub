package me.rerere.rikkahub.data.files

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LegacyIosFileMigrationTest {
    private val root = Path(SystemTemporaryDirectory, "cmp-ios-files-${Uuid.random()}").canonicalFile
    private val migration = LegacyIosFileMigration(root)
    private val oldRoot = "/private/var/mobile/Containers/Data/Application/${Uuid.random()}/Documents"

    @AfterTest
    fun cleanup() { root.deleteRecursively() }

    @Test
    fun repairsTheActualOldDoublePrefixAndDoubleEncodingWithoutDecodingLiteralPercentTwice() {
        val relative = "platform-files/attachments/中文 space + %2F #?.txt"
        val current = file(relative)
        val fileKitUri = "file://${"$oldRoot/$relative".encodeURLPath()}"
        val oldImportUri = "file://${fileKitUri.encodeURLPath()}"
        assertEquals(current, migration.repairLocation(oldImportUri))
        assertEquals(current, migration.repairLocation(fileKitUri))
        assertEquals(current, migration.repairLocation("$oldRoot/$relative"))
        assertEquals(current, migration.repairLocation(current))
    }

    @Test
    fun repairsAvatarBackgroundAndEveryNestedUrlButLeavesMessageTextAndMetadataUnchanged() {
        val relative = "platform-files/images/avatar.png"
        val current = file(relative)
        val old = "file://$oldRoot/$relative"
        val input = """{"avatar":{"url":"$old"},"background":"$old","id":"original-id",
            "messages":[{"parts":[{"output":[{"url":"$old"}]}],"text":"$old"}],"unknown":7}"""
        val expected = input.replace("\"url\":\"$old\"", "\"url\":\"$current\"")
            .replace("\"background\":\"$old\"", "\"background\":\"$current\"")
        val repaired = migration.repairJson(input)
        assertEquals(JsonInstant.parseToJsonElement(expected), JsonInstant.parseToJsonElement(repaired))
        assertEquals(repaired, migration.repairJson(repaired))
        val unchanged = "{ \"text\" : \"$old\" }"
        assertEquals(unchanged, migration.repairJson(unchanged))
    }

    @Test
    fun keepsMissingFilesExternalDirectoriesRemoteUrlsAndTraversalUnchanged() {
        file("upload/present.txt")
        listOf("file://$oldRoot/upload/missing.txt", "file:///tmp/upload/present.txt",
            "file://$oldRoot/private/present.txt", "file://$oldRoot/upload/../upload/present.txt",
            "https://example.invalid/$oldRoot/upload/present.txt", "content://files/12", "file:///external/bad%GG.png",
            "data:image/png;base64,aGVsbG8=").forEach {
            assertEquals(it, migration.repairLocation(it))
        }
    }

    @Test
    fun missingFileCanBeRestoredLaterAndRepairedOnTheNextOpen() {
        val old = "file://$oldRoot/upload/restored.txt"
        assertEquals(old, migration.repairLocation(old))
        val current = file("upload/restored.txt")
        assertEquals(current, migration.repairLocation(old))
    }

    @Test
    fun preferencesMigrationOnlyChangesAssistantAndDisplayAssetFieldsAndDoesNotChangeVersion() = runTest {
        val current = file("upload/avatar.png")
        val old = "file://$oldRoot/upload/avatar.png"
        val untouched = stringPreferencesKey("unrelated-setting")
        val input = preferencesOf(SettingsStore.VERSION to 3,
            SettingsStore.ASSISTANTS to """[{"avatar":{"url":"$old"},"background":"$old"}]""",
            SettingsStore.DISPLAY_SETTING to """{"userAvatar":{"url":"$old"}}""",
            untouched to "unchanged")
        assertTrue(migration.shouldMigrate(input))
        val result = migration.migrate(input)
        assertEquals(3, result[SettingsStore.VERSION])
        assertEquals("unchanged", result[untouched])
        assertEquals(input.asMap().keys, result.asMap().keys)
        assertTrue(result[SettingsStore.ASSISTANTS]!!.contains(current))
        assertTrue(result[SettingsStore.DISPLAY_SETTING]!!.contains(current))
        assertFalse(migration.shouldMigrate(result))
    }

    @Test
    fun sqliteRepairKeepsRowsBranchSelectionAndFavoritesAndIsIdempotent() {
        val current = file("upload/old.png")
        val old = "file://${"file://$oldRoot/upload/old.png".encodeURLPath()}"
        val json = """[{"id":"same-message","parts":[{"url":${JsonPrimitive(old)}}]}]"""
        BundledSQLiteDriver().open(":memory:").use { db ->
            listOf("CREATE TABLE message_node(id TEXT PRIMARY KEY, messages TEXT, select_index INTEGER)",
                "CREATE TABLE favorites(id TEXT PRIMARY KEY, snapshot_json TEXT, meta_json TEXT)",
                "INSERT INTO message_node VALUES ('source', '$json', 1)",
                "INSERT INTO message_node VALUES ('fork', '$json', 0)",
                "INSERT INTO favorites VALUES ('favorite', '$json', '{\"title\":\"unchanged\"}')").forEach {
                db.prepare(it).use { statement -> statement.step() }
            }
            migration.migrateDatabase(db)
            repeat(2) {
                db.prepare("SELECT id, messages, select_index FROM message_node ORDER BY id").use { rows ->
                    assertTrue(rows.step())
                    assertEquals("fork", rows.getText(0))
                    assertTrue(rows.getText(1).contains(current))
                    assertTrue(rows.getText(1).contains("same-message"))
                    assertEquals(0L, rows.getLong(2))
                    assertTrue(rows.step())
                    assertEquals("source", rows.getText(0))
                    assertEquals(1L, rows.getLong(2))
                    assertFalse(rows.step())
                }
                db.prepare("SELECT snapshot_json, meta_json FROM favorites").use { row ->
                    assertTrue(row.step())
                    assertTrue(row.getText(0).contains(current))
                    assertEquals("{\"title\":\"unchanged\"}", row.getText(1))
                }
                migration.migrateDatabase(db)
            }
        }
    }

    private fun file(relative: String): String {
        val path = root.resolve(relative)
        path.parent!!.mkdirs()
        path.writeBytes(byteArrayOf(1, 3, 5))
        return "file://${path.toString().encodeURLPath()}"
    }
}
