package me.rerere.rikkahub.ui.pages.share.handler

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.base64Encode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.dsl.koinApplication
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class ShareHandlerTest {
    @Test
    fun sharedViewModelKeepsTextAndPersistsSelectedAssistantBeforeOpeningChat() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val values = MutableStateFlow(emptyPreferences())
        val preferences = object : DataStore<Preferences> {
            override val data = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return transform(values.value).also { values.value = it }
            }
        }
        val store = SettingsStore(preferences, backgroundScope)
        val koin = koinApplication { modules(module { single { store } }, viewModelModule) }
        val models = ViewModelStore()
        try {
            val text = "  CMP76 分享 🐇\nhttps://example.com/?a=1&b=中文+%20  "
            val vm = koin.koin.get<ShareHandlerVM> { parametersOf(text) }
            models.put("share", vm)
            assertEquals(text, vm.shareText)
            val assistant = Uuid.random()
            vm.updateAssistant(assistant)
            assertEquals(assistant.toString(), values.value[SettingsStore.SELECT_ASSISTANT])
            val stack = mutableListOf<NavKey>(Screen.History, Screen.ShareHandler(text))
            navigateToChatPage(Navigator(stack), initText = vm.shareText.base64Encode())
            assertEquals(1, stack.size)
            val chat = assertIs<Screen.Chat>(stack.single())
            assertEquals(text, chat.text?.base64Decode())
            assertEquals(emptyList(), chat.files)
        } finally {
            models.clear()
            koin.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun imageLocationIsPassedThroughWithoutReencodingOrTrimming() {
        for (image in listOf("content://camera/照片%20+1.jpg", "file:///tmp/照片%20+1.jpg")) {
            val stack = mutableListOf<NavKey>(Screen.ShareHandler("", image))
            navigateToChatPage(Navigator(stack), initText = "".base64Encode(), initFiles = listOf(image))
            val chat = assertIs<Screen.Chat>(stack.single())
            assertEquals(listOf(image), chat.files)
            assertEquals("", chat.text?.base64Decode())
        }
    }
}
