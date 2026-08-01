package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.readString
import kotlin.io.encoding.Base64
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.assistant_importer_import_failed
import me.rerere.rikkahub.generated.resources.assistant_importer_import_tavern_json
import me.rerere.rikkahub.generated.resources.assistant_importer_import_tavern_png
import me.rerere.rikkahub.generated.resources.assistant_importer_importing
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.platform.FileStoreArea
import me.rerere.rikkahub.platform.createCharacterCardMetadataReader
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import org.jetbrains.compose.resources.stringResource

@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    onUpdate: (Assistant) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val fileStore = remember { FileKitPlatformFileStore() }
    var isLoading by remember { mutableStateOf(false) }

    fun import(file: PlatformFile, png: Boolean) {
        isLoading = true
        scope.launch {
            runCatching {
                val background = if (png) {
                    fileStore.copyIntoSandbox(file, FileStoreArea.IMAGES).getOrThrow().file.path
                } else {
                    null
                }
                val jsonText = if (png) {
                    val encoded = createCharacterCardMetadataReader().read(file.readBytes()).getOrThrow()
                    Base64.Default
                        .withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
                        .decode(encoded.filterNot(Char::isWhitespace))
                        .decodeToString()
                } else {
                    file.readString()
                }
                parseTavernAssistant(Json.parseToJsonElement(jsonText).jsonObject, background)
            }.onSuccess(onUpdate)
                .onFailure {
                    toaster.show(it.message ?: "Assistant import failed", type = ToastType.Error)
                }
            isLoading = false
        }
    }

    val pngPicker = rememberFilePickerLauncher(type = FileKitType.File("png")) { file ->
        file?.let { import(it, png = true) }
    }
    val jsonPicker = rememberFilePickerLauncher(type = FileKitType.File("json")) { file ->
        file?.let { import(it, png = false) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = pngPicker::launch, enabled = !isLoading) {
                AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
                Text(
                    if (isLoading) {
                        stringResource(Res.string.assistant_importer_importing)
                    } else {
                        stringResource(Res.string.assistant_importer_import_tavern_png)
                    },
                )
            }
            OutlinedButton(onClick = jsonPicker::launch, enabled = !isLoading) {
                AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
                Text(
                    if (isLoading) {
                        stringResource(Res.string.assistant_importer_importing)
                    } else {
                        stringResource(Res.string.assistant_importer_import_tavern_json)
                    },
                )
            }
        }
    }
}

internal fun parseTavernAssistant(json: JsonObject, background: String?): Assistant {
    val spec = json["spec"]?.jsonPrimitive?.contentOrNull ?: error("Missing character card spec")
    require(spec == "chara_card_v2" || spec == "chara_card_v3") { "Unsupported character card spec: $spec" }
    val data = json["data"]?.jsonObject ?: error("Missing character card data")
    val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull ?: error("Missing character name")
    val firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull
    val system = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull
    val description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull
    val personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull
    val scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull
    val prompt = buildString {
        appendLine("You are roleplaying as $name.")
        appendLine()
        if (!system.isNullOrBlank()) {
            appendLine(system)
            appendLine()
        }
        appendLine("## Description of the character")
        appendLine(description ?: "Empty")
        appendLine()
        appendLine("## Personality of the character")
        appendLine(personality ?: "Empty")
        appendLine()
        appendLine("## Scenario")
        append(scenario ?: "Empty")
    }
    return Assistant(
        name = name,
        presetMessages = firstMessage?.let { listOf(UIMessage.assistant(it)) }.orEmpty(),
        systemPrompt = prompt,
        background = background,
    )
}
