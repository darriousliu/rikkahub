package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.utils.toCssHex

@Composable
fun Mermaid(code: String, modifier: Modifier = Modifier) {
    DiagramPreview(diagram = rememberDiagramSource(code, "mermaid"), modifier = modifier)
}

internal fun mermaidThemeConfig(colorScheme: ColorScheme): String = buildJsonObject {
    put("theme", "base")
    put("themeVariables", buildJsonObject {
        put("primaryColor", colorScheme.primaryContainer.toCssHex())
        put("primaryTextColor", colorScheme.onPrimaryContainer.toCssHex())
        put("primaryBorderColor", colorScheme.primaryContainer.toCssHex())
        put("secondaryColor", colorScheme.secondaryContainer.toCssHex())
        put("secondaryTextColor", colorScheme.onSecondaryContainer.toCssHex())
        put("secondaryBorderColor", colorScheme.secondaryContainer.toCssHex())
        put("tertiaryColor", colorScheme.tertiaryContainer.toCssHex())
        put("tertiaryTextColor", colorScheme.onTertiaryContainer.toCssHex())
        put("tertiaryBorderColor", colorScheme.tertiaryContainer.toCssHex())
        put("background", colorScheme.background.toCssHex())
        put("mainBkg", colorScheme.primaryContainer.toCssHex())
        put("secondBkg", colorScheme.secondaryContainer.toCssHex())
        put("lineColor", colorScheme.onBackground.toCssHex())
        put("textColor", colorScheme.onBackground.toCssHex())
        put("nodeBkg", colorScheme.surface.toCssHex())
        put("nodeBorder", colorScheme.primaryContainer.toCssHex())
        put("clusterBkg", colorScheme.surface.toCssHex())
        put("clusterBorder", colorScheme.primaryContainer.toCssHex())
        put("actorBorder", colorScheme.primaryContainer.toCssHex())
        put("actorBkg", colorScheme.surface.toCssHex())
        put("actorTextColor", colorScheme.onBackground.toCssHex())
        put("actorLineColor", colorScheme.primaryContainer.toCssHex())
        put("taskBorderColor", colorScheme.primaryContainer.toCssHex())
        put("taskBkgColor", colorScheme.primaryContainer.toCssHex())
        put("taskTextLightColor", colorScheme.onPrimaryContainer.toCssHex())
        put("taskTextDarkColor", colorScheme.onBackground.toCssHex())
        put("labelColor", colorScheme.onBackground.toCssHex())
        put("errorBkgColor", colorScheme.error.toCssHex())
        put("errorTextColor", colorScheme.onError.toCssHex())
    })
}.toString()
