package me.rerere.rikkahub.ui.pages.assistant

/** Removes assistant-owned local assets while leaving remote URLs untouched. */
fun interface AssistantAssetCleaner {
    suspend fun deleteLocalAssets(locations: List<String>)
}
