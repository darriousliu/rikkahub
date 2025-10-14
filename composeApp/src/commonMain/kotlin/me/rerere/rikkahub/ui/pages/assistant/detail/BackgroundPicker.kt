package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.toUri
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.utils.createChatFilesByContents
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.Res
import rikkahub.composeapp.generated.resources.assistant_page_background_set
import rikkahub.composeapp.generated.resources.assistant_page_cancel
import rikkahub.composeapp.generated.resources.assistant_page_change_background
import rikkahub.composeapp.generated.resources.assistant_page_chat_background
import rikkahub.composeapp.generated.resources.assistant_page_chat_background_desc
import rikkahub.composeapp.generated.resources.assistant_page_confirm
import rikkahub.composeapp.generated.resources.assistant_page_enter_image_url
import rikkahub.composeapp.generated.resources.assistant_page_image_url
import rikkahub.composeapp.generated.resources.assistant_page_remove
import rikkahub.composeapp.generated.resources.assistant_page_remove_background
import rikkahub.composeapp.generated.resources.assistant_page_select_background
import rikkahub.composeapp.generated.resources.assistant_page_select_from_gallery

@Composable
fun BackgroundPicker(
    background: String?,
    onUpdate: (String?) -> Unit
) {
    val context = LocalPlatformContext.current
    var showPickOption by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.Image
    ) { uri ->
        uri?.let {
            val localUris = context.createChatFilesByContents(listOf(it.absolutePath().toUri()))
            localUris.firstOrNull()?.let { localUri ->
                onUpdate(localUri.toString())
            }
        }
    }

    Card {
        FormItem(
            modifier = Modifier.padding(16.dp),
            label = {
                Text(stringResource(Res.string.assistant_page_chat_background))
            },
            description = {
                Text(stringResource(Res.string.assistant_page_chat_background_desc))
            }
        ) {
            Button(
                onClick = {
                    showPickOption = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (background != null) {
                        stringResource(Res.string.assistant_page_change_background)
                    } else {
                        stringResource(Res.string.assistant_page_select_background)
                    }
                )
            }

            if (background != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.assistant_page_background_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            onUpdate(null)
                        }
                    ) {
                        Text(stringResource(Res.string.assistant_page_remove))
                    }
                }

                AsyncImage(
                    model = background,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showPickOption) {
        AlertDialog(
            onDismissRequest = {
                showPickOption = false
            },
            title = {
                Text(stringResource(Res.string.assistant_page_select_background))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showPickOption = false
                            imagePickerLauncher.launch()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.assistant_page_select_from_gallery))
                    }
                    Button(
                        onClick = {
                            showPickOption = false
                            urlInput = ""
                            showUrlInput = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.assistant_page_enter_image_url))
                    }
                    if (background != null) {
                        Button(
                            onClick = {
                                showPickOption = false
                                onUpdate(null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.assistant_page_remove_background))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPickOption = false
                    }
                ) {
                    Text(stringResource(Res.string.assistant_page_cancel))
                }
            }
        )
    }

    if (showUrlInput) {
        AlertDialog(
            onDismissRequest = {
                showUrlInput = false
            },
            title = {
                Text(stringResource(Res.string.assistant_page_enter_image_url))
            },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(Res.string.assistant_page_image_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/image.jpg") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onUpdate(urlInput.trim())
                            showUrlInput = false
                        }
                    }
                ) {
                    Text(stringResource(Res.string.assistant_page_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUrlInput = false
                    }
                ) {
                    Text(stringResource(Res.string.assistant_page_cancel))
                }
            }
        )
    }
}
