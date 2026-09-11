package me.rerere.rikkahub.data.files

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.SQLiteConnection
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath
import io.ktor.http.URLDecodeException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.SerializationException
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

/** 修复旧 CMP iOS 自有附件地址；文件、会话 ID、元数据及其余设置保持。 */
internal class LegacyIosFileMigration(private val filesDirectory: Path) : DataMigration<Preferences> {
    private val preferenceKeys = listOf(SettingsStore.ASSISTANTS, SettingsStore.DISPLAY_SETTING)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = preferenceKeys.any { key ->
        currentData[key]?.let { repairJson(it) != it } == true
    }

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        preferenceKeys.forEach { key -> currentData[key]?.let { this[key] = repairJson(it) } }
    }.toPreferences()

    override suspend fun cleanUp() {}

    fun migrateDatabase(connection: SQLiteConnection) {
        repairColumn(connection, "message_node", "messages")
        repairColumn(connection, "favorites", "snapshot_json")
    }

    private fun repairColumn(connection: SQLiteConnection, table: String, column: String) {
        connection.prepare("SELECT id, $column FROM $table").use { rows ->
            connection.prepare("UPDATE $table SET $column = ? WHERE id = ?").use { update ->
                while (rows.step()) {
                    val original = rows.getText(1)
                    val repaired = repairJson(original)
                    if (repaired != original) {
                        update.bindText(1, repaired)
                        update.bindText(2, rows.getText(0))
                        update.step()
                        update.reset()
                    }
                }
            }
        }
    }

    internal fun repairJson(json: String): String {
        val original = try {
            JsonInstant.parseToJsonElement(json)
        } catch (_: SerializationException) {
            return json
        }
        val repaired = repairElement(original)
        return if (repaired == original) json else repaired.toString()
    }

    private fun repairElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key in listOf("url", "background") && value is JsonPrimitive && value.isString) {
                JsonPrimitive(repairLocation(value.content))
            } else {
                repairElement(value)
            }
        })
        is JsonArray -> JsonArray(element.map(::repairElement))
        else -> element
    }

    internal fun repairLocation(location: String): String {
        var path = location
        // 旧实现给 FileKit 返回的 file: URI 再加前缀并编码，每层只解码一次。
        while (path.startsWith("file:")) {
            path = try {
                path.removePrefix("file:").removePrefix("//").decodeURLPart()
            } catch (_: URLDecodeException) {
                return location
            }
        }
        val root = filesDirectory.toString().trimEnd('/')
        val relative = when {
            path.startsWith("$root/") -> path.removePrefix("$root/")
            else -> containerDocuments.find(path)?.let { path.substring(it.range.last + 1) }
        } ?: return location
        if (managedDirectories.none { relative.startsWith(it) }) return location
        if (relative.split('/').any { it == ".." || it == "." }) return location
        val target = Path(filesDirectory, relative)
        // 备份尚未恢复文件时保留旧地址；下次打开存储时可再次修复。
        if (SystemFileSystem.metadataOrNull(target)?.isRegularFile != true) return location
        return "file://${target.toString().encodeURLPath()}"
    }

    private companion object {
        val managedDirectories = listOf("upload/", "platform-files/attachments/", "platform-files/images/")
        val containerDocuments = Regex(
            "^/.*/Containers/Data/Application/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/Documents/"
        )
    }
}
