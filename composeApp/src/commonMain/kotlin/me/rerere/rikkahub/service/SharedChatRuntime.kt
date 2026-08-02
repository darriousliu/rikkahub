package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
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
import me.rerere.rikkahub.data.ai.mcp.McpRuntime
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Portable chat runtime used by the iOS and Desktop product shells.
 *
 * Android keeps its feature-complete [ChatService]. This implementation owns the common core:
 * persisted conversation state, provider requests, streaming updates, cancellation and basic
 * message/folder operations. Android-only workspace, local-tool and attachment cleanup remain
 * behind the Android runtime and platform UI adapters.
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
    private val mcpRuntime: McpRuntime,
) : ChatRuntime {
    private val conversations = mutableMapOf<Uuid, MutableStateFlow<Conversation>>()
    private val processingStatuses = mutableMapOf<Uuid, MutableStateFlow<String?>>()
    private val generationVersions = mutableMapOf<Uuid, Long>()
    private val jobs = MutableStateFlow<Map<Uuid, Job?>>(emptyMap())
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

    override fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        conversationState(conversationId).asStateFlow()

    override fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        jobs.map { it[conversationId] }

    override fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        processingStatuses.getOrPut(conversationId) { MutableStateFlow(null) }.asStateFlow()

    override fun getConversationJobs(): Flow<Map<Uuid, Job?>> = jobs

    override fun addConversationReference(conversationId: Uuid) {
        conversationState(conversationId)
    }

    override fun removeConversationReference(conversationId: Uuid) = Unit

    override suspend fun initializeConversation(conversationId: Uuid) {
        val stored = conversationRepository.getConversationById(conversationId)
        val state = conversationState(conversationId)
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
            val conversation = conversationState(conversationId).value
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getAssistantById(conversation.assistantId) ?: settings.getCurrentAssistant()
            val processedParts = content.map { part ->
                if (part is UIMessagePart.Text) {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false,
                        ),
                    )
                } else {
                    part
                }
            }
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
        val conversation = conversationState(conversationId).value
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
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Context compression is not available on this platform"),
    )

    override suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid,
    ): Conversation {
        val source = conversationState(conversationId).value
        val nodeIndex = source.messageNodes.indexOfFirst { node -> node.messages.any { it.id == messageId } }
        require(nodeIndex >= 0) { "Message $messageId is not part of conversation $conversationId" }
        val fork = source.copy(
            id = Uuid.random(),
            title = source.title.takeIf(String::isNotBlank)?.let { "$it (Fork)" }.orEmpty(),
            messageNodes = source.messageNodes.take(nodeIndex + 1).map { node -> node.copy(id = Uuid.random()) },
            createAt = Clock.System.now(),
            updateAt = Clock.System.now(),
            newConversation = false,
        )
        conversationRepository.insertConversation(fork)
        conversations[fork.id] = MutableStateFlow(fork)
        return fork
    }

    override suspend fun deleteMessage(conversationId: Uuid, message: UIMessage) {
        val conversation = conversationState(conversationId).value
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
            val conversation = conversationState(conversationId).value
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
            val conversation = conversationState(conversationId).value
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
        jobs.value[conversationId]?.cancel()
    }

    override suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val persisted = conversation.copy(newConversation = false)
        if (conversationRepository.existsConversationById(conversationId)) {
            conversationRepository.updateConversation(persisted)
        } else {
            conversationRepository.insertConversation(persisted)
        }
        conversationState(conversationId).value = persisted
    }

    override fun updateConversationState(
        conversationId: Uuid,
        update: (Conversation) -> Conversation,
    ) {
        conversationState(conversationId).update(update)
    }

    override fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguageTag: String,
    ) {
        addError(
            UnsupportedOperationException("Message translation is not available on this platform"),
            conversationId = conversationId,
        )
    }

    override suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean,
    ) {
        if (!force && conversation.title.isNotBlank()) return
        val title = conversation.currentMessages
            .firstOrNull { it.role == MessageRole.USER }
            ?.toText()
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.take(TITLE_MAX_LENGTH)
            .orEmpty()
        if (title.isNotBlank()) saveConversation(conversationId, conversation.copy(title = title))
    }

    override suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) = Unit

    override fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        updateConversationState(conversationId) { conversation ->
            conversation.copy(
                messageNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { message ->
                            if (message.id == messageId) message.copy(translation = null) else message
                        },
                    )
                },
            )
        }
    }

    override fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean =
        jobs.value.any { (conversationId, job) ->
            job?.isActive == true && conversations[conversationId]?.value?.folderId == folderId
        }

    override suspend fun deleteFolder(folderId: Uuid) {
        conversations.values.forEach { state ->
            if (state.value.folderId == folderId) state.update { it.copy(folderId = null) }
        }
        folderRepository.deleteFolder(folderId)
    }

    override suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        conversationRepository.updateConversationFolderId(conversationId, folderId)
        conversationState(conversationId).update { it.copy(folderId = folderId) }
    }

    private fun conversationState(conversationId: Uuid): MutableStateFlow<Conversation> =
        conversations.getOrPut(conversationId) {
            MutableStateFlow(
                Conversation.ofId(
                    id = conversationId,
                    assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id,
                    newConversation = true,
                ),
            )
        }

    private fun startGeneration(conversationId: Uuid, block: suspend () -> Unit) {
        jobs.value[conversationId]?.cancel()
        val generationVersion = (generationVersions[conversationId] ?: 0L) + 1L
        generationVersions[conversationId] = generationVersion
        val job = scope.launch {
            try {
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
                    processingStatuses[conversationId]?.value = null
                    jobs.update { current -> current + (conversationId to null) }
                }
            }
        }
        jobs.update { current -> current + (conversationId to job) }
    }

    private suspend fun completeConversation(conversationId: Uuid) {
        val state = conversationState(conversationId)
        val settings = settingsStore.settingsFlow.first()
        val conversation = state.value
        val assistant = settings.getAssistantById(conversation.assistantId) ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
            ?: error("No chat model is configured")
        val providerSetting = model.findProvider(settings.providers)
            ?: error("No provider is configured for ${model.displayName}")
        val provider = providerManager.getProviderByType(providerSetting)
        val status = processingStatuses.getOrPut(conversationId) { MutableStateFlow(null) }
        val senderName = assistant.name.ifBlank { model.displayName }
        val systemPrompt = conversation.customSystemPrompt
            ?.takeIf { assistant.allowConversationSystemPrompt && it.isNotBlank() }
            ?: assistant.systemPrompt
        val tools = buildMcpTools()
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

            val requestMessages = buildList {
                if (systemPrompt.isNotBlank()) add(UIMessage.system(systemPrompt))
                addAll(state.value.currentMessages.limitContext(assistant.contextMessageLimit))
            }
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
            val finishedMessages = state.value.currentMessages.let { messages ->
                if (messages.isEmpty()) messages else messages.dropLast(1) + messages.last().finishReasoning()
            }
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
        if (completed) generateTitle(conversationId, state.value)
    }

    private fun buildMcpTools(): List<Tool> = mcpRuntime.getAllAvailableTools().also { available ->
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
            execute = { input -> mcpRuntime.callTool(serverId, tool.name, input.jsonObject) },
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
        const val TITLE_MAX_LENGTH = 80
        const val MAX_GENERATION_STEPS = 32
    }
}
