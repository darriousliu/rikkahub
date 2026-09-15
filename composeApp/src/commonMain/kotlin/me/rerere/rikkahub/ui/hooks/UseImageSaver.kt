package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import me.rerere.rikkahub.platform.encodeImageToPng
import me.rerere.rikkahub.service.toLocalFilePath
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.compose.koinInject
import kotlin.io.encoding.Base64
import kotlin.time.Clock

@Composable
fun rememberImageSaver(): (String) -> Unit {
    val client = koinInject<HttpClient>()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val pendingImage = remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberFileSaverLauncher(dialogSettings = FileKitDialogSettings.createDefault()) { target ->
        val bytes = pendingImage.value
        if (target != null && bytes != null) {
            scope.launch {
                runCatching { target.write(bytes) }
                    .onSuccess { toaster.show("已保存图片", type = ToastType.Success) }
                    .onFailure { toaster.show(it.toString(), type = ToastType.Error) }
            }
        }
        pendingImage.value = null
    }
    return { image ->
        scope.launch {
            toaster.show("正在保存")
            runCatching { readImageForSave(image, client) }
                .onSuccess { bytes ->
                    if (bytes != null) {
                        pendingImage.value = bytes
                        launcher.launch(
                            suggestedName = "RikkaHub_${Clock.System.now().toEpochMilliseconds()}",
                            defaultExtension = "png",
                        )
                    }
                }
                .onFailure { toaster.show(it.toString(), type = ToastType.Error) }
        }
    }
}

// The original FilesManager image branches, with FileKit/Ktor replacing Android I/O.
internal suspend fun readImageForSave(image: String, client: HttpClient): ByteArray? = withContext(Dispatchers.IO) {
    when {
        image.startsWith("data:image") -> encodeImageToPng(Base64.decode(image.substringAfter("base64,")))!!
        image.startsWith("file:") -> PlatformFile(image.toLocalFilePath()).readBytes()
        image.startsWith("/") || Path(image).isAbsolute -> PlatformFile(image).readBytes()
        image.startsWith("http") -> runCatching {
            val response = client.get(image)
            if (response.status == HttpStatusCode.OK) encodeImageToPng(response.body()) else null
        }.getOrNull()
        else -> error("Invalid image format")
    }
}
