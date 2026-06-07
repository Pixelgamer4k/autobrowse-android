package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.agent.tools.ToolExecutionContext
import com.autobrowse.android.agent.tools.ToolRegistry
import com.autobrowse.android.agent.tools.parseToolArgs
import com.autobrowse.android.agent.trajectory.SelfImprovementEngine
import com.autobrowse.android.agent.trajectory.TrajectoryStore
import com.autobrowse.android.data.remote.ChatMessageDto
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.AgentConversationResult
import com.autobrowse.android.domain.model.AgentPhase
import com.autobrowse.android.domain.model.AgentProgress
import com.autobrowse.android.domain.model.AgentRole
import com.autobrowse.android.domain.model.AgentTurn
import com.autobrowse.android.domain.model.AutoBrowseRequest
import com.autobrowse.android.domain.model.AutomationTask
import com.autobrowse.android.domain.model.BrowserContext
import com.autobrowse.android.domain.model.ChatMessage
import com.autobrowse.android.domain.model.TaskStatus
import com.autobrowse.android.domain.model.ToolCall
import com.autobrowse.android.domain.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class AgentLoop(
    private val repository: AutobrowseRepository,
    private val llmApi: LlmApiService,
    private val toolRegistry: ToolRegistry,
    private val promptBuilder: PromptBuilder,
    private val memoryManager: MemoryManager,
    private val contextCompressor: ContextCompressor,
    private val trajectoryStore: TrajectoryStore,
    private val selfImprovementEngine: SelfImprovementEngine,
    private val maxIterations: Int = 12,
) {
    private val _progress = MutableStateFlow(AgentProgress(AgentPhase.IDLE, 0, maxIterations))
    val progress: StateFlow<AgentProgress> = _progress.asStateFlow()
    private val cancelRequested = AtomicBoolean(false)

    fun requestCancel() {
        cancelRequested.set(true)
        _progress.value = AgentProgress(
            phase = AgentPhase.IDLE,
            iteration = _progress.value.iteration,
            maxIterations = maxIterations,
            message = "Stopping…",
        )
    }

    private fun ensureNotCancelled() {
        coroutineContext.ensureActive()
        if (cancelRequested.get()) {
            throw CancellationException("Stopped by user")
        }
    }

    suspend fun runConversation(
        request: AutoBrowseRequest,
        browserContext: BrowserContext,
    ): AgentConversationResult {
        val taskId = UUID.randomUUID().toString()
        val task = AutomationTask(
            id = taskId,
            title = request.prompt.take(48),
            description = request.prompt,
            status = TaskStatus.RUNNING,
            progress = 0.05f,
        )
        repository.saveTask(task, request.sessionId)

        val displayPrompt = request.prompt.ifBlank {
            if (request.attachmentPayload.attachments.isNotEmpty()) "Analyze the attached file(s)"
            else request.prompt
        }
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = request.sessionId,
            role = AgentRole.USER,
            content = displayPrompt,
            attachments = request.attachmentPayload.attachments.map { it.attachment },
        )
        repository.saveChatMessage(userMessage)

        cancelRequested.set(false)

        return try {
            runConversationLoop(
                request = request,
                browserContext = browserContext,
                task = task,
                taskId = taskId,
                displayPrompt = displayPrompt,
            )
        } catch (e: CancellationException) {
            repository.updateTask(
                task.copy(status = TaskStatus.CANCELLED, progress = _progress.value.iteration.toFloat() / maxIterations),
                request.sessionId,
            )
            repository.saveChatMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = request.sessionId,
                    role = AgentRole.AGENT,
                    content = "Stopped.",
                ),
            )
            _progress.value = AgentProgress(AgentPhase.IDLE, 0, maxIterations)
            cancelRequested.set(false)
            AgentConversationResult(
                success = false,
                finalResponse = "",
                turns = emptyList(),
                error = "Stopped by user",
            )
        }
    }

    private suspend fun runConversationLoop(
        request: AutoBrowseRequest,
        browserContext: BrowserContext,
        task: AutomationTask,
        taskId: String,
        displayPrompt: String,
    ): AgentConversationResult = runCatching {
            ensureNotCancelled()
            _progress.value = AgentProgress(AgentPhase.THINKING, 0, maxIterations, message = "Planning…")

            val config = repository.getLlmConfig()
            val prefetched = memoryManager.prefetch(request.prompt)
            val strategies = selfImprovementEngine.getRelevantStrategies(
                request.prompt,
                browserContext.pageUrl,
            )
            val systemPrompt = promptBuilder.build(
                prefetchedMemory = prefetched,
                strategies = strategies,
                pageUrl = browserContext.pageUrl,
                enabledToolNames = toolRegistry.definitions().map { it.name },
            )

            var history = repository.getRecentChatHistory(request.sessionId, 30)
            val (compressedHistory, summary) = contextCompressor.maybeCompress(config, history)
            history = compressedHistory
            if (summary != null) {
                repository.saveCompressionSummary(request.sessionId, summary)
            }

            val budget = IterationBudget(maxIterations)
            val loopMessages = contextCompressor.toApiHistory(history.dropLast(1)).toMutableList().apply {
                add(ChatMessageDto(role = "user", content = displayPrompt))
            }
            val turns = mutableListOf<AgentTurn>()
            val toolContext = ToolExecutionContext(
                sessionId = request.sessionId,
                pageUrl = browserContext.pageUrl,
                pageHtml = browserContext.pageHtml,
                pageText = browserContext.pageText,
            )

            var finalResponse: String? = null

            while (budget.consume()) {
                ensureNotCancelled()
                val iteration = budget.used()
                _progress.value = AgentProgress(
                    phase = AgentPhase.THINKING,
                    iteration = iteration,
                    maxIterations = maxIterations,
                    message = "Thinking (turn $iteration)…",
                )
                repository.updateTask(
                    task.copy(progress = iteration.toFloat() / maxIterations),
                    request.sessionId,
                )

                val completion = llmApi.complete(
                    config = config,
                    systemPrompt = systemPrompt,
                    messages = loopMessages,
                    tools = toolRegistry.definitions(),
                    attachmentPayload = if (iteration == 1) request.attachmentPayload else com.autobrowse.android.domain.model.AttachmentPayload(),
                )
                ensureNotCancelled()

                if (completion.toolCalls.isEmpty()) {
                    finalResponse = completion.content?.trim().orEmpty()
                    turns += AgentTurn(
                        iteration = iteration,
                        assistantContent = finalResponse,
                    )
                    break
                }

                val toolCalls = completion.toolCalls.map { dto ->
                    ToolCall(dto.id, dto.function.name, dto.function.arguments)
                }

                loopMessages += ChatMessageDto(
                    role = "assistant",
                    content = completion.content,
                    toolCalls = completion.toolCalls,
                )

                val toolResults = mutableListOf<ToolResult>()
                for (call in toolCalls) {
                    ensureNotCancelled()
                    _progress.value = AgentProgress(
                        phase = AgentPhase.EXECUTING_TOOL,
                        iteration = iteration,
                        maxIterations = maxIterations,
                        currentTool = call.name,
                        message = "Running ${call.name}…",
                    )
                    val args = parseToolArgs(call.argumentsJson)
                    val result = toolRegistry.dispatch(call.name, args, toolContext)
                    toolResults += ToolResult(call.id, call.name, result.output, result.success)
                    loopMessages += ChatMessageDto(
                        role = "tool",
                        content = result.output,
                        toolCallId = call.id,
                        name = call.name,
                    )
                }

                turns += AgentTurn(
                    iteration = iteration,
                    toolCalls = toolCalls,
                    toolResults = toolResults,
                    assistantContent = completion.content,
                )

                if (budget.exhausted() && finalResponse == null) {
                    finalResponse = "Reached iteration limit. Partial results available from tool outputs."
                }
            }

            finalResponse = finalResponse?.ifBlank {
                "Task completed using ${turns.sumOf { it.toolCalls.size }} tool calls."
            } ?: "No response generated."

            val agentMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = request.sessionId,
                role = AgentRole.AGENT,
                content = finalResponse,
            )
            repository.saveChatMessage(agentMessage)

            _progress.value = AgentProgress(AgentPhase.REFLECTING, budget.used(), maxIterations, message = "Learning…")
            val memoriesLearned = memoryManager.syncTurn(request.sessionId, request.prompt, finalResponse)
            val strategiesUpdated = selfImprovementEngine.reflectAndImprove(
                prompt = request.prompt,
                success = turns.none { turn -> turn.toolResults.any { !it.success } },
                turns = turns,
                pageUrl = browserContext.pageUrl,
            )
            trajectoryStore.save(
                sessionId = request.sessionId,
                taskId = taskId,
                prompt = request.prompt,
                success = true,
                turns = turns,
            )

            repository.updateTask(
                task.copy(status = TaskStatus.COMPLETED, progress = 1f),
                request.sessionId,
            )
            _progress.value = AgentProgress(AgentPhase.IDLE, budget.used(), maxIterations)

            AgentConversationResult(
                success = true,
                finalResponse = finalResponse,
                turns = turns,
                actions = toolContext.browserActions,
                extractedData = toolContext.extractedData,
                iterationsUsed = budget.used(),
                memoriesLearned = memoriesLearned,
                strategiesUpdated = strategiesUpdated,
            )
        }.getOrElse { error ->
            trajectoryStore.save(
                sessionId = request.sessionId,
                taskId = taskId,
                prompt = request.prompt,
                success = false,
                turns = emptyList(),
                reflection = error.message,
            )
            repository.updateTask(task.copy(status = TaskStatus.FAILED, progress = 1f), request.sessionId)
            val errorMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = request.sessionId,
                role = AgentRole.AGENT,
                content = "Error: ${error.message}",
            )
            repository.saveChatMessage(errorMessage)
            _progress.value = AgentProgress(AgentPhase.IDLE, 0, maxIterations)
            AgentConversationResult(
                success = false,
                finalResponse = "",
                turns = emptyList(),
                error = error.message,
            )
        }
}