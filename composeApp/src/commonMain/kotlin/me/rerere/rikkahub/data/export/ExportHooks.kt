package me.rerere.rikkahub.data.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.Uri
import coil3.compose.LocalPlatformContext
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.PickerResultLauncher
import io.github.vinceglb.filekit.dialogs.compose.SaverResultLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.writeString
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.common.PlatformContext
import me.rerere.common.utils.getUriForFile
import me.rerere.common.utils.mkdirs
import me.rerere.common.utils.toUri
import me.rerere.common.utils.writeContentToUri
import me.rerere.rikkahub.ui.pages.chat.shareFile

@Stable
class ExporterState<T>(
    private val data: T,
    private val serializer: ExportSerializer<T>,
    private val context: PlatformContext,
    private val scope: CoroutineScope,
    private val createDocumentLauncher: SaverResultLauncher,
) {
    val value: String
        get() = serializer.exportToJson(data)

    val fileName: String
        get() = serializer.getExportFileName(data)

    fun exportToFile(fileName: String = this.fileName) {
        createDocumentLauncher.launch(fileName)
    }

    fun exportAndShare(fileName: String = this.fileName) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                val cacheDir = PlatformFile(FileKit.cacheDir, "export")
                cacheDir.mkdirs()
                val file = PlatformFile(cacheDir, fileName)
                file.writeString(value)
                file
            }

            val uri = getUriForFile(
                context,
                file
            )
            shareFile(context, uri, "application/json")
        }
    }

    internal fun writeToUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            writeContentToUri(context, uri, value.toByteArray())
        }
    }
}

@Composable
fun <T> rememberExporter(
    data: T,
    serializer: ExportSerializer<T>,
): ExporterState<T> {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()

    var pendingState by remember { mutableStateOf<ExporterState<T>?>(null) }

    val createDocumentLauncher = rememberFileSaverLauncher { uri ->
        uri?.let { pendingState?.writeToUri(it.toUri(context)) }
    }

    val state = remember(data, serializer) {
        ExporterState(
            data = data,
            serializer = serializer,
            context = context,
            scope = scope,
            createDocumentLauncher = createDocumentLauncher,
        ).also { pendingState = it }
    }

    return state
}

@Stable
class ImporterState<T>(
    private val serializer: ExportSerializer<T>,
    private val context: PlatformContext,
    private val scope: CoroutineScope,
    private val openDocumentLauncher: PickerResultLauncher,
    private val onResult: (Result<T>) -> Unit,
) {
    fun importFromFile() {
        openDocumentLauncher.launch()
    }

    internal fun handleUri(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                serializer.import(context, uri)
            }
            onResult(result)
        }
    }
}

@Composable
fun <T> rememberImporter(
    serializer: ExportSerializer<T>,
    onResult: (Result<T>) -> Unit,
): ImporterState<T> {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()

    var pendingState by remember { mutableStateOf<ImporterState<T>?>(null) }

    val openDocumentLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(setOf("application/json"))
    ) { uri ->
        uri?.let { pendingState?.handleUri(it.toUri(context)) }
    }

    val state = remember(serializer) {
        ImporterState(
            serializer = serializer,
            context = context,
            scope = scope,
            openDocumentLauncher = openDocumentLauncher,
            onResult = onResult,
        ).also { pendingState = it }
    }

    return state
}
