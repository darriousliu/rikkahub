package me.rerere.rikkahub.ui.components.ai

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Color
import android.system.Os
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.content.FileProvider
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dokar.sonner.rememberToasterState
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.shared.R
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.playAsrSound
import me.rerere.rikkahub.utils.preloadAsrSounds
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

@RunWith(AndroidJUnit4::class)
class ChatInputPlatformTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val input = ChatInputState()
    private val clipboard = IsolatedClipboard()
    private val loading = mutableStateOf(false)
    private val visible = mutableStateOf(true)
    private val settings = mutableStateOf(
        Settings(displaySetting = DisplaySetting(pasteLongTextAsFile = true, pasteLongTextThreshold = THRESHOLD)),
    )
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var repository: FilesRepository
    private lateinit var filesManager: FilesManager
    private lateinit var isolatedKoin: KoinApplication
    private lateinit var composeView: View
    private var contentSet = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = Files.createTempDirectory(context.cacheDir.toPath(), "cmp61-chat-input-").toFile()
        database = buildAppDatabase(
            builder = Room.inMemoryDatabaseBuilder<AppDatabase>(
                context = context,
                factory = AppDatabaseConstructor::initialize,
            ),
            driver = BundledSQLiteDriver(),
            ftsDialect = MessageFtsDialect.UNICODE61,
        )
        repository = FilesRepository(database.managedFileDao())
        val managedRoot = File(root, "managed").also { assertTrue(it.mkdir()) }
        filesManager = FilesManager(Path(managedRoot.absolutePath), repository, scope, asyncFileIo = false)
        isolatedKoin = koinApplication { modules(module { single { filesManager } }) }
    }

    @After
    fun tearDown() {
        try {
            if (contentSet) {
                compose.runOnIdle { visible.value = false }
                compose.waitForIdle()
            }
        } finally {
            clipboard.entry = null
            try {
                runBlocking { withTimeout(WAIT_MILLIS) { scopeJob.cancelAndJoin() } }
            } finally {
                try {
                    if (::isolatedKoin.isInitialized) isolatedKoin.close()
                    if (::database.isInitialized) database.close()
                } finally {
                    if (::root.isInitialized) assertTrue("Remove only the CMP61 fixture", root.deleteRecursively())
                }
            }
        }
    }

    @Test
    fun textPasteRespectsThresholdAndDisabledSettingAndPreservesAttachedText() {
        showInput()
        val passThroughCases = listOf(
            true to "x".repeat(THRESHOLD - 1),
            true to "x".repeat(THRESHOLD),
            false to "x".repeat(THRESHOLD + 1),
        )
        for ((enabled, text) in passThroughCases) {
            compose.runOnIdle {
                input.clearInput()
                settings.value = settings.value.copy(
                    displaySetting = settings.value.displaySetting.copy(pasteLongTextAsFile = enabled),
                )
            }
            paste(ClipData.newPlainText("CMP61", text))
            compose.runOnIdle {
                assertEquals(text, input.textContent.text.toString())
                assertTrue(input.messageContent.isEmpty())
            }
        }
        assertEquals(0 to 0L, runBlocking { filesManager.countChatFiles() })
        assertTrue(registeredFiles().isEmpty())
        compose.runOnIdle {
            input.clearInput()
            settings.value = settings.value.copy(
                displaySetting = settings.value.displaySetting.copy(pasteLongTextAsFile = true),
            )
        }
        val text = "原始 pasted text\n第二行  \n".repeat(3)
        assertTrue(text.length > THRESHOLD)
        paste(ClipData.newPlainText("CMP61", text))

        val document = compose.runOnIdle {
            assertEquals("", input.textContent.text.toString())
            input.messageContent.single() as UIMessagePart.Document
        }
        assertEquals("pasted_text.txt", document.fileName)
        assertEquals("text/plain", document.mime)
        compose.onNodeWithText("pasted_text.txt").assertIsDisplayed()
        val file = storedFile(document.url)
        assertArrayEquals(text.encodeToByteArray(), file.readBytes())
        val entity = registeredFiles().single()
        assertEquals("pasted_text.txt", entity.displayName)
        assertEquals("text/plain", entity.mimeType)
        assertEquals("upload/${file.name}", entity.relativePath)
        assertEquals(text.encodeToByteArray().size.toLong(), entity.sizeBytes)
        assertEquals(1 to entity.sizeBytes, runBlocking { filesManager.countChatFiles() })
    }

    @Test
    fun mixedMimePastePrioritizesImageAndLeavesRemainingLongTextInline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(root, "clipboard_image.png")
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(32, 96, 160))
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val sourceBytes = source.readBytes()
        val sourceMode = Os.stat(source.absolutePath).st_mode
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
        val text = "mixed text must remain inline ".repeat(3)
        val clip = ClipData("CMP61 mixed", arrayOf("image/png", "text/plain"), ClipData.Item(uri))
        clip.addItem(ClipData.Item(text))
        showInput()
        paste(clip)

        val image = compose.runOnIdle {
            assertEquals(text, input.textContent.text.toString())
            input.messageContent.single() as UIMessagePart.Image
        }
        val copy = storedFile(image.url)
        assertArrayEquals(sourceBytes, copy.readBytes())
        assertArrayEquals(sourceBytes, source.readBytes())
        assertEquals(sourceMode, Os.stat(source.absolutePath).st_mode)
        val entity = registeredFiles().single()
        assertEquals(source.name, entity.displayName)
        assertEquals("image/png", entity.mimeType)
        assertEquals("upload/${copy.name}", entity.relativePath)
        assertEquals(sourceBytes.size.toLong(), entity.sizeBytes)
        assertEquals(1 to entity.sizeBytes, runBlocking { filesManager.countChatFiles() })
        assertEquals(2, clip.itemCount)
        assertEquals(uri, clip.getItemAt(0).uri)
        assertEquals(text, clip.getItemAt(1).text.toString())
    }

    @Test
    fun loadingScreenOnLifecycleAndOriginalAsrSoundResourcesAreWired() {
        showInput()
        assertKeepScreenOn(false)
        compose.runOnIdle { loading.value = true }
        assertKeepScreenOn(true)
        compose.runOnIdle { loading.value = false }
        assertKeepScreenOn(false)
        compose.runOnIdle { loading.value = true }
        assertKeepScreenOn(true)
        compose.runOnIdle { visible.value = false }
        assertKeepScreenOn(false)
        val player = KoinPlatform.getKoin().get<SoundEffectPlayer>()
        val resourceIds = listOf(R.raw.asr_start, R.raw.asr_stop)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resourceIds.forEach { id ->
            context.resources.openRawResource(id).use { assertTrue("Packaged ASR audio is nonempty", it.read() >= 0) }
        }
        val samples = compose.runOnIdle { loadedSounds(player) }
        assertTrue(resourceIds.all { (samples[it] ?: 0) > 0 })
        assertEquals(2, resourceIds.map { samples.getValue(it) }.distinct().size)
        compose.waitUntil(WAIT_MILLIS) {
            compose.runOnUiThread { resourceIds.all { samples.getValue(it) in readySounds(player) } }
        }
        compose.runOnIdle {
            preloadAsrSounds()
            playAsrSound(ASRStatus.Listening)
            playAsrSound(ASRStatus.Stopping)
            playAsrSound(ASRStatus.Idle)
            assertSame(player, KoinPlatform.getKoin().get<SoundEffectPlayer>())
            assertEquals("Preload and playback reuse decoded samples", samples, loadedSounds(player))
        }
        // Do not release or replace the application-owned singleton. Audible output is outside this test.
    }

    private fun showInput() {
        compose.setContent {
            composeView = LocalView.current
            KoinIsolatedContext(context = isolatedKoin) {
                CompositionLocalProvider(
                    LocalClipboard provides clipboard,
                    LocalSettings provides settings.value,
                    LocalASRState provides null,
                    LocalToaster provides rememberToasterState(),
                ) {
                    MaterialTheme {
                        if (visible.value) {
                            ChatInput(
                                state = input,
                                loading = loading.value,
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
        }
        contentSet = true
        compose.waitForIdle()
    }

    private fun paste(clip: ClipData) {
        compose.runOnIdle { clipboard.entry = ClipEntry(clip) }
        // This is TextField's real paste action, which dispatches Android TransferableContent/consume.
        compose.onNodeWithTag("chat_input").performSemanticsAction(SemanticsActions.PasteText) { action ->
            assertTrue(action())
        }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(clip, clipboard.entry!!.clipData) }
    }

    private fun registeredFiles(): List<ManagedFileEntity> = runBlocking {
        withTimeout(WAIT_MILLIS) {
            scopeJob.children.toList().joinAll()
            repository.listByFolder(FileFolders.UPLOAD).first()
        }
    }

    private fun storedFile(url: String): File = File(URI(url)).also {
        assertEquals(File(root, "managed/upload").canonicalFile, it.parentFile!!.canonicalFile)
        assertTrue(it.isFile)
    }

    private fun assertKeepScreenOn(expected: Boolean) {
        compose.runOnIdle {
            assertEquals(
                "ChatInput loading owns the Compose view's keep-screen-on request",
                expected,
                composeView.keepScreenOn,
            )
        }
    }

    // Read-only observation keeps production APIs and the real SoundPool binding unchanged.
    @Suppress("UNCHECKED_CAST")
    private fun loadedSounds(player: SoundEffectPlayer): Map<Int, Int> =
        (SoundEffectPlayer::class.java.getDeclaredField("loadedSounds").apply { isAccessible = true }
            .get(player) as Map<Int, Int>).toMap()

    @Suppress("UNCHECKED_CAST")
    private fun readySounds(player: SoundEffectPlayer): Set<Int> =
        (SoundEffectPlayer::class.java.getDeclaredField("readySounds").apply { isAccessible = true }
            .get(player) as Set<Int>).toSet()

    // Native ClipData stays local to this composition; the user's system clipboard is never read or replaced.
    private class IsolatedClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
        }
    }

    companion object {
        private const val THRESHOLD = 32
        private const val WAIT_MILLIS = 5_000L
    }
}
