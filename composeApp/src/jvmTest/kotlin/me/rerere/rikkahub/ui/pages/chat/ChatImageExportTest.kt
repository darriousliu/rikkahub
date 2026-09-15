package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.createJvmSettingsDataStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalComposeUiApi::class)
class ChatImageExportTest {
    @Test
    fun waitsForWebViewSnapshotRegisteredAfterMeasurement() = runBlocking(Dispatchers.Main) {
        var locals: CompositionLocalContext? = null
        val host = ImageComposeScene(1, 1) { locals = currentCompositionLocalContext }
        try {
            host.render().close()
            val png = renderComposeImage(checkNotNull(locals), Density(1f)) {
                val snapshots = LocalWebViewSnapshots.current
                val state = rememberWebViewStateWithHTMLData("snapshot fixture")
                var size by remember { mutableStateOf(IntSize.Zero) }
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(size) {
                    if (size.width > 0) {
                        val job = CompletableDeferred<Unit>()
                        snapshots[state] = job
                        delay(200)
                        ready = true
                        job.complete(Unit)
                    }
                }
                Box(Modifier.width(540.dp).height(200.dp)
                    .onSizeChanged { size = it }
                    .background(if (ready) Color.Green else Color.Red))
            }
            val image = ImageIO.read(png.inputStream())
            assertEquals(0xff00ff00.toInt(), image.getRGB(540, 200))
        } finally {
            host.close()
        }
    }

    @Test
    fun sharedChatRendererInheritsThemeContextAndIncludesImageAttachments() = runBlocking(Dispatchers.Main) {
        val directory = Files.createTempDirectory("chat-image-export-").toFile()
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val appScope = AppScope()
        val dataStore = createJvmSettingsDataStore(scope = settingsScope, directory = directory)
        val settingsStore = SettingsStore(dataStore, settingsScope)
        val settings = Settings()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val source = BufferedImage(400, 80, BufferedImage.TYPE_INT_RGB)
        source.createGraphics().apply {
            color = java.awt.Color(230, 20, 30)
            fillRect(0, 0, 200, 80)
            color = java.awt.Color(20, 40, 230)
            fillRect(200, 0, 200, 80)
            dispose()
        }
        val file = directory.resolve("attachment.png")
        ImageIO.write(source, "png", file)
        var locals: CompositionLocalContext? = null
        val host = ImageComposeScene(1, 1) {
            KoinApplication(configuration = koinConfiguration {
                modules(module {
                    single { dataStore }
                    single { settingsStore }
                    single { appScope }
                    single<BooleanPreferenceStore> { DataStoreBooleanPreferenceStore(dataStore) }
                })
            }) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    locals = currentCompositionLocalContext
                }
            }
        }
        try {
            host.render().close()
            val message = UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Text("# Export 中文\n\n" + (1..40).joinToString("\n") { "$it. Long conversation" }),
                    UIMessagePart.Image(file.toURI().toString()),
                    UIMessagePart.Text("Bottom of conversation"),
                ),
            )
            val conversation = Conversation(assistantId = Uuid.random(), messageNodes = emptyList(), title = "Export")
            val png = renderComposeImage(checkNotNull(locals), Density(1f)) {
                CompositionLocalProvider(LocalSettings provides settings) {
                    ExportedChatImage(conversation, listOf(message))
                }
            }
            val image = ImageIO.read(png.inputStream())
            assertEquals(1080, image.width)
            assertTrue(image.height > 2000)
            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            assertTrue(pixels.any { it == 0xffe6141e.toInt() }, "Image attachment's left half must be captured")
            assertTrue(pixels.any { it == 0xff1428e6.toInt() }, "Image attachment's right half must be captured")
        } finally {
            host.close()
            appScope.cancel()
            settingsScope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun capturesBothEdgesAndFullHeightAtDifferentScreenDensities() = runBlocking(Dispatchers.Main) {
        for (density in listOf(1f, 1.5f, 2f)) {
            var locals: CompositionLocalContext? = null
            val host = ImageComposeScene(1, 1, density = Density(density)) {
                locals = currentCompositionLocalContext
            }
            try {
                host.render().close()
                val png = renderComposeImage(checkNotNull(locals), Density(density)) {
                    MaterialTheme {
                        Column(Modifier.width(540.dp).background(Color.White)) {
                            Row(Modifier.fillMaxWidth().height(16.dp)) {
                                Box(Modifier.width(8.dp).height(16.dp).background(Color.Red))
                                Spacer(Modifier.weight(1f))
                                Box(Modifier.width(8.dp).height(16.dp).background(Color.Blue))
                            }
                            repeat(100) { Text("Row $it 中文", Modifier.height(24.dp)) }
                            Box(Modifier.fillMaxWidth().height(16.dp).background(Color.Green))
                        }
                    }
                }
                val image = ImageIO.read(png.inputStream())
                assertEquals(1080, image.width)
                assertEquals(4864, image.height)
                assertEquals(0xffff0000.toInt(), image.getRGB(4, 4))
                assertEquals(0xff0000ff.toInt(), image.getRGB(1075, 4))
                assertEquals(0xff00ff00.toInt(), image.getRGB(540, 4860))
                assertTrue((500 until 4500).any { y -> image.getRGB(15, y) != 0xffffffff.toInt() })
            } finally {
                host.close()
            }
        }
    }

    @Test
    fun omitsNativeWebViewAndKeepsContentAfterIt() = runBlocking(Dispatchers.Main) {
        var locals: CompositionLocalContext? = null
        val host = ImageComposeScene(1, 1) { locals = currentCompositionLocalContext }
        try {
            host.render().close()
            val png = renderComposeImage(checkNotNull(locals), Density(1f)) {
                Column(Modifier.width(540.dp).background(Color.White)) {
                    WebView(
                        rememberWebViewStateWithHTMLData("<body style='background:red'>Native HTML</body>"),
                        Modifier.fillMaxWidth().height(200.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(16.dp).background(Color.Green))
                }
            }
            val image = ImageIO.read(png.inputStream())
            assertEquals(1080, image.width)
            assertEquals(432, image.height)
            assertEquals(0xffffffff.toInt(), image.getRGB(540, 200))
            assertEquals(0xff00ff00.toInt(), image.getRGB(540, 428))
        } finally {
            host.close()
        }
    }
}
