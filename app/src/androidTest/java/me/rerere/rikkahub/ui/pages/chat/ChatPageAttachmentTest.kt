@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.rerere.rikkahub.ui.pages.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.yalantis.ucrop.UCrop
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import java.nio.file.Files
import me.rerere.rikkahub.ui.components.ai.useCropLauncher
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.rememberCameraLauncher
import me.rerere.rikkahub.utils.toAndroidUri
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPageAttachmentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val registry = RecordingActivityResultRegistry()
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private val ownedOutputs = mutableSetOf<File>()

    @Before
    fun setUp() {
        root = Files.createTempDirectory(context.cacheDir.toPath(), "cmp62-chat-attachment-").toFile()
    }

    @After
    fun tearDown() {
        ownedOutputs.forEach { it.delete() }
        assertTrue("Remove only the CMP62 fixture", root.deleteRecursively())
    }

    @Test
    fun cameraCaptureRetainsOutputUntilCallbackCleanupAndRemovesCancelledOutput() {
        // The host grants/restores this permission outside instrumentation; revoking it kills the app process.
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.CAMERA))
        var launchCamera: (() -> Unit)? = null
        var captured: PlatformFile? = null
        var cleanup: (() -> Unit)? = null
        var captures = 0

        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                launchCamera = rememberCameraLauncher { file, release ->
                    captures++
                    captured = file
                    cleanup = release
                }
            }
        }
        compose.waitForIdle()

        compose.runOnIdle { launchCamera!!.invoke() }
        val successfulLaunch = registry.latest<Uri>(ActivityResultContracts.TakePicture::class.java)
        val successfulOutput = successfulLaunch.input
        writeTinyPng(successfulOutput)
        compose.runOnIdle { registry.dispatch(successfulLaunch, Activity.RESULT_OK, null) }

        val capturedFile = requireNotNull(captured)
        assertArrayEquals(TINY_PNG, context.contentResolver.openInputStream(capturedFile.toAndroidUri())!!.use { it.readBytes() })
        assertEquals(1, captures)
        assertTrue(cameraFile(successfulOutput).isFile)

        compose.runOnIdle { cleanup!!.invoke() }
        assertFalse(cameraFile(successfulOutput).exists())

        compose.runOnIdle { launchCamera!!.invoke() }
        val cancelledLaunch = registry.latest<Uri>(ActivityResultContracts.TakePicture::class.java)
        val cancelledOutput = cancelledLaunch.input
        writeTinyPng(cancelledOutput)
        assertTrue(cameraFile(cancelledOutput).exists())
        compose.runOnIdle { registry.dispatch(cancelledLaunch, Activity.RESULT_CANCELED, null) }

        assertEquals("A cancelled capture must not reach the attachment callback", 1, captures)
        assertFalse(cameraFile(cancelledOutput).exists())
    }

    @Test
    fun cropResultKeepsSuccessfulOutputDuringCallbackAndCleansEveryTerminalResult() {
        var launchCrop: ((PlatformFile) -> Unit)? = null
        val events = mutableListOf<String>()
        val source = File(root, "source.png").also(::writeTinyPng)
        var successCount = 0

        compose.setContent {
            val toaster = rememberToasterState()
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalToaster provides toaster,
            ) {
                Toaster(state = toaster)
                launchCrop = useCropLauncher(
                    onCroppedImageReady = { output ->
                        assertArrayEquals(
                            TINY_PNG,
                            output.toAndroidUri().toFile().readBytes(),
                        )
                        assertTrue(output.toAndroidUri().toFile().isFile)
                        successCount++
                        events += "success"
                    },
                    onCleanup = { events += "cleanup" },
                )
            }
        }
        compose.waitForIdle()

        compose.runOnIdle { launchCrop!!.invoke(PlatformFile(source)) }
        val successLaunch = registry.latest<Intent>(ActivityResultContracts.StartActivityForResult::class.java)
        val successOutput = cropOutput(successLaunch.input)
        writeTinyPng(successOutput)
        compose.runOnIdle { registry.dispatch(successLaunch, Activity.RESULT_OK, Intent()) }
        compose.waitUntil(WAIT_MILLIS) { !successOutput.toFile().exists() }
        assertEquals(listOf("success", "cleanup"), events)
        assertFalse(successOutput.toFile().exists())

        events.clear()
        compose.runOnIdle { launchCrop!!.invoke(PlatformFile(source)) }
        val cancelledLaunch = registry.latest<Intent>(ActivityResultContracts.StartActivityForResult::class.java)
        val cancelledOutput = cropOutput(cancelledLaunch.input)
        writeTinyPng(cancelledOutput)
        compose.runOnIdle { registry.dispatch(cancelledLaunch, Activity.RESULT_CANCELED, null) }
        compose.waitUntil(WAIT_MILLIS) { !cancelledOutput.toFile().exists() }
        assertEquals(listOf("cleanup"), events)
        assertEquals(1, successCount)

        events.clear()
        compose.runOnIdle { launchCrop!!.invoke(PlatformFile(source)) }
        val errorLaunch = registry.latest<Intent>(ActivityResultContracts.StartActivityForResult::class.java)
        val errorOutput = cropOutput(errorLaunch.input)
        writeTinyPng(errorOutput)
        val errorResult = Intent().putExtra(UCrop.EXTRA_ERROR, IllegalStateException("CMP62 crop failure"))
        compose.runOnIdle { registry.dispatch(errorLaunch, UCrop.RESULT_ERROR, errorResult) }
        compose.waitUntil(WAIT_MILLIS) { !errorOutput.toFile().exists() }
        compose.onNodeWithText("Failed to crop image: CMP62 crop failure").assertIsDisplayed()
        assertEquals(listOf("cleanup"), events)
        assertEquals(1, successCount)
    }

    private fun cameraFile(output: Uri): File = File(context.cacheDir, requireNotNull(output.lastPathSegment))

    private fun cropOutput(intent: Intent): Uri = requireNotNull(intent.getParcelableExtra<Uri>(UCrop.EXTRA_OUTPUT_URI)) {
        "UCrop launch intent must include its destination URI"
    }.also { output ->
        assertEquals(output, UCrop.getOutput(intent))
    }

    private fun writeTinyPng(uri: Uri) {
        ownedOutputs += if (uri.scheme == "file") uri.toFile() else cameraFile(uri)
        when (uri.scheme) {
            "file" -> writeTinyPng(uri.toFile())
            else -> context.contentResolver.openOutputStream(uri)!!.use { it.write(TINY_PNG) }
        }
    }

    private fun writeTinyPng(file: File) {
        file.outputStream().use { it.write(TINY_PNG) }
    }

    private class RecordingActivityResultRegistry : ActivityResultRegistry() {
        private val launches = mutableListOf<Launch<*>>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launches += Launch(requestCode, contract, input)
        }

        fun <I> latest(contractType: Class<*>): Launch<I> {
            @Suppress("UNCHECKED_CAST")
            return launches.last { contractType.isInstance(it.contract) } as Launch<I>
        }

        fun dispatch(launch: Launch<*>, resultCode: Int, data: Intent?) {
            dispatchResult(launch.requestCode, resultCode, data)
        }
    }

    private data class Launch<I>(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: I,
    )

    private companion object {
        const val WAIT_MILLIS = 5_000L
        val TINY_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4.toByte(), 0x89.toByte(),
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x44, 0x41, 0x54,
            0x08, 0xd7.toByte(), 0x63, 0xf8.toByte(), 0xcf.toByte(), 0xc0.toByte(), 0xf0.toByte(), 0x1f,
            0x00, 0x05, 0x00, 0x01, 0xff.toByte(), 0x89.toByte(), 0x99.toByte(), 0x3d,
            0x1d, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44,
            0xae.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }
}
