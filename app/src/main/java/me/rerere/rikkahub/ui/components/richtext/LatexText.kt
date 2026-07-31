package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import ru.noties.jlatexmath.JLatexMathDrawable

fun assumeLatexSize(
    latex: String,
    fontSize: Float,
    displayMode: LatexDisplayMode = LatexDisplayMode.Inline,
): LatexSize {
    return runCatching {
        JLatexMathDrawable.builder(parseLatexFormula(latex, displayMode).source)
            .textSize(fontSize)
            .padding(0)
            .build()
            .bounds
            .let { LatexSize(width = it.width().toFloat(), height = it.height().toFloat()) }
    }.getOrElse { LatexSize(width = 0f, height = 0f) }
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
    val style = style.merge(
        fontSize = fontSize,
        color = color
    )
    val density = LocalDensity.current

    val formula = remember(latex, displayMode) { parseLatexFormula(latex, displayMode) }
    val drawable = remember(formula, fontSize, style) {
        runCatching {
            with(density) {
                getLatexDrawable(
                    latex = formula.source,
                    fontSize = fontSize.toPx(),
                    color = style.color.toArgb(),
                    background = style.background.toArgb()
                )
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    if (drawable != null) {
        with(density) {
            Canvas(
                modifier = modifier
                    .size(
                        width = drawable.bounds.width().toDp(),
                        height = drawable.bounds.height().toDp()
                    )
            ) {
                drawable.draw(drawContext.canvas.nativeCanvas)
            }
        }
    } else {
        Text(
            text = latex,
            style = style,
            modifier = modifier
        )
    }
}

private fun getLatexDrawable(
    latex: String,
    fontSize: Float,
    color: Int,
    background: Int
): JLatexMathDrawable? {
    return runCatching {
        JLatexMathDrawable.builder(latex)
            .textSize(fontSize)
            .color(color)
            .background(background)
            .padding(0)
            .align(JLatexMathDrawable.ALIGN_LEFT)
            .build()
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()
}

class LatexRenderSegment internal constructor(
    val source: String,
    internal val drawable: JLatexMathDrawable,
) {
    val size = LatexSize(
        width = drawable.bounds.width().toFloat(),
        height = drawable.bounds.height().toFloat(),
    )
}

/**
 * 将一条行内公式按顶层运算符水平拆分为多段，
 * 以便在文本流中换行，避免单体公式超出可用宽度。
 * 拆分失败时返回空列表，调用方需自行回退。
 */
fun splitLatex(
    latex: String,
    maxWidthPx: Float,
    fontSize: Float,
    color: Int
): List<LatexRenderSegment> {
    return runCatching {
        val source = parseLatexFormula(latex).source
        when (
            val result = splitLatexFormula(
                source = source,
                maxWidthPx = maxWidthPx,
                measureWidth = { segment ->
                    try {
                        JLatexMathDrawable.builder(segment)
                            .textSize(fontSize)
                            .build()
                            .intrinsicWidth
                            .toFloat()
                    } catch (_: Exception) {
                        Float.MAX_VALUE
                    }
                },
            )
        ) {
            is LatexSplitResult.Fallback -> emptyList()
            is LatexSplitResult.Segments -> result.values.map { segment ->
                LatexRenderSegment(
                    source = segment,
                    drawable = JLatexMathDrawable.builder(segment)
                        .textSize(fontSize)
                        .color(color)
                        .build(),
                )
            }
        }
    }.onFailure {
        it.printStackTrace()
    }.getOrElse { emptyList() }
}

@Composable
fun LatexDrawable(
    segment: LatexRenderSegment,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val drawable = segment.drawable
    with(density) {
        Canvas(
            modifier = modifier.size(
                width = drawable.bounds.width().toDp(),
                height = drawable.bounds.height().toDp()
            )
        ) {
            drawable.draw(drawContext.canvas.nativeCanvas)
        }
    }
}
