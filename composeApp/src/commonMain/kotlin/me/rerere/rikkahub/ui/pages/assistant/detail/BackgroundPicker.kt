package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.assistant_page_background_set
import me.rerere.rikkahub.generated.resources.assistant_page_change_background
import me.rerere.rikkahub.generated.resources.assistant_page_chat_background
import me.rerere.rikkahub.generated.resources.assistant_page_chat_background_desc
import me.rerere.rikkahub.generated.resources.assistant_page_enter_image_url
import me.rerere.rikkahub.generated.resources.assistant_page_image_url
import me.rerere.rikkahub.generated.resources.assistant_page_remove
import me.rerere.rikkahub.generated.resources.assistant_page_select_background
import me.rerere.rikkahub.generated.resources.assistant_page_select_from_gallery
import me.rerere.rikkahub.generated.resources.assistant_page_cancel
import me.rerere.rikkahub.generated.resources.assistant_page_confirm
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.platform.FileStoreArea
import me.rerere.rikkahub.ui.components.ui.FormItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun BackgroundPicker(
    modifier: Modifier = Modifier,
    background: String?,
    backgroundOpacity: Float = 1f,
    onUpdate: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fileStore = remember { FileKitPlatformFileStore() }
    var showPicker by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        file?.let {
            scope.launch {
                fileStore.copyIntoSandbox(it, FileStoreArea.IMAGES)
                    .onSuccess { stored -> onUpdate(stored.file.path) }
            }
        }
    }

    FormItem(
        modifier = modifier,
        label = { Text(stringResource(Res.string.assistant_page_chat_background)) },
        description = { Text(stringResource(Res.string.assistant_page_chat_background_desc)) },
    ) {
        Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (background == null) {
                        Res.string.assistant_page_select_background
                    } else {
                        Res.string.assistant_page_change_background
                    },
                ),
            )
        }
        if (background != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.assistant_page_background_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onUpdate(null) }) {
                    Text(stringResource(Res.string.assistant_page_remove))
                }
            }
            AsyncImage(
                model = background,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().alpha(backgroundOpacity.coerceIn(0f, 1f)),
            )
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(Res.string.assistant_page_select_background)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showPicker = false
                            imagePicker.launch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.assistant_page_select_from_gallery)) }
                    Button(
                        onClick = {
                            showPicker = false
                            urlInput = background.orEmpty()
                            showUrlInput = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.assistant_page_enter_image_url)) }
                }
            },
            confirmButton = {},
        )
    }

    if (showUrlInput) {
        AlertDialog(
            onDismissRequest = { showUrlInput = false },
            title = { Text(stringResource(Res.string.assistant_page_enter_image_url)) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(Res.string.assistant_page_image_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(urlInput.trim().takeIf(String::isNotEmpty))
                        showUrlInput = false
                    },
                ) { Text(stringResource(Res.string.assistant_page_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInput = false }) {
                    Text(stringResource(Res.string.assistant_page_cancel))
                }
            },
        )
    }
}
