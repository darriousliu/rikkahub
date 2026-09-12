package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.chat_page_check_title_model_settings
import me.rerere.rikkahub.generated.resources.chat_page_clear_all_errors
import me.rerere.rikkahub.generated.resources.chat_page_dismiss_error
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatErrorSolution
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.uuid.Uuid

class ErrorCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val clipboard = RecordingClipboard()
    private val backStack = mutableListOf<NavKey>()

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalClipboard provides clipboard,
                LocalNavController provides Navigator(backStack),
            ) {
                MaterialTheme(content = content)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun titleAndMessageAreVisibleAndCopyPreservesPlainTextAndLabel() {
        val error = ChatError(title = "Send failed", error = IllegalStateException("CMP24 failure"))
        show { ErrorCard(error, onDismiss = {}) }

        compose.onNodeWithText("Send failed").assertIsDisplayed()
        compose.onNodeWithText("CMP24 failure").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy error message").performClick()
        compose.runOnIdle {
            assertEquals("CMP24 failure", clipboard.entry!!.clipData.getItemAt(0).text.toString())
            assertEquals("Error", clipboard.entry!!.clipData.description.label.toString())
        }
    }

    @Test
    fun missingExceptionMessageDisplaysAndCopiesOriginalFallback() {
        show { ErrorCard(ChatError(error = Exception()), onDismiss = {}) }

        compose.onNodeWithText("Unknown error").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy error message").performClick()
        compose.runOnIdle {
            assertEquals("Unknown error", clipboard.entry!!.clipData.getItemAt(0).text.toString())
        }
    }

    @Test
    fun dismissTargetsTheSelectedErrorAndSingleErrorHasNoClearAllButton() {
        val error = ChatError(error = Exception("one"))
        val dismissed = mutableListOf<Uuid>()
        val clear = runBlocking { getString(Res.string.chat_page_clear_all_errors) }
        val dismiss = runBlocking { getString(Res.string.chat_page_dismiss_error) }
        show { ErrorCardsDisplay(listOf(error), dismissed::add, onClearAllErrors = {}) }

        compose.onNodeWithText(clear).assertDoesNotExist()
        compose.onNodeWithContentDescription(dismiss).performClick()
        compose.runOnIdle { assertEquals(listOf(error.id), dismissed) }
    }

    @Test
    fun allErrorsAreDisplayedAndClearAllUsesItsOwnCallback() {
        val errors = listOf(ChatError(error = Exception("one")), ChatError(error = Exception("two")))
        val dismissed = mutableListOf<Uuid>()
        var clearCount = 0
        val clear = runBlocking { getString(Res.string.chat_page_clear_all_errors) }
        show { ErrorCardsDisplay(errors, dismissed::add, onClearAllErrors = { clearCount++ }) }

        compose.onNodeWithText("one").assertIsDisplayed()
        compose.onNodeWithText("two").assertIsDisplayed()
        compose.onNodeWithText(clear).performClick()
        compose.runOnIdle {
            assertEquals(1, clearCount)
            assertTrue(dismissed.isEmpty())
        }
    }

    @Test
    fun autoDismissWaitsFiveSecondsAndDoesNotRepeat() {
        var dismissCount = 0
        show { ErrorCard(ChatError(error = Exception("timeout")), onDismiss = { dismissCount++ }) }

        compose.mainClock.advanceTimeBy(4_900)
        compose.runOnIdle { assertEquals(0, dismissCount) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, dismissCount) }
        compose.mainClock.advanceTimeBy(5_000)
        compose.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun replacingErrorRestartsTimerForItsNewId() {
        val first = ChatError(error = Exception("first"))
        val second = ChatError(error = Exception("second"))
        val current = mutableStateOf(first)
        val dismissed = mutableListOf<Uuid>()
        show {
            val error = current.value
            ErrorCard(error, onDismiss = { dismissed += error.id })
        }

        compose.mainClock.advanceTimeBy(4_000)
        compose.runOnIdle { current.value = second }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(4_000)
        compose.runOnIdle { assertTrue(dismissed.isEmpty()) }
        compose.mainClock.advanceTimeBy(1_100)
        compose.runOnIdle { assertEquals(listOf(second.id), dismissed) }
    }

    @Test
    fun titleModelSolutionOpensModelSettings() {
        val link = runBlocking { getString(Res.string.chat_page_check_title_model_settings) }
        show {
            ErrorCard(
                ChatError(error = Exception("title"), solution = ChatErrorSolution.CheckTitleModelSettings),
                onDismiss = {},
            )
        }

        compose.onNodeWithText(link).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(Screen.SettingModels), backStack) }
    }

    private class RecordingClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
        }
    }
}
