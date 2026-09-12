package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.common.concurrent.ConcurrentHashMap
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.tools.buildMemoryTools
import me.rerere.rikkahub.data.ai.tools.createConversationTools
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.chat_page_compress_not_enough_messages
import me.rerere.rikkahub.generated.resources.error_title_generate_title
import me.rerere.rikkahub.generated.resources.error_title_translate_message
import me.rerere.rikkahub.generated.resources.translating
import me.rerere.rikkahub.ui.pages.translator.TranslationLanguage
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "SharedChatRuntime"

/**
 * Portable chat runtime used by the iOS and Desktop product shells.
 *
 * Android keeps its feature-complete [ChatService]. This implementation owns the common core:
 * persisted conversation state, provider requests, streaming updates, cancellation, the shared
 * transformer pipeline and local tools. Android-only workspace tools, search tools and attachment
 * cleanup remain behind the Android runtime and platform UI adapters.
 */
internal class SharedChatRuntime(
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val folderRepository: FolderRepository,
    private val providerManager: ProviderManager,
    private val eventBus: AppEventBus,
    private val booleanPreferenceStore: BooleanPreferenceStore,
    private val stringPreferenceStore: StringPreferenceStore,
    private val attachmentStore: SharedChatAttachmentStore,
    private val mcpManager: McpManager,
    private val templateTransformer: TemplateTransformer,
    private val localTools: LocalTools,
    private val memoryRepository: MemoryRepository,
    private val skillManager: SkillManager,
) : ChatRuntime {
    private val titleGenerator = ConversationTitleGenerator(
        providerManager = providerManager,
        getSettings = { settingsStore.settingsFlow.first() },
        getConversation = conversationRepository::getConversationById,
        saveConversation = ::saveConversation,
        onError = { id, error ->
            addError(
                error = error,
                conversationId = id,
                title = getString(Res.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckTitleModelSettings,
            )
        },
    )

    private val suggestionGenerator = ConversationSuggestionGenerator(
        providerManager = providerManager,
        getSettings = { settingsStore.settingsFlow.first() },
        getConversation = conversationRepository::getConversationById,
        getLoadedConversation = { id -> sessions[id]?.state?.value },
        updateConversation = { id, conversation -> getOrCreateSession(id).state.value = conversation },
        saveConversation = ::saveConversation,
    )

    private val conversationCompressor = ConversationCompressor(
        providerManager = providerManager,
        getSettings = { settingsStore.settingsFlow.first() },
        saveConversation = ::saveConversation,
        getNotEnoughMessagesText = { getString(Res.string.chat_page_compress_not_enough_messages) },
    )

    private val textTranslator = TextTranslationGenerator(providerManager)
    private val messageTranslator = MessageTranslationManager(
        scope = scope,
        getSettings = { settingsStore.settingsFlow.first() },
        translateText = { settings, source, code, name, onStreamUpdate ->
            textTranslator.translateText(settings, source, code, name, onStreamUpdate).flowOn(Dispatchers.Default)
        },
        getConversation = { getConversationFlow(it).value },
        updateConversation = { id, conversation -> getOrCreateSession(id).state.value = conversation },
        saveConversation = ::saveConversation,
        getLoadingText = { getString(Res.string.translating) },
        onError = { id, error ->
            addError(error, id, title = getString(Res.string.error_title_translate_message))
        },
    )

    // Keeps the Android ordering: shared statics first, then the injected template transformer.
    private val inputTransformers: List<InputMessageTransformer> =
        SHARED_INPUT_TRANSFORMERS + templateTransformer

    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)
    private val generationVersions = mutableMapOf<Uuid, Long>()
    private val createNewConversationOnStart = MutableStateFlow(true)
    private val mutableErrors = MutableStateFlow<List<ChatError>>(emptyList())
    private val mutableGenerationDoneFlow = MutableSharedFlow<Uuid>(extraBufferCapacity = 1)

    override val errors: StateFlow<List<ChatError>> = mutableErrors.asStateFlow()
    override val generationDoneFlow: SharedFlow<Uuid> = mutableGenerationDoneFlow.asSharedFlow()

    init {
        scope.launch {
            booleanPreferenceStore.observe(CREATE_NEW_CONVERSATION_KEY, true).collect {
                createNewConversationOnStart.value = it
            }
        }
    }

    // ---- Session 管理 ----

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        var created = false
        val session = sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id
                ),
                scope = scope,
                onIdle = { removeSession(it) }
            ).also {
                created = true
            }
        }
        if (created) {
            // Collectors may synchronously read the map; publish only after the session has been inserted.
            _sessionsVersion.update { it + 1 }
            Log.i(TAG, "createSession: $conversationId (total: ${sessions.size})")
        }
        return session
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            _sessionsVersion.update { it + 1 }
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    // ---- 引用管理 ----

    override fun addConversationReference(conversationId: Uuid) {
        getOrCreateSession(conversationId).acquire()
    }

    override fun removeConversationReference(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit
    ): Job = scope.launch {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    // ---- 对话状态访问 ----

    override fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        return getOrCreateSession(conversationId).state
    }

    override fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null)
        return session.generationJob
    }

    override fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> {
        val session = sessions[conversationId] ?: return MutableStateFlow(null)
        return session.processingStatus
    }

    override fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(currentSessions.map { s ->
                    s.generationJob.map { job -> s.id to job }
                }) { pairs ->
                    pairs.filter { it.second != null }.toMap()
                }
            }
        }
    }

    override suspend fun initializeConversation(conversationId: Uuid) {
        val stored = conversationRepository.getConversationById(conversationId)
        val state = getOrCreateSession(conversationId).state
        if (stored != null) {
            state.value = stored
            settingsStore.updateAssistant(stored.assistantId)
            return
        }

        val assistant = settingsStore.settingsFlowRaw.first().getCurrentAssistant()
        state.value = Conversation.ofId(
            id = conversationId,
            assistantId = assistant.id,
            messages = assistant.presetMessages.map(UIMessage::toMessageNode),
            newConversation = true,
        )
    }

    override fun rememberConversation(conversationId: Uuid) {
        scope.launch { stringPreferenceStore.set(LAST_CONVERSATION_KEY, conversationId.toString()) }
    }

    override fun shouldCreateNewConversationOnAssistantSwitch(): Boolean =
        createNewConversationOnStart.value

    override fun deleteChatFiles(urls: List<String>) {
        scope.launch { attachmentStore.delete(urls) }
    }

    override fun addError(
        error: Throwable,
        conversationId: Uuid?,
        title: String?,
        solution: ChatErrorSolution?,
    ) {
        if (error is CancellationException) return
        mutableErrors.update { current ->
            current + ChatError(
                title = title,
                error = error,
                conversationId = conversationId,
                solution = solution,
            )
        }
    }

    override fun dismissError(id: Uuid) {
        mutableErrors.update { current -> current.filterNot { it.id == id } }
    }

    override fun clearAllErrors() {
        mutableErrors.value = emptyList()
    }

    override fun sendMessage(
        conversationId: Uuid,
        content: List<UIMessagePart>,
        answer: Boolean,
    ) {
        if (content.isEmptyInputMessage()) return
        startGeneration(conversationId) {
            val conversation = getOrCreateSession(conversationId).state.value
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getAssistantById(conversation.assistantId) ?: settings.getCurrentAssistant()
            val processedParts = preprocessUserInputParts(content, assistant)
            val updated = conversation.copy(
                messageNodes = conversation.messageNodes + UIMessage(
                    role = MessageRole.USER,
                    parts = processedParts,
                ).toMessageNode(),
                updateAt = Clock.System.now(),
            )
            saveConversation(conversationId, updated)
            if (answer) completeConversation(conversationId)
        }
    }

    override suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>,
    ) {
        val conversation = getOrCreateSession(conversationId).state.value
        saveConversation(
            conversationId,
            conversation.copy(
                messageNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { message ->
                            if (message.id == messageId) message.copy(parts = parts) else message
                        },
                    )
                },
                updateAt = Clock.System.now(),
            ),
        )
    }

    override suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int,
    ): Result<Unit> = conversationCompressor.compress(
        conversationId, conversation, additionalPrompt, targetTokens, keepRecentMessages,
    )

    override suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val forkConversation = Conversation(
            id = Uuid.random(),
            assistantId = currentConversation.assistantId,
            messageNodes = copiedNodes,
            customSystemPrompt = currentConversation.customSystemPrompt,
            modeInjectionIds = currentConversation.modeInjectionIds,
            lorebookIds = currentConversation.lorebookIds,
        )

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    private suspend fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        suspend fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = attachmentStore.copyIntoSandbox(url)
            return copied ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    override suspend fun deleteMessage(conversationId: Uuid, message: UIMessage) {
        val conversation = getOrCreateSession(conversationId).state.value
        val updatedNodes = conversation.messageNodes.mapNotNull { node ->
            if (node.messages.none { it.id == message.id }) return@mapNotNull node
            val remaining = node.messages.filterNot { it.id == message.id }
            if (remaining.isEmpty()) {
                null
            } else {
                node.copy(
                    messages = remaining,
                    selectIndex = node.selectIndex.coerceAtMost(remaining.lastIndex),
                )
            }
        }
        saveConversation(
            conversationId,
            conversation.copy(messageNodes = updatedNodes, updateAt = Clock.System.now()),
        )
    }

    override fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean,
    ) {
        startGeneration(conversationId) {
            val conversation = getOrCreateSession(conversationId).state.value
            val nodeIndex = conversation.messageNodes.indexOfFirst { node -> node.messages.any { it.id == message.id } }
            require(nodeIndex >= 0) { "Message ${message.id} is not part of conversation $conversationId" }
            val retainedCount = if (message.role == MessageRole.USER) nodeIndex + 1 else nodeIndex
            saveConversation(
                conversationId,
                conversation.copy(
                    messageNodes = conversation.messageNodes.take(retainedCount),
                    updateAt = Clock.System.now(),
                ),
            )
            if (regenerateAssistantMsg || message.role == MessageRole.USER) {
                completeConversation(conversationId)
            }
        }
    }

    override fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String,
        answer: String?,
    ) {
        startGeneration(conversationId) {
            val conversation = getOrCreateSession(conversationId).state.value
            val approvalState = when {
                answer != null -> ToolApprovalState.Answered(answer)
                approved -> ToolApprovalState.Approved
                else -> ToolApprovalState.Denied(reason)
            }
            val updated = conversation.copy(
                messageNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { message ->
                            message.copy(
                                parts = message.parts.map { part ->
                                    if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                                        part.copy(approvalState = approvalState)
                                    } else {
                                        part
                                    }
                                },
                            )
                        },
                    )
                },
                updateAt = Clock.System.now(),
            )
            saveConversation(conversationId, updated)
            val stillPending = updated.currentMessages.any { message ->
                message.getTools().any { tool -> tool.isPending }
            }
            if (!stillPending) completeConversation(conversationId)
        }
    }

    override suspend fun stopGeneration(conversationId: Uuid) {
        sessions[conversationId]?.getJob()?.cancel()
    }

    override suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val persisted = conversation.copy(newConversation = false)
        if (conversationRepository.existsConversationById(conversationId)) {
            conversationRepository.updateConversation(persisted)
        } else {
            conversationRepository.insertConversation(persisted)
        }
        getOrCreateSession(conversationId).state.value = persisted
    }

    override fun updateConversationState(
        conversationId: Uuid,
        update: (Conversation) -> Conversation,
    ) {
        getOrCreateSession(conversationId).state.update(update)
    }

    override fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguageTag: String,
    ) {
        val language = TranslationLanguage.entries.firstOrNull {
            it.languageTag.equals(targetLanguageTag, ignoreCase = true)
        }
        messageTranslator.translate(
            conversationId = conversationId,
            message = message,
            targetLanguageCode = language?.promptCode ?: targetLanguageTag.replace('-', '_'),
            targetLanguageName = language?.apiName ?: targetLanguageTag,
        )
    }

    override suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean,
    ) {
        titleGenerator.generate(conversationId, conversation, force)
    }

    override suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        suggestionGenerator.generate(conversationId, conversation)
    }

    override fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        messageTranslator.clear(conversationId, messageId)
    }

    override fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean =
        sessions.values.any { it.isGenerating && it.state.value.folderId == folderId }

    override suspend fun deleteFolder(folderId: Uuid) {
        sessions.values.forEach { session ->
            val state = session.state
            if (state.value.folderId == folderId) state.update { it.copy(folderId = null) }
        }
        folderRepository.deleteFolder(folderId)
    }

    override suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        conversationRepository.updateConversationFolderId(conversationId, folderId)
        getOrCreateSession(conversationId).state.update { it.copy(folderId = folderId) }
    }

    private fun startGeneration(conversationId: Uuid, block: suspend () -> Unit) {
        val session = getOrCreateSession(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()
        val generationVersion = (generationVersions[conversationId] ?: 0L) + 1L
        generationVersions[conversationId] = generationVersion
        val job = scope.launch {
            try {
                runCatching { previousJob?.join() }
                block()
                mutableGenerationDoneFlow.emit(conversationId)
            } catch (error: CancellationException) {
                if (generationVersions[conversationId] == generationVersion) {
                    eventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, "", null))
                }
                throw error
            } catch (error: Throwable) {
                if (generationVersions[conversationId] == generationVersion) {
                    eventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, "", null))
                }
                addError(error, conversationId = conversationId)
            } finally {
                if (generationVersions[conversationId] == generationVersion) {
                    session.processingStatus.value = null
                }
            }
        }
        session.setJob(job)
    }

    private suspend fun completeConversation(conversationId: Uuid) {
        val state = getOrCreateSession(conversationId).state
        val settings = settingsStore.settingsFlow.first()
        val conversation = state.value
        val assistant = settings.getAssistantById(conversation.assistantId) ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
            ?: error("No chat model is configured")
        val providerSetting = model.findProvider(settings.providers)
            ?: error("No provider is configured for ${model.displayName}")
        val provider = providerManager.getProviderByType(providerSetting)
        val status = getOrCreateSession(conversationId).processingStatus
        val senderName = assistant.name.ifBlank { model.displayName }
        val systemPrompt = conversation.customSystemPrompt
            ?.takeIf { assistant.allowConversationSystemPrompt && it.isNotBlank() }
            ?: assistant.systemPrompt
        val memoryAssistantId = if (assistant.useGlobalMemory) {
            MemoryRepository.GLOBAL_MEMORY_ID
        } else {
            assistant.id.toString()
        }
        val memories = if (assistant.enableMemory) {
            memoryRepository.getMemoriesOfAssistant(memoryAssistantId)
        } else {
            emptyList()
        }
        // 顺序与 Android 的 ChatService 保持一致
        val tools = buildList {
            if (assistant.enableWebSearch) addAll(createSearchTools(settings))
            addAll(localTools.getTools(assistant.localTools))
            if (assistant.enableRecentChatsReference) {
                addAll(createConversationTools(conversationRepository, assistant.id))
            }
            if (assistant.enableMemory) {
                addAll(
                    buildMemoryTools(
                        json = JsonInstantPretty,
                        onCreation = { content -> memoryRepository.addMemory(memoryAssistantId, content) },
                        onUpdate = { id, content -> memoryRepository.updateContent(id, content) },
                        onDelete = { id -> memoryRepository.deleteMemory(id) },
                    ),
                )
            }
            if (assistant.enabledSkills.isNotEmpty()) {
                addAll(
                    createSkillTools(
                        enabledSkills = assistant.enabledSkills,
                        allSkills = skillManager.listSkills(),
                        skillManager = skillManager,
                    ),
                )
            }
            addAll(buildMcpTools())
        }
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = assistant.customHeaders + model.customHeaders,
            customBody = assistant.customBodies + model.customBodies,
        )

        var completed = false
        var stepCount = 0
        while (stepCount < MAX_GENERATION_STEPS) {
            stepCount++
            val pendingTools = state.value.currentMessages.lastOrNull()
                ?.getTools()
                ?.filterNot { tool -> tool.isExecuted }
                .orEmpty()
            if (pendingTools.isNotEmpty()) {
                val preparedTools = pendingTools.map { tool ->
                    val definition = tools.find { candidate -> candidate.name == tool.toolName }
                    if (tool.approvalState is ToolApprovalState.Auto &&
                        definition?.needsApproval(tool.inputAsJson()) == true
                    ) {
                        tool.copy(approvalState = ToolApprovalState.Pending)
                    } else {
                        tool
                    }
                }
                updateLastTools(state, preparedTools)
                saveConversation(conversationId, state.value)
                if (preparedTools.any { tool -> tool.isPending }) {
                    eventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
                    return@completeConversation
                }

                status.value = "Executing tool"
                val executedTools = preparedTools.map { tool -> executeTool(tool, tools) }
                updateLastTools(state, executedTools)
                saveConversation(conversationId, state.value)
                continue
            }

            val currentMessages = state.value.currentMessages
            val requestMessages = buildList {
                val system = buildString {
                    if (systemPrompt.isNotBlank()) append(systemPrompt)
                    if (assistant.enableMemory) {
                        appendLine()
                        append(buildMemoryPrompt(memories))
                    }
                    tools.forEach { tool ->
                        appendLine()
                        append(tool.systemPrompt(model, currentMessages))
                    }
                }
                if (system.isNotBlank()) add(UIMessage.system(system))
                addAll(currentMessages.limitContext(assistant.contextMessageLimit))
            }.transforms(
                transformers = inputTransformers,
                model = model,
                assistant = assistant,
                settings = settings,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                processingStatus = status,
            )
            status.value = "Generating"
            if (assistant.streamOutput) {
                provider.streamText(providerSetting, requestMessages, params).collect { chunk ->
                    val updatedMessages = state.value.currentMessages.handleMessageChunk(chunk, model)
                    state.value = state.value
                        .updateCurrentMessages(updatedMessages)
                        .copy(updateAt = Clock.System.now())
                    updatedMessages.lastOrNull()?.let { message ->
                        eventBus.tryEmit(AppEvent.ChatGenerationUpdate(conversationId, message, senderName))
                    }
                }
            } else {
                val chunk = provider.generateText(providerSetting, requestMessages, params)
                state.value = state.value
                    .updateCurrentMessages(state.value.currentMessages.handleMessageChunk(chunk, model))
                    .copy(updateAt = Clock.System.now())
            }
            val finishedMessages = state.value.currentMessages
                .let { messages ->
                    if (messages.isEmpty()) messages else messages.dropLast(1) + messages.last().finishReasoning()
                }
                .visualTransforms(OUTPUT_TRANSFORMERS, model, assistant, settings)
                .onGenerationFinish(OUTPUT_TRANSFORMERS, model, assistant, settings)
            state.value = state.value.updateCurrentMessages(finishedMessages)
            saveConversation(conversationId, state.value)
            if (finishedMessages.lastOrNull()?.getTools()?.any { tool -> !tool.isExecuted } != true) {
                completed = true
                break
            }
        }
        check(completed) {
            "MCP tool execution exceeded $MAX_GENERATION_STEPS steps"
        }
        eventBus.tryEmit(
            AppEvent.ChatGenerationEnded(
                conversationId = conversationId,
                senderName = senderName,
                contentPreview = state.value.currentMessages.lastOrNull()?.toText()?.trim()?.take(50),
            ),
        )
        if (completed) {
            val finalConversation = state.value
            launchWithConversationReference(conversationId) { generateTitle(conversationId, finalConversation) }
            launchWithConversationReference(conversationId) { generateSuggestion(conversationId, finalConversation) }
        }
    }

    private fun buildMcpTools(): List<Tool> = mcpManager.getAllAvailableTools().also { available ->
        val invalidNames = available.map { it.second }.distinct().filter { name ->
            name.isEmpty() || !name.all { character ->
                character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9'
            }
        }
        require(invalidNames.isEmpty()) {
            "MCP server names must contain only letters and digits: ${invalidNames.joinToString()}"
        }
    }.map { (serverId, serverName, tool) ->
        Tool(
            name = "mcp__${serverName}__${tool.name}",
            description = tool.description.orEmpty(),
            parameters = { tool.inputSchema },
            needsApproval = { tool.needsApproval },
            execute = { input -> mcpManager.callTool(serverId, tool.name, input.jsonObject) },
        )
    }

    private suspend fun executeTool(
        tool: UIMessagePart.Tool,
        definitions: List<Tool>,
    ): UIMessagePart.Tool = when (val approval = tool.approvalState) {
        is ToolApprovalState.Denied -> tool.copy(
            output = listOf(errorToolOutput("Tool execution denied by user: ${approval.reason}")),
        )

        is ToolApprovalState.Answered -> tool.copy(output = listOf(UIMessagePart.Text(approval.answer)))
        ToolApprovalState.Pending -> tool
        ToolApprovalState.Auto,
        ToolApprovalState.Approved,
            -> try {
                val definition = definitions.find { candidate -> candidate.name == tool.toolName }
                    ?: error("Tool ${tool.toolName} not found")
                tool.copy(output = definition.execute(tool.inputAsJson()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                tool.copy(output = listOf(errorToolOutput(error.message ?: "Tool execution failed")))
            }
    }

    private fun errorToolOutput(message: String): UIMessagePart.Text = UIMessagePart.Text(
        buildJsonObject { put("error", message) }.toString(),
    )

    private fun updateLastTools(
        state: MutableStateFlow<Conversation>,
        tools: List<UIMessagePart.Tool>,
    ) {
        val messages = state.value.currentMessages
        val last = messages.lastOrNull() ?: return
        val updatedLast = last.copy(
            parts = last.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    tools.find { tool -> tool.toolCallId == part.toolCallId } ?: part
                } else {
                    part
                }
            },
        )
        state.value = state.value
            .updateCurrentMessages(messages.dropLast(1) + updatedLast)
            .copy(updateAt = Clock.System.now())
    }

    private companion object {
        const val CREATE_NEW_CONVERSATION_KEY = "create_new_conversation_on_start"
        const val LAST_CONVERSATION_KEY = "lastConversationId"
        const val MAX_GENERATION_STEPS = 32

        /**
         * Transformers that only need shared code. Placeholder, OCR, document and workspace
         * transformers stay behind the Android runtime because they depend on platform APIs.
         */
        private val SHARED_INPUT_TRANSFORMERS: List<InputMessageTransformer> = listOf(
            TimeReminderTransformer,
            PromptInjectionTransformer,
            PlaceholderTransformer,
            DocumentAsPromptTransformer,
            OcrTransformer,
        )

        private val OUTPUT_TRANSFORMERS: List<OutputMessageTransformer> = listOf(
            ThinkTagTransformer,
            Base64ImageToLocalFileTransformer,
            RegexOutputTransformer,
        )
    }
}
