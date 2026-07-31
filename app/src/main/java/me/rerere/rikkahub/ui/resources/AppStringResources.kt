package me.rerere.rikkahub.ui.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource as composeStringResource
import java.util.Locale

private const val AVAILABLE_VARIABLES_KEY = "assistant_page_available_variables"
private const val MESSAGE_JUMPER_POSITION_DESCRIPTION_KEY =
    "setting_display_page_message_jumper_position_desc"
private val CONSECUTIVE_ASCII_SPACES = Regex(" +")

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
 * This intentionally does not collapse whitespace globally: doing so would corrupt explicit line breaks and
 * intentional spacing in unrelated resources.
 */
internal fun decodeAndroidResourceText(key: String, template: String): String {
    val isQuoted = template.length >= 2 && template.first() == '"' && template.last() == '"'
    var decoded = if (isQuoted) template.substring(1, template.lastIndex) else template

    decoded = decoded
        .replace("\\'", "'")
        .replace("\\\"", "\"")

    if (!isQuoted && key == AVAILABLE_VARIABLES_KEY) {
        decoded = decoded.trim()
    }
    if (!isQuoted && key == MESSAGE_JUMPER_POSITION_DESCRIPTION_KEY) {
        decoded = decoded.replace(CONSECUTIVE_ASCII_SPACES, " ")
    }

    return decoded
}

internal fun formatAndroidResourceText(
    template: String,
    locale: Locale,
    vararg formatArgs: Any,
): String = String.format(locale, template, *formatArgs)
