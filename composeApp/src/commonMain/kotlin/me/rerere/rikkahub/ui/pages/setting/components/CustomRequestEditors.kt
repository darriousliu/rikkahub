package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.assistant_page_add_body
import me.rerere.rikkahub.generated.resources.assistant_page_add_header
import me.rerere.rikkahub.generated.resources.assistant_page_body_key
import me.rerere.rikkahub.generated.resources.assistant_page_body_value
import me.rerere.rikkahub.generated.resources.assistant_page_custom_bodies
import me.rerere.rikkahub.generated.resources.assistant_page_custom_headers
import me.rerere.rikkahub.generated.resources.assistant_page_delete_body
import me.rerere.rikkahub.generated.resources.assistant_page_delete_header
import me.rerere.rikkahub.generated.resources.assistant_page_header_name
import me.rerere.rikkahub.generated.resources.assistant_page_header_value
import me.rerere.rikkahub.generated.resources.assistant_page_invalid_json
import me.rerere.rikkahub.ui.components.ui.CardGroup
import org.jetbrains.compose.resources.stringResource

private val requestBodyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

@Composable
fun CustomHeaders(headers: List<CustomHeader>, onUpdate: (List<CustomHeader>) -> Unit) {
    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.assistant_page_custom_headers))
        Spacer(Modifier.height(8.dp))

        headers.forEachIndexed { index, header ->
            var headerName by remember(header.name) { mutableStateOf(header.name) }
            var headerValue by remember(header.value) { mutableStateOf(header.value) }
            CardGroup {
                item(
                    supportingContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = headerName,
                                onValueChange = {
                                    headerName = it
                                    onUpdate(headers.replaceAt(index, header.copy(name = it.trim())))
                                },
                                label = { Text(stringResource(Res.string.assistant_page_header_name)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = headerValue,
                                onValueChange = {
                                    headerValue = it
                                    onUpdate(headers.replaceAt(index, header.copy(value = it.trim())))
                                },
                                label = { Text(stringResource(Res.string.assistant_page_header_value)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onUpdate(headers.filterIndexed { itemIndex, _ -> itemIndex != index }) }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(Res.string.assistant_page_delete_header),
                            )
                        }
                    },
                    headlineContent = {},
                )
            }
        }

        Button(
            onClick = { onUpdate(headers + CustomHeader("", "")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(HugeIcons.Add01, contentDescription = stringResource(Res.string.assistant_page_add_header))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.assistant_page_add_header))
        }
    }
}

@Composable
fun CustomBodies(customBodies: List<CustomBody>, onUpdate: (List<CustomBody>) -> Unit) {
    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.assistant_page_custom_bodies))
        Spacer(Modifier.height(8.dp))

        customBodies.forEachIndexed { index, body ->
            var bodyKey by remember(body.key) { mutableStateOf(body.key) }
            var bodyValue by remember(body.value) {
                mutableStateOf(requestBodyJson.encodeToString(JsonElement.serializer(), body.value))
            }
            var jsonParseError by remember { mutableStateOf<String?>(null) }
            CardGroup {
                item(
                    supportingContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = bodyKey,
                                onValueChange = {
                                    bodyKey = it
                                    onUpdate(customBodies.replaceAt(index, body.copy(key = it.trim())))
                                },
                                label = { Text(stringResource(Res.string.assistant_page_body_key)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = bodyValue,
                                onValueChange = { value ->
                                    bodyValue = value
                                    runCatching { requestBodyJson.parseToJsonElement(value) }
                                        .onSuccess {
                                            onUpdate(customBodies.replaceAt(index, body.copy(value = it)))
                                            jsonParseError = null
                                        }
                                        .onFailure {
                                            jsonParseError = it.message?.take(100).orEmpty()
                                        }
                                },
                                label = { Text(stringResource(Res.string.assistant_page_body_value)) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = jsonParseError != null,
                                supportingText = jsonParseError?.let { error ->
                                    {
                                        Text(stringResource(Res.string.assistant_page_invalid_json, error))
                                    }
                                },
                                minLines = 3,
                                maxLines = 5,
                                textStyle = LocalTextStyle.current.merge(fontFamily = FontFamily.Monospace),
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                onUpdate(customBodies.filterIndexed { itemIndex, _ -> itemIndex != index })
                            },
                        ) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(Res.string.assistant_page_delete_body),
                            )
                        }
                    },
                    headlineContent = {},
                )
            }
        }

        Button(
            onClick = { onUpdate(customBodies + CustomBody("", JsonPrimitive(""))) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(HugeIcons.Add01, contentDescription = stringResource(Res.string.assistant_page_add_body))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.assistant_page_add_body))
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().apply { this[index] = value }
