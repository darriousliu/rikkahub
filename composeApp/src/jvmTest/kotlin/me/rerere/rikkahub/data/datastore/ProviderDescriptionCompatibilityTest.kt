package me.rerere.rikkahub.data.datastore

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Exercises ai's compiled composable properties from a separate module. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderDescriptionCompatibilityTest {
    @Test
    fun constructorsAndBothCopyMethodsRetainDescriptions() {
        val rendered = mutableListOf<String>()
        val description: @Composable () -> Unit = { SideEffect { rendered += "long" } }
        val shortDescription: @Composable () -> Unit = { SideEffect { rendered += "short" } }

        providers(description, shortDescription).forEach { provider ->
            val concreteCopy = when (provider) {
                is ProviderSetting.OpenAI -> provider.copy(name = "copy")
                is ProviderSetting.Google -> provider.copy(name = "copy")
                is ProviderSetting.Claude -> provider.copy(name = "copy")
            }
            listOf(provider, concreteCopy, provider.copyProvider(name = "copyProvider")).forEach { copy ->
                rendered.clear()
                render(copy)
                assertEquals(listOf("long", "short"), rendered)
            }
        }
    }

    @Test
    fun baseCopyCanOverrideOneDescriptionAndKeepTheOther() {
        val rendered = mutableListOf<String>()
        providers(
            description = { SideEffect { rendered += "original" } },
            shortDescription = { SideEffect { rendered += "short" } },
        ).forEach { provider ->
            rendered.clear()
            render(provider.copyProvider(description = { SideEffect { rendered += "override" } }))
            assertEquals(listOf("override", "short"), rendered)
        }
    }

    @Test
    fun deserializationKeepsEmptyDefaultsAndSettingsCanRestoreDescriptions() {
        val json = Json { encodeDefaults = true }
        val rendered = mutableListOf<String>()
        providers(
            description = { SideEffect { rendered += "long" } },
            shortDescription = { SideEffect { rendered += "short" } },
        ).forEach { provider ->
            val encoded = json.encodeToString<ProviderSetting>(provider)
            assertFalse("description" in encoded)
            assertFalse("shortDescription" in encoded)
            val decoded = json.decodeFromString<ProviderSetting>(encoded)
            rendered.clear()
            render(decoded)
            assertEquals(emptyList(), rendered)

            val descriptions = mapOf(provider.id to provider.description)
            render(decoded.copyProvider(
                description = descriptions.getValue(decoded.id),
                shortDescription = provider.shortDescription,
            ))
            assertEquals(listOf("long", "short"), rendered)
        }
    }

    @Test
    fun capturedStateStillTriggersRecompositionAcrossTheModuleBoundary() = runTest {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val value = mutableStateOf("before")
        val rendered = mutableListOf<String>()
        val provider: ProviderSetting = ProviderSetting.OpenAI(description = {
            val text = value.value
            SideEffect { rendered += text }
        })
        try {
            composition.setContent { provider.description() }
            runCurrent()
            assertEquals(listOf("before"), rendered)

            value.value = "after"
            Snapshot.sendApplyNotifications()
            runCurrent()
            frameClock.sendFrame(1_000_000L)
            runCurrent()
            assertEquals(listOf("before", "after"), rendered)
        } finally {
            composition.dispose()
            recomposer.close()
            runner.join()
        }
    }

    private fun providers(
        description: @Composable () -> Unit,
        shortDescription: @Composable () -> Unit,
    ): List<ProviderSetting> = listOf(
        ProviderSetting.OpenAI(description = description, shortDescription = shortDescription),
        ProviderSetting.Google(description = description, shortDescription = shortDescription),
        ProviderSetting.Claude(description = description, shortDescription = shortDescription),
    )

    private fun render(provider: ProviderSetting) = runTest {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(EmptyApplier(), recomposer)
        try {
            composition.setContent {
                provider.description()
                provider.shortDescription()
            }
        } finally {
            composition.dispose()
            recomposer.close()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
