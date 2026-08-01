package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.takeOrElse
import io.ratex.DisplayList
import io.ratex.RaTeXEngine
import io.ratex.compose.RaTeX
import io.ratex.compose.rememberBlockingRaTeXDisplayList
import io.ratex.compose.rememberRaTeXDisplayList
import io.ratex.measure
import kotlin.math.ceil

fun assumeLatexSize(
    latex: String,
    fontSize: Float,
    displayMode: LatexDisplayMode = LatexDisplayMode.Inline,
): LatexSize {
    val formula = parseLatexFormula(latex, displayMode)
    return parseLatexBlocking(
        source = formula.source,
        fontSizePx = fontSize,
        displayMode = formula.displayMode,
        color = Color.Black,
    )?.size ?: LatexSize(width = 0f, height = 0f)
}

@Composable
fun LatexText(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    displayMode: LatexDisplayMode = LatexDisplayMode.Inline,
) {
    val localTextStyle = LocalTextStyle.current
    val contentColor = LocalContentColor.current
    val mergedStyle = style.merge(fontSize = fontSize, color = color)
    val resolvedFontSize = mergedStyle.fontSize.takeOrElse { localTextStyle.fontSize }
    val resolvedColor = if (mergedStyle.color == Color.Unspecified) contentColor else mergedStyle.color
    val formula = remember(latex, displayMode) { parseLatexFormula(latex, displayMode) }

    val displayListResult: Result<DisplayList?>? = when (formula.displayMode) {
        LatexDisplayMode.Inline -> rememberBlockingRaTeXDisplayList(
            latex = formula.source,
            displayMode = false,
            color = resolvedColor,
        )

        LatexDisplayMode.Display -> {
            val result by rememberRaTeXDisplayList(
                latex = formula.source,
                displayMode = true,
                color = resolvedColor,
            )
            result
        }
    }
    val displayList = displayListResult?.getOrNull()

    if (displayList != null) {
        val renderModifier = if (mergedStyle.background == Color.Unspecified) {
            modifier
        } else {
            modifier.background(mergedStyle.background)
        }
        RaTeX(
            displayList = displayList,
            modifier = renderModifier,
            fontSize = resolvedFontSize,
        )
    } else {
        Text(
            text = latex,
            style = mergedStyle,
            modifier = modifier,
        )
    }
}

class LatexRenderSegment internal constructor(
    val source: String,
    internal val displayList: DisplayList,
    internal val fontSizePx: Float,
    val size: LatexSize,
)

/**
 * 将一条行内公式按顶层运算符水平拆分为多段，
 * 以便在文本流中换行，避免单体公式超出可用宽度。
 * 拆分失败时返回空列表，调用方需自行回退。
 */
fun splitLatex(
    latex: String,
    maxWidthPx: Float,
    fontSize: Float,
    color: Int,
): List<LatexRenderSegment> {
    val source = parseLatexFormula(latex).source
    val parsedSegments = mutableMapOf<String, ParsedLatex?>()
    fun parseSegment(segment: String): ParsedLatex? = if (parsedSegments.containsKey(segment)) {
        parsedSegments[segment]
    } else {
        parseLatexBlocking(
            source = segment,
            fontSizePx = fontSize,
            displayMode = LatexDisplayMode.Inline,
            color = Color(color),
        ).also { parsedSegments[segment] = it }
    }

    return when (
        val result = splitLatexFormula(
            source = source,
            maxWidthPx = maxWidthPx,
            measureWidth = { segment -> parseSegment(segment)?.size?.width ?: Float.MAX_VALUE },
        )
    ) {
        is LatexSplitResult.Fallback -> emptyList()
        is LatexSplitResult.Segments -> result.values.map { segment ->
            val parsed = parseSegment(segment) ?: return emptyList()
            LatexRenderSegment(
                source = segment,
                displayList = parsed.displayList,
                fontSizePx = fontSize,
                size = parsed.size,
            )
        }
    }
}

@Composable
fun LatexDrawable(
    segment: LatexRenderSegment,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    with(density) {
        RaTeX(
            displayList = segment.displayList,
            modifier = modifier.size(
                width = segment.size.width.toDp(),
                height = segment.size.height.toDp(),
            ),
            fontSize = segment.fontSizePx.toSp(),
        )
    }
}

private data class ParsedLatex(
    val displayList: DisplayList,
    val size: LatexSize,
)

private fun parseLatexBlocking(
    source: String,
    fontSizePx: Float,
    displayMode: LatexDisplayMode,
    color: Color,
): ParsedLatex? = runCatching {
    val displayList = RaTeXEngine.parseBlocking(
        latex = source,
        displayMode = displayMode == LatexDisplayMode.Display,
        color = color,
    )
    val measured = displayList.measure(fontSizePx)
    ParsedLatex(
        displayList = displayList,
        size = LatexSize(
            width = ceil(measured.widthPx.toDouble()).toFloat(),
            height = ceil(measured.totalHeightPx.toDouble()).toFloat(),
        ),
    )
}.getOrNull()
