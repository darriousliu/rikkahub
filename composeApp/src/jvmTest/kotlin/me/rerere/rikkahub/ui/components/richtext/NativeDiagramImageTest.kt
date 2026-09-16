package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.runBlocking
import me.rerere.mermaid.renderMermaidSvg
import me.rerere.rikkahub.platform.createDesktopImageLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NativeDiagramImageTest {
    @Test
    fun decodesSvgThroughResvgAndCachesTheBitmap() = runBlocking {
        val loader = createDesktopImageLoader(PlatformContext.INSTANCE)
        try {
            val diagram = DiagramSource(
                """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="50"><rect width="100" height="50" fill="#e6141e"/></svg>""",
                "svg",
            )
            val request = diagramImageRequest(PlatformContext.INSTANCE, diagram).size(200, 200).build()
            val result = assertIs<SuccessResult>(loader.execute(request))
            val bitmap = result.image.toBitmap()
            assertEquals(0xffe6141e.toInt(), bitmap.getColor(bitmap.width / 2, bitmap.height / 2))
            assertEquals(DataSource.MEMORY_CACHE, assertIs<SuccessResult>(loader.execute(request)).dataSource)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun mermaidThemeUnicodeMathAndSourceConfigReachTheNativeDecoder() = runBlocking {
        val loader = createDesktopImageLoader(PlatformContext.INSTANCE)
        try {
            // Frontmatter must remain at the start of the original source.
            val source = "---\ntitle: 原生图表\n---\nflowchart LR\nA[你好] --> B[\"\$\$x^2\$\$\"]"
            val light = DiagramSource(source, "mermaid", mermaidThemeConfig(lightColorScheme()))
            val dark = DiagramSource(source, "mermaid", mermaidThemeConfig(darkColorScheme()))
            assertNotEquals(light.cacheKey, dark.cacheKey)
            val svg = checkNotNull(renderMermaidSvg(source, light.config))
            assertTrue(svg.contains("你好"))
            assertTrue(svg.contains("原生图表"))
            assertTrue(!svg.contains("foreignObject"))
            assertTrue(!svg.contains("\$\$x^2\$\$"))
            for (diagram in listOf(light, dark)) {
                val result = assertIs<SuccessResult>(loader.execute(
                    diagramImageRequest(PlatformContext.INSTANCE, diagram).size(600, 400).build(),
                ))
                assertTrue(result.image.width > 0 && result.image.height > 0)
            }
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun invalidSourcesProduceCoilErrorsInsteadOfCrashingTheComposition() = runBlocking {
        val loader = createDesktopImageLoader(PlatformContext.INSTANCE)
        try {
            for (diagram in listOf(DiagramSource("bad diagram", "mermaid"), DiagramSource("<svg>", "svg"))) {
                assertIs<ErrorResult>(loader.execute(
                    diagramImageRequest(PlatformContext.INSTANCE, diagram).size(200, 200).build(),
                ))
            }
        } finally {
            loader.shutdown()
        }
    }
}
