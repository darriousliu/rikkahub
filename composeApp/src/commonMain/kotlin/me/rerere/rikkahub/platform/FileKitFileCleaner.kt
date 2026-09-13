package me.rerere.rikkahub.platform

import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.service.toLocalFilePath

/** 非 Android 文件清理接线；兼容旧 CMP fork 共用文件，不改写存量会话。 */
class FileKitFileCleaner(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {
    suspend fun deleteChatFiles(urls: List<String>) {
        // Repository 已删除会话，数据库中剩下的引用都需要保留。
        delete(urls, settingsStore.settingsFlowRaw.first().assetLocations())
    }

    suspend fun deleteLocalAssets(locations: List<String>) {
        // 原 VM 在更新设置之前清理；只扣除本次移除的引用。
        val retained = settingsStore.settingsFlowRaw.first().assetLocations()
            .map(String::toLocalFilePath).toMutableList()
        locations.forEach { retained.remove(it.toLocalFilePath()) }
        delete(locations, retained)
    }

    private suspend fun delete(locations: List<String>, retainedAssets: List<String>) {
        val retained = (database.messageNodeDao().getFileUrls() + retainedAssets)
            .map(String::toLocalFilePath).toSet()
        locations.filter { it.startsWith("file:") || it.startsWith("/") }.forEach { location ->
            val path = location.toLocalFilePath()
            if (path !in retained) {
                // 原 File.delete() 忽略删除失败；不吞掉取消或其他非 I/O 异常。
                try {
                    SystemFileSystem.delete(Path(path), mustExist = false)
                } catch (_: IOException) {
                }
            }
        }
    }
}

private fun Settings.assetLocations(): List<String> = buildList {
    (displaySetting.userAvatar as? Avatar.Image)?.let { add(it.url) }
    assistants.forEach { assistant ->
        (assistant.avatar as? Avatar.Image)?.let { add(it.url) }
        assistant.background?.let(::add)
        addAll(Conversation.ofId(assistant.id, messages = assistant.presetMessages.map { it.toMessageNode() }).files)
    }
}
