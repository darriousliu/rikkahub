package me.rerere.rikkahub.ui.components.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.dokar.sonner.rememberToasterState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.asr_button_content_description
import me.rerere.rikkahub.generated.resources.asr_button_stop
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.CustomAsrState
import org.jetbrains.compose.resources.getString
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatInputAsrTest {
    @Test
    fun speechTransitionsHideSendAndKeepTheOriginalTranscriptPrefix() {
        val asr = FakeAsr()
        withInput(asr) { ui ->
            ui.input.setMessageText("prefix")
            ui.advance()
            ui.clickVoice()
            assertEquals(1, asr.starts)
            ui.advance()
            assertFalse(ui.hasSendButton())

            asr.state.value = asr.state.value.copy(status = ASRStatus.Listening)
            ui.advance()
            asr.transcript("first")
            assertEquals("prefix first", ui.input.textContent.text.toString())
            asr.transcript("replacement")
            assertEquals("prefix replacement", ui.input.textContent.text.toString())
            ui.clickStop()
            assertEquals(1, asr.stops)
            ui.advance()
            assertFalse(ui.hasSendButton())
            assertEquals(listOf(ASRStatus.Listening, ASRStatus.Stopping), ui.effects.played)

            asr.state.value = asr.state.value.copy(status = ASRStatus.Idle)
            ui.advance()
            assertTrue(ui.hasSendButton())
            assertEquals(1, ui.effects.preloaded)
        }
    }

    @Test
    fun blankPrefixAndErrorRetryDoNotAddExtraSeparators() {
        val asr = FakeAsr()
        withInput(asr) { ui ->
            ui.clickVoice()
            asr.transcript("first")
            assertEquals("first", ui.input.textContent.text.toString())
            asr.state.value = asr.state.value.copy(status = ASRStatus.Error)
            ui.advance()
            ui.clickVoice()
            asr.transcript("")
            assertEquals("first", ui.input.textContent.text.toString())
            asr.transcript("next")
            assertEquals("first next", ui.input.textContent.text.toString())
            assertEquals(2, asr.starts)
        }
    }

    @Test
    fun withoutAsrKeepsSendAvailableAndLoadingWindowEffectIsDisposed() = withInput(null) { ui ->
        assertTrue(ui.hasSendButton())
        assertTrue(ui.nodes().none { ui.voiceDescription in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
        ui.input.setMessageText("message")
        ui.advance()
        ui.sendButton().config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, ui.sent)
        Snapshot.withMutableSnapshot { ui.loading.value = true }
        ui.advance()
        ui.sendButton().config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, ui.cancelled, "The rendered send action should see loading=true")
        assertEquals(1, ui.effects.keepScreenOn)
        Snapshot.withMutableSnapshot { ui.loading.value = false }
        ui.advance()
        assertEquals(0, ui.effects.keepScreenOn)
    }

    private fun withInput(asr: FakeAsr?, block: (InputFixture) -> Unit) {
        val fixture = InputFixture(asr)
        try {
            fixture.advance()
            block(fixture)
        } finally {
            fixture.scene.close()
            fixture.koin.close()
        }
    }

    private class FakeAsr : CustomAsrState {
        override val state = MutableStateFlow(ASRState(isAvailable = true))
        var starts = 0
        var stops = 0
        lateinit var transcript: (String) -> Unit
        override fun start(onTranscriptChange: (String) -> Unit) {
            starts++
            transcript = onTranscriptChange
            state.value = state.value.copy(status = ASRStatus.Connecting)
        }
        override fun stop() {
            stops++
            state.value = state.value.copy(status = ASRStatus.Stopping)
        }
        override fun cleanup() = Unit
    }

    private class Effects : ChatInputPlatformContent {
        var preloaded = 0
        val played = mutableListOf<ASRStatus>()
        var keepScreenOn = 0
        override fun preloadAsrSounds() { preloaded++ }
        override fun playAsrSound(status: ASRStatus) { played += status }
        @Composable
        override fun KeepScreenOn() {
            DisposableEffect(Unit) {
                keepScreenOn++
                onDispose { keepScreenOn-- }
            }
        }
    }

    private class InputFixture(asr: FakeAsr?) {
        val input = ChatInputState()
        val loading = mutableStateOf(false)
        val effects = Effects()
        val koin = koinApplication { modules(module { single<ChatInputPlatformContent> { effects } }) }
        var sent = 0
        var cancelled = 0
        private var frame = 0L
        val voiceDescription = runBlocking { getString(Res.string.asr_button_content_description) }
        private val stopText = runBlocking { getString(Res.string.asr_button_stop) }
        private val settings = Settings(displaySetting = DisplaySetting(enableBlurEffect = false))
        val scene = ImageComposeScene(width = 640, height = 480) {
            KoinIsolatedContext(context = koin) {
                CompositionLocalProvider(
                    LocalASRState provides asr,
                    LocalSettings provides settings,
                    LocalToaster provides rememberToasterState(),
                ) {
                    MaterialTheme {
                        ChatInput(
                            state = input,
                            loading = loading.value,
                            settings = settings,
                            hazeState = rememberHazeState(),
                            enableSearch = false,
                            onToggleSearch = {},
                            onUpdateChatModel = {},
                            onUpdateAssistant = {},
                            onUpdateSearchService = {},
                            onMoreClick = {},
                            onCancelClick = { cancelled++ },
                            onSendClick = { sent++ },
                            onLongSendClick = {},
                        )
                    }
                }
            }
        }
        fun advance() {
            repeat(40) {
                Snapshot.sendApplyNotifications()
                frame += 16_000_000
                scene.render(frame).close()
            }
        }
        fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }
        fun hasSendButton() = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "chat_send_button" }
        fun sendButton() = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "chat_send_button" }
        fun clickVoice() {
            val node = nodes().single {
                voiceDescription in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() &&
                    it.config.contains(SemanticsActions.OnClick)
            }
            node.config[SemanticsActions.OnClick].action!!.invoke()
        }
        fun clickStop() {
            val node = nodes().single {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == stopText } &&
                    it.config.contains(SemanticsActions.OnClick)
            }
            node.config[SemanticsActions.OnClick].action!!.invoke()
        }
    }
}
