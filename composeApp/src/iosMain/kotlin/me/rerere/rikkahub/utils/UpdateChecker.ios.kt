package me.rerere.rikkahub.utils

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import me.rerere.common.PlatformContext

actual class UpdateChecker actual constructor(client: HttpClient) {
    actual fun checkUpdate(): Flow<UiState<UpdateInfo>> {
        TODO("Not yet implemented")
    }

    actual fun downloadUpdate(
        context: PlatformContext,
        download: UpdateDownload
    ) {
    }
}
