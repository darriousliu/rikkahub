package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Edit03
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.avatar_cancel
import me.rerere.rikkahub.generated.resources.avatar_change_avatar
import me.rerere.rikkahub.generated.resources.avatar_input_url
import me.rerere.rikkahub.generated.resources.avatar_pick_emoji
import me.rerere.rikkahub.generated.resources.avatar_pick_image
import me.rerere.rikkahub.generated.resources.avatar_reset
import me.rerere.rikkahub.generated.resources.avatar_url_confirm
import me.rerere.rikkahub.generated.resources.avatar_url_dialog_title
import me.rerere.rikkahub.generated.resources.avatar_url_hint
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.platform.FileStoreArea
import me.rerere.rikkahub.platform.ImageCropRequest
import me.rerere.rikkahub.platform.ImageCropResult
import me.rerere.rikkahub.platform.rememberImageCropper
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

@Composable
fun TextAvatar(
    text: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Box(
        modifier = modifier
            .then(Modifier.size(32.dp))
            .clip(if (loading) RoundedCornerShape(50) else MaterialTheme.shapes.medium)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(1).uppercase(),
            color = LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 32.sp, stepSize = 1.sp),
            lineHeight = 0.8.em,
        )
    }
}

@Composable
fun UIAvatar(
    name: String,
    value: Avatar,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onUpdate: ((Avatar) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val fileStore = remember { FileKitPlatformFileStore() }
    var showPicker by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf<AvatarInput?>(null) }
    var input by remember { mutableStateOf("") }

    fun storeAvatar(result: ImageCropResult) {
        val file = (result as? ImageCropResult.Success)?.file ?: return
        scope.launch {
            fileStore.copyIntoSandbox(file, FileStoreArea.IMAGES)
                .onSuccess { onUpdate?.invoke(Avatar.Image(it.file.path)) }
        }
    }

    val cropper = rememberImageCropper(::storeAvatar)
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        file?.let {
            cropper.launch(
                ImageCropRequest(
                    source = it,
                    aspectRatio = 1f to 1f,
                    freeStyleCropEnabled = false,
                ),
            )
        }
    }

    Box(modifier = modifier.then(Modifier.size(32.dp))) {
        Surface(
            shape = if (loading) RoundedCornerShape(50) else MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxSize(),
            onClick = {
                onClick?.invoke()
                if (onUpdate != null) showPicker = true
            },
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when (value) {
                    is Avatar.Image -> AsyncImage(
                        model = value.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    is Avatar.Emoji -> Text(
                        text = value.content,
                        autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 30.sp),
                        lineHeight = 0.8.em,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(5.dp),
                    )
                    Avatar.Dummy -> ProceduralAvatar(name, Modifier.fillMaxSize())
                }
            }
        }
        if (onUpdate != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Edit03,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp).padding(1.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(Res.string.avatar_change_avatar)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showPicker = false
                            imagePicker.launch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.avatar_pick_image)) }
                    Button(
                        onClick = {
                            showPicker = false
                            input = ""
                            showTextInput = AvatarInput.Emoji
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.avatar_pick_emoji)) }
                    Button(
                        onClick = {
                            showPicker = false
                            input = ""
                            showTextInput = AvatarInput.Url
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.avatar_input_url)) }
                    Button(
                        onClick = {
                            showPicker = false
                            onUpdate?.invoke(Avatar.Dummy)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.avatar_reset)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(Res.string.avatar_cancel))
                }
            },
        )
    }

    showTextInput?.let { inputType ->
        AlertDialog(
            onDismissRequest = { showTextInput = null },
            title = { Text(stringResource(Res.string.avatar_url_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(Res.string.avatar_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = input.trim()
                        if (value.isNotEmpty()) {
                            onUpdate?.invoke(
                                when (inputType) {
                                    AvatarInput.Emoji -> Avatar.Emoji(value)
                                    AvatarInput.Url -> Avatar.Image(value)
                                },
                            )
                            showTextInput = null
                        }
                    },
                ) { Text(stringResource(Res.string.avatar_url_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTextInput = null }) {
                    Text(stringResource(Res.string.avatar_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProceduralAvatar(name: String, modifier: Modifier = Modifier) {
    val (fromColor, toColor) = remember(name) { avatarColors(name.ifBlank { "?" }) }
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(fromColor, toColor),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
    }
}

private fun avatarColors(name: String): Pair<Color, Color> {
    val hue = abs(name.fold(0) { hash, character -> hash * 31 + character.code } % 360).toFloat()
    return hslToColor(hue, 0.65f, 0.55f) to hslToColor((hue + 120f) % 360f, 0.65f, 0.55f)
}

private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val sector = hue / 60f
    val intermediate = chroma * (1f - abs(sector % 2f - 1f))
    val (red, green, blue) = when {
        sector < 1f -> Triple(chroma, intermediate, 0f)
        sector < 2f -> Triple(intermediate, chroma, 0f)
        sector < 3f -> Triple(0f, chroma, intermediate)
        sector < 4f -> Triple(0f, intermediate, chroma)
        sector < 5f -> Triple(intermediate, 0f, chroma)
        else -> Triple(chroma, 0f, intermediate)
    }
    val offset = lightness - chroma / 2f
    return Color(red + offset, green + offset, blue + offset)
}

private enum class AvatarInput { Emoji, Url }
