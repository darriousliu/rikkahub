package me.rerere.rikkahub.data.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.platform.sharePlatformFile

@Stable
class ExporterState<T>(
    private val data: T,
    private val serializer: ExportSerializer<T>,
    private val scope: CoroutineScope,
    private val saveFile: (fileName: String, content: String) -> Unit,
    private val shareFile: suspend (fileName: String, content: String) -> Unit,
) {
    val value: String
        get() = serializer.exportToJson(data)

    val fileName: String
        get() = serializer.getExportFileName(data)

    fun exportToFile(fileName: String = this.fileName) {
        saveFile(fileName, value)
    }

    fun exportAndShare(fileName: String = this.fileName) {
        scope.launch { shareFile(fileName, value) }
    }
}

@Composable
fun <T> rememberExporter(
    data: T,
    serializer: ExportSerializer<T>,
): ExporterState<T> {
    val scope = rememberCoroutineScope()
    val pendingExport = remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { target ->
        val content = pendingExport.value
        if (target != null && content != null) {
            scope.launch { target.writeString(content) }
        }
        pendingExport.value = null
    }
    return remember(data, serializer, saveLauncher) {
        ExporterState(
            data = data,
            serializer = serializer,
            scope = scope,
            saveFile = { fileName, content ->
                pendingExport.value = content
                saveLauncher.launch(
                    suggestedName = fileName.removeSuffix(".json"),
                    defaultExtension = "json",
                    allowedExtensions = setOf("json"),
                )
            },
            shareFile = { fileName, content ->
                val exportDirectory = FileKit.cacheDir / "export"
                exportDirectory.createDirectories()
                val target = exportDirectory / fileName
                target.writeString(content)
                sharePlatformFile(target)
            },
        )
    }
}

@Stable
class ImporterState<T>(
    private val launchPicker: () -> Unit,
) {
    fun importFromFile() {
        launchPicker()
    }
}

@Composable
fun <T> rememberImporter(
    serializer: ExportSerializer<T>,
    onResult: (Result<T>) -> Unit,
): ImporterState<T> {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.File("json")) { file: PlatformFile? ->
        if (file != null) {
            scope.launch {
                val result = runCatching {
                    require(file.name.endsWith(".json", ignoreCase = true)) { "Not a JSON file" }
                    file.readString()
                }.fold(
                    onSuccess = { content -> serializer.import(content, file.name) },
                    onFailure = { Result.failure(it) },
                )
                onResult(result)
            }
        }
    }

    return remember(picker) { ImporterState(picker::launch) }
}
