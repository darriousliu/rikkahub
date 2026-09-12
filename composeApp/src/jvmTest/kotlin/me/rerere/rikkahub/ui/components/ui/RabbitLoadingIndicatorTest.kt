package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.context.LocalSettings

class RabbitLoadingIndicatorTest {
    @Test
    fun eyesAnimateAndRepeatAfterThreeSeconds() {
        scene { RabbitLoadingIndicator() }.use { scene ->
            scene.frame(0)
            val initial = scene.frame(0)
            val lookingLeft = scene.frame(1500)
            val repeated = scene.frame(3000)
            assertFalse(initial.contentEquals(lookingLeft), "Eyes should move during the cycle")
            assertContentEquals(initial.copyOfRange(0, 128 * 70), lookingLeft.copyOfRange(0, 128 * 70))
            assertContentEquals(initial, repeated, "The original animation repeats every 3000 ms")
        }
    }

    @Test
    fun callerSizeAndChangingThemeColorAreRespected() {
        val primary = mutableStateOf(Color.Red)
        ImageComposeScene(width = 128, height = 128) {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme(colorScheme = lightColorScheme(primary = primary.value)) {
                    RabbitLoadingIndicator(Modifier.size(28.dp))
                }
            }
        }.use { scene ->
            val red = scene.frame(0)
            assertTrue(red.any { it == 0xFFFF0000.toInt() })
            assertTrue(red.indices.filter { red[it] ushr 24 != 0 }.all { it % 128 < 28 && it / 128 < 28 })
            primary.value = Color.Blue
            val blue = scene.frame(16)
            assertTrue(blue.any { it == 0xFF0000FF.toInt() })
            assertFalse(blue.any { it == 0xFFFF0000.toInt() })
        }
    }

    @Test
    fun disablingRabbitKeepsTheOriginalMaterialIndicator() {
        val actual = scene(useRabbit = false) {
            RabbitLoadingIndicator(Modifier.size(32.dp))
        }.use { it.frame(0) }
        val expected = scene {
            ContainedLoadingIndicator(Modifier.size(32.dp))
        }.use { it.frame(0) }
        assertTrue(actual.any { it ushr 24 != 0 })
        assertContentEquals(expected, actual)
    }

    private fun scene(useRabbit: Boolean = true, content: @Composable () -> Unit) =
        ImageComposeScene(width = 128, height = 128) {
            CompositionLocalProvider(
                LocalSettings provides Settings(
                    displaySetting = DisplaySetting(useAppIconStyleLoadingIndicator = useRabbit),
                ),
            ) {
                MaterialTheme { content() }
            }
        }

    private fun ImageComposeScene.frame(millis: Long): IntArray = render(millis * 1_000_000).use { image ->
        image.encodeToData()!!.use { data ->
            val buffered = ImageIO.read(ByteArrayInputStream(data.bytes))
            buffered.getRGB(0, 0, 128, 128, null, 0, 128)
        }
    }
}
