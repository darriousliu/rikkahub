package me.rerere.rikkahub.di

import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi

@OptIn(KoinInternalApi::class)
class AndroidModuleTest {
    @Test
    fun platformBindingsDoNotOverrideCommonBindings() {
        val duplicates = commonModule.mappings.keys.intersect(androidModule.mappings.keys)
        assertTrue("Duplicate registrations: $duplicates", duplicates.isEmpty())
    }
}
