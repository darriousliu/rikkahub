package me.rerere.rikkahub.ui.components.richtext

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.code_block_preview
import me.rerere.rikkahub.generated.resources.mermaid_export
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue

/**
 * Exercises the same [MarkdownBlock] route used by assistant chat messages with entirely local data.
 *
 * The test deliberately does not create an assistant, conversation, provider, database, or network request.
 * `native-diagram-*` and `code-block-source-toggle` are implementation test tags: they make the rendered
 * diagram state observable without coupling the assertions to Coil or Android WebView internals.
 */
@RunWith(AndroidJUnit4::class)
class NativeDiagramChatTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun offlineChatMessage_rendersNativeDiagramsAndKeepsControlsAndHtmlAvailableInBothThemes() {
        val dark = mutableStateOf(false)
        val codePreview = runBlocking { getString(Res.string.code_block_preview) }
        val export = runBlocking { getString(Res.string.mermaid_export) }

        compose.setContent {
            val toaster = rememberToasterState()
            CompositionLocalProvider(
                LocalSettings provides Settings(),
                LocalNavController provides Navigator(mutableListOf()),
                LocalToaster provides toaster,
            ) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Toaster(state = toaster)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        MarkdownBlock(content = FIXTURE_MARKDOWN)
                    }
                }
            }
        }

        assertRenderedDiagramState()
        assertInlineHtmlWebViewIsInteractive()

        // Source switching is local to its code block: returning to the preview must not remove other diagrams.
        compose.onAllNodesWithTag(SOURCE_TOGGLE_TAG, useUnmergedTree = true)[0].performScrollTo()
        compose.onAllNodesWithTag(SOURCE_TOGGLE_TAG, useUnmergedTree = true)[0].performClick()
        compose.onNodeWithText("graph TD", substring = true).assertIsDisplayed()
        compose.onAllNodesWithTag(SOURCE_TOGGLE_TAG, useUnmergedTree = true)[0].performClick()
        assertRenderedDiagramState()

        val viewport = compose.onAllNodesWithTag(VIEWPORT_TAG, useUnmergedTree = true)[0]
        val diagram = compose.onAllNodesWithTag(IMAGE_TAG, useUnmergedTree = true)[0]
        viewport.performScrollTo().assertHeightIsEqualTo(200.dp)
        val scroll = diagram.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("A tall Mermaid must overflow its fixed viewport when fitted to its width", scroll.maxValue() > 0f)
        saveViewportEvidence("mermaid-fit-width.png")

        // Tapping and scrolling the image must not open the full-screen preview.
        viewport.performTouchInput { click(center) }
        compose.onAllNodesWithTag(PREVIEW_TAG, useUnmergedTree = true).assertCountEquals(0)
        val initialScroll = scroll.value()
        viewport.performTouchInput { swipeUp() }
        compose.waitUntil(TIMEOUT_MILLIS) {
            scroll.value() > initialScroll
        }
        viewport.assertHeightIsEqualTo(200.dp)
        compose.onAllNodesWithTag(PREVIEW_TAG, useUnmergedTree = true).assertCountEquals(0)
        saveViewportEvidence("mermaid-scrolled.png")

        // Both the code-block header and the original bottom-right button open the native preview.
        for (index in 0..1) {
            compose.onAllNodesWithContentDescription(codePreview, useUnmergedTree = true)[index].performScrollTo()
            compose.onAllNodesWithContentDescription(codePreview, useUnmergedTree = true)[index].performClick()
            compose.waitUntil(TIMEOUT_MILLIS) {
                compose.onAllNodesWithTag(PREVIEW_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithTag(PREVIEW_TAG, useUnmergedTree = true).assertCountEquals(1)
            compose.onNodeWithContentDescription("Close", useUnmergedTree = true).performClick()
        }

        // Export is intentionally asserted, not invoked: a platform file-saver is outside this isolated UI host.
        compose.onAllNodesWithContentDescription(export, useUnmergedTree = true).assertCountEquals(5)

        compose.runOnIdle { dark.value = true }
        assertRenderedDiagramState()
        assertInlineHtmlWebViewIsInteractive()
    }

    private fun saveViewportEvidence(name: String) {
        if (InstrumentationRegistry.getArguments().getString("nativeDiagramEvidence") != "true") return
        val bitmap = compose.onAllNodesWithTag(VIEWPORT_TAG, useUnmergedTree = true)[0]
            .captureToImage().asAndroidBitmap()
        val evidenceDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "native-diagram-layout-evidence",
        )
        check(evidenceDir.mkdirs() || evidenceDir.isDirectory) { "Unable to create $evidenceDir" }
        File(evidenceDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertRenderedDiagramState() {
        try {
            compose.waitUntil(TIMEOUT_MILLIS) {
                compose.onAllNodes(
                    hasTestTag(IMAGE_TAG) and hasStateDescription(READY_STATE),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size == 3 &&
                    compose.onAllNodesWithTag(ERROR_TAG, useUnmergedTree = true).fetchSemanticsNodes().size == 2
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Native fixture semantics on timeout:\n${compose.onRoot(useUnmergedTree = true).printToString()}", error)
        }
        // All five diagram blocks have a native image host. The two invalid sources also expose an error
        // tag while the three valid sources decode into visible Coil/Resvg images.
        compose.onAllNodesWithTag(IMAGE_TAG, useUnmergedTree = true).assertCountEquals(5)
        compose.onAllNodes(
            hasTestTag(IMAGE_TAG) and hasStateDescription(READY_STATE),
            useUnmergedTree = true,
        ).assertCountEquals(3)
        compose.onAllNodesWithTag(ERROR_TAG, useUnmergedTree = true).assertCountEquals(2)
        // Five diagram blocks plus the preserved HTML block retain independent source toggles.
        compose.onAllNodesWithTag(SOURCE_TOGGLE_TAG, useUnmergedTree = true).assertCountEquals(6)
    }

    private fun assertInlineHtmlWebViewIsInteractive() {
        compose.waitUntil(TIMEOUT_MILLIS) { findWebViews(compose.activity.window.decorView).isNotEmpty() }
        val body = evaluateHtml("document.body.textContent")
        assertTrue("Inline HTML body must remain in Android WebView: $body", body.contains(HTML_SENTINEL))
        val clicked = evaluateHtml(
            "document.getElementById('fixture-action').click();document.getElementById('fixture-state').textContent",
        )
        assertTrue("Inline HTML JavaScript action must run: $clicked", clicked.contains(HTML_CLICKED))
    }

    private fun evaluateHtml(script: String): String {
        val result = AtomicReference<String>()
        val finished = CountDownLatch(1)
        compose.runOnIdle {
            findWebViews(compose.activity.window.decorView).single().evaluateJavascript(script) {
                result.set(it)
                finished.countDown()
            }
        }
        assertTrue("Timed out evaluating inline HTML", finished.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        return requireNotNull(result.get())
    }

    private fun findWebViews(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> buildList {
            for (index in 0 until view.childCount) addAll(findWebViews(view.getChildAt(index)))
        }
        else -> emptyList()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val IMAGE_TAG = "native-diagram-image"
        const val VIEWPORT_TAG = "native-diagram-viewport"
        const val ERROR_TAG = "native-diagram-error"
        const val PREVIEW_TAG = "native-diagram-preview"
        const val SOURCE_TOGGLE_TAG = "code-block-source-toggle"
        const val READY_STATE = "Ready"
        const val HTML_SENTINEL = "HTML fixture remains inline"
        const val HTML_CLICKED = "HTML fixture clicked"

        val FIXTURE_MARKDOWN = """
            # Native diagram fixture

            ```mermaid
            graph TD
              Start[Start] --> Validate{Valid?}
              Validate -->|yes| Finish[Finish]
              Validate -->|no| Start
            ```

            ```mermaid
            sequenceDiagram
              participant Client
              participant Server
              Client->>Server: fixture request
              Server-->>Client: fixture response
            ```

            ```svg
            <svg viewBox="0 0 160 80" xmlns="http://www.w3.org/2000/svg">
              <rect x="4" y="4" width="152" height="72" rx="8" fill="#dcecff" stroke="#235a97"/>
              <path d="M20 54 L76 22 L140 54" fill="none" stroke="#235a97" stroke-width="4"/>
              <text x="80" y="68" text-anchor="middle">SVG fixture</text>
            </svg>
            ```

            ```html
            <!doctype html>
            <html><body>
              <button id="fixture-action" onclick="document.getElementById('fixture-state').textContent='$HTML_CLICKED'">$HTML_SENTINEL</button>
              <output id="fixture-state">ready</output>
            </body></html>
            ```

            ```mermaid
            bad diagram
            ```

            ```svg
            <svg>
            ```
        """.trimIndent()
    }
}
