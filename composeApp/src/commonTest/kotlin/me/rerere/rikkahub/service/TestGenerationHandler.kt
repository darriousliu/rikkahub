package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.Flow
import kotlinx.io.files.Path
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstant

/** Translation has no memory or filesystem side effects. Unexpected memory access must fail. */
internal fun testGenerationHandler(providers: ProviderManager) = GenerationHandler(
    Path("."), providers, JsonInstant, MemoryRepository(object : MemoryDAO {
        override fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>> = unused()
        override suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity> = unused()
        override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = unused()
        override suspend fun getAllMemories(): List<MemoryEntity> = unused()
        override suspend fun getMemoryById(id: Int): MemoryEntity? = unused()
        override suspend fun insertMemory(memory: MemoryEntity): Long = unused()
        override suspend fun updateMemory(memory: MemoryEntity): Unit = unused()
        override suspend fun deleteMemory(id: Int): Unit = unused()
        override suspend fun deleteMemoriesOfAssistant(assistantId: String): Unit = unused()
        private fun unused(): Nothing = error("Translation must not access memory")
    }),
)
