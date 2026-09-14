@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextRange
import com.dokar.sonner.rememberToasterState
import dev.chrisbanes.haze.rememberHazeState
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.testFilesManager
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.deleteRecursively
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChatInputPasteTest {
    @Test
    fun realTextFieldPastePreservesThresholdSettingAndStoresOriginalText() = withInput { ui ->
        for ((enabled, text) in listOf(true to "x".repeat(31), true to "x".repeat(32), false to "x".repeat(33))) {
            ui.input.clearInput()
            ui.settings.value = ui.settings.value.copy(displaySetting = ui.settings.value.displaySetting.copy(
                pasteLongTextAsFile = enabled,
            ))
            ui.paste(StringSelection(text)) { ui.input.textContent.text.toString() == text }
            assertTrue(ui.input.messageContent.isEmpty())
        }
        ui.input.clearInput()
        ui.settings.value = ui.settings.value.copy(displaySetting = ui.settings.value.displaySetting.copy(
            pasteLongTextAsFile = true,
        ))
        val text = "原始 pasted text\n第二行  \n".repeat(3)
        ui.paste(StringSelection(text)) { ui.input.messageContent.isNotEmpty() }
        assertEquals("", ui.input.textContent.text.toString())
        val document = ui.input.messageContent.single() as UIMessagePart.Document
        assertEquals("pasted_text.txt", document.fileName)
        assertEquals(text, File(URI(document.url)).readText())
        assertEquals(1, runBlocking { ui.filesManager.countChatFiles().first })
    }

    @Test
    fun imagePasteAddsOneAttachmentAndDoesNotDeleteSelectedText() = withInput { ui ->
        ui.input.setMessageText("keep selection")
        ui.input.textContent.edit { selection = TextRange(0, 4) }
        val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB)
        val contents = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor): Any = image
        }
        ui.paste(contents) { ui.input.messageContent.isNotEmpty() }
        val attached = ui.input.messageContent.single() as UIMessagePart.Image
        assertEquals("keep selection", ui.input.textContent.text.toString())
        assertEquals(TextRange(0, 4), ui.input.textContent.selection)
        val saved = ImageIO.read(File(URI(attached.url)))
        assertEquals(3, saved.width)
        assertEquals(2, saved.height)
    }

    @Test
    fun imageOnlyContextMenuShowsPasteAndImportsOnlyWhenClicked() = withInput(newContextMenu = true) { ui ->
        val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB)
        val paste = ui.openPasteMenu(object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor): Any = image
        })
        assertTrue(SemanticsProperties.Disabled !in paste.config, "Image paste menu must be enabled")
        assertTrue(ui.input.messageContent.isEmpty(), "Opening a menu must not import the image")
        assertTrue(paste.config[SemanticsActions.OnClick].action!!.invoke())
        ui.waitUntil { ui.input.messageContent.size == 1 }
    }

    private fun withInput(newContextMenu: Boolean = false, block: (InputFixture) -> Unit) {
        val oldContextMenuFlag = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenu
        val ui = InputFixture()
        try { block(ui) } finally {
            ui.scene.close()
            ui.koin.close()
            ui.scope.cancel()
            ui.root.deleteRecursively()
            ComposeFoundationFlags.isNewContextMenuEnabled = oldContextMenuFlag
        }
    }

    private class InputFixture {
        val root = Path(SystemTemporaryDirectory, "paste-test-${Uuid.random()}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val filesManager = testFilesManager(root, scope)
        val koin = koinApplication { modules(module { single { filesManager } }) }
        val input = ChatInputState()
        val settings = mutableStateOf(Settings(displaySetting = DisplaySetting(
            enableBlurEffect = false, pasteLongTextAsFile = true, pasteLongTextThreshold = 32,
        )))
        private val clipboard = object : Clipboard {
            val board = java.awt.datatransfer.Clipboard("isolated chat input test")
            override val nativeClipboard: NativeClipboard get() = board
            override suspend fun getClipEntry() = board.getContents(null)?.let(::ClipEntry)
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                board.setContents(clipEntry?.asAwtTransferable ?: StringSelection(""), null)
            }
        }
        private var frame = 0L
        val scene = ImageComposeScene(width = 640, height = 480) {
            KoinIsolatedContext(context = koin) {
                CompositionLocalProvider(
                    LocalASRState provides null,
                    LocalClipboard provides clipboard,
                    LocalSettings provides settings.value,
                    LocalToaster provides rememberToasterState(),
                ) {
                    MaterialTheme {
                        ChatInput(
                            state = input,
                            loading = false,
                            settings = settings.value,
                            hazeState = rememberHazeState(),
                            enableSearch = false,
                            onToggleSearch = {},
                            onUpdateChatModel = {},
                            onUpdateAssistant = {},
                            onUpdateSearchService = {},
                            onMoreClick = {},
                            onCancelClick = {},
                            onSendClick = {},
                            onLongSendClick = {},
                        )
                    }
                }
            }
        }
        fun paste(contents: Transferable, completed: () -> Boolean) {
            clipboard.board.setContents(contents, null)
            advance()
            assertTrue(textField().config[SemanticsActions.PasteText].action!!.invoke())
            waitUntil(completed)
        }
        fun openPasteMenu(contents: Transferable): SemanticsNode {
            clipboard.board.setContents(contents, null)
            advance()
            val position = textField().boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, position,
                buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
            scene.sendPointerEvent(PointerEventType.Release, position,
                buttons = PointerButtons(), button = PointerButton.Secondary)
            var paste: SemanticsNode? = null
            waitUntil {
                paste = allNodes().singleOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Paste" } == true
                }
                paste != null
            }
            return paste!!
        }
        private fun allNodes(): List<SemanticsNode> {
            fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
            return scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
        }
        private fun textField(): SemanticsNode {
            return allNodes().single {
                it.config.getOrNull(SemanticsProperties.TestTag) == "chat_input"
            }
        }
        fun waitUntil(completed: () -> Boolean) {
            val deadline = System.nanoTime() + 3_000_000_000L
            while (!completed() && System.nanoTime() < deadline) {
                advance()
                Thread.sleep(5)
            }
            advance()
            assertTrue(completed(), "Paste action did not produce the expected state")
        }
        private fun advance() {
            repeat(5) {
                Snapshot.sendApplyNotifications()
                frame += 16_000_000
                scene.render(frame).close()
            }
        }
    }
}
