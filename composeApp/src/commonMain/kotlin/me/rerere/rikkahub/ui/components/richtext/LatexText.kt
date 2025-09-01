package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import io.github.darriousliu.katex.core.MTMathView
import io.github.darriousliu.katex.mathdisplay.parse.MTLineStyle
import io.github.darriousliu.katex.mathdisplay.parse.MTMathListBuilder
import io.github.darriousliu.katex.mathdisplay.render.MTFontManager
import io.github.darriousliu.katex.mathdisplay.render.MTTypesetter

fun assumeLatexSize(latex: String, fontSize: Float): Rect {
    return runCatching {
        val mathList = MTMathListBuilder.buildFromString(latex)
        require(mathList != null)
        val displayList = MTTypesetter.createLineForMathList(
            mathList,
            MTFontManager.defaultFont().copyFontWithSize(fontSize),
            MTLineStyle.KMTLineStyleDisplay
        )
        Rect(0f, 0f, displayList.width + 10, displayList.ascent + displayList.descent + 10)
    }.getOrElse { Rect(0f, 0f, 0f, 0f) }
}

@Composable
fun LatexText(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val style = style.merge(
        fontSize = fontSize,
        color = color
    )

    val mathList = remember(latex, fontSize, style) {
        runCatching {
            val mathList = MTMathListBuilder.buildFromString(processLatex(latex))
            mathList
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    if (mathList != null) {
        MTMathView(
            mathList = mathList,
            modifier = modifier,
            fontSize = fontSize,
            textColor = color,
        )
    } else {
        Text(
            text = latex,
            style = style,
            modifier = modifier
        )
    }
}

private val inlineDollarRegex = Regex("""^\$(.*?)\$""")
private val displayDollarRegex = Regex("""^\$\$(.*?)\$\$""")
private val inlineParenRegex = Regex("""^\\\((.*?)\\\)""")
private val displayBracketRegex = Regex("""^\\\[(.*?)\\]""")

private fun processLatex(latex: String): String {
    val trimmed = latex.trim()
    return when {
        displayDollarRegex.matches(trimmed) ->
            displayDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        inlineDollarRegex.matches(trimmed) ->
            inlineDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        displayBracketRegex.matches(trimmed) ->
            displayBracketRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        inlineParenRegex.matches(trimmed) ->
            inlineParenRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        else -> trimmed
    }
}
