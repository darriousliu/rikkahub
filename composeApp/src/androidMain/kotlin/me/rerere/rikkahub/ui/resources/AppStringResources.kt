package me.rerere.rikkahub.ui.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource as composeStringResource
import java.util.Locale

private const val WEB_SERVER_ADDRESS_NOTE_DESCRIPTION_KEY =
    "setting_page_web_server_address_note_desc"
private val PHYSICAL_NEWLINE_PREFIXES = listOf(
    "LANアドレス:",
    "LAN 주소:",
    "LAN-адрес:",
    "局域网地址：",
)

/**
 * Reads a Compose Multiplatform string while preserving Android's AAPT string semantics.
 */
@Composable
fun stringResource(id: StringResource): String {
    return decodeAndroidResourceText(
        key = id.key,
        template = composeStringResource(id),
    )
}

/**
 * Formats a Compose Multiplatform string with the locale Android currently exposes to Compose.
 */
@Composable
fun stringResource(id: StringResource, vararg formatArgs: Any): String {
    val locale = LocalResources.current.configuration.locales[0]
    val template = decodeAndroidResourceText(
        key = id.key,
        template = composeStringResource(id),
    )
    return formatAndroidResourceText(template, locale, *formatArgs)
}

/**
 * Reproduces the AAPT transformations needed by the current app string catalog.
 *
 * Unquoted ASCII spaces follow AAPT collapsing rules. Escape-derived line breaks remain intact; the one catalog
 * entry whose XML contains physical newlines is identified narrowly because Compose loses that source distinction.
 */
internal fun decodeAndroidResourceText(key: String, template: String): String {
    val collapsePhysicalNewlines = key == WEB_SERVER_ADDRESS_NOTE_DESCRIPTION_KEY &&
        PHYSICAL_NEWLINE_PREFIXES.any(template::startsWith)
    val result = StringBuilder(template.length)
    var quoted = false
    var pendingWhitespace = false
    var index = 0

    fun flushWhitespace() {
        if (pendingWhitespace && result.isNotEmpty()) {
            result.append(' ')
        }
        pendingWhitespace = false
    }

    while (index < template.length) {
        val character = template[index]
        val escapedCharacter = template.getOrNull(index + 1)
        if (character == '\\' && (escapedCharacter == '\'' || escapedCharacter == '"')) {
            flushWhitespace()
            result.append(escapedCharacter)
            index += 2
            continue
        }

        when {
            character == '"' -> {
                if (!quoted) {
                    flushWhitespace()
                }
                quoted = !quoted
            }

            !quoted && (character == ' ' || collapsePhysicalNewlines && character == '\n') -> {
                pendingWhitespace = true
            }

            else -> {
                flushWhitespace()
                result.append(character)
            }
        }
        index++
    }

    return result.toString()
}

internal fun formatAndroidResourceText(
    template: String,
    locale: Locale,
    vararg formatArgs: Any,
): String = String.format(locale, template, *formatArgs)
