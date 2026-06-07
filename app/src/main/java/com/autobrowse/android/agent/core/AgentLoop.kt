package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.agent.tools.ToolExecutionContext
import com.autobrowse.android.agent.tools.ToolRegistry
import com.autobrowse.android.agent.tools.parseToolArgs
import com.autobrowse.android.browser.TabManager
import com.autobrowse.android.domain.model.AttachmentPayload
import com.autobrowse.android.agent.training.PostTaskSkillLearner
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
import com.autobrowse.android.domain.model.LlmProvider
import com.autobrowse.android.domain.model.TaskStatus
import com.autobrowse.android.domain.model.ToolCall
import com.autobrowse.android.domain.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
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
    private val postTaskSkillLearner: PostTaskSkillLearner? = null,
    private val tabManager: TabManager? = null,
    private val maxIterations: Int = 20,
) {
    private val _progress = MutableStateFlow(AgentProgress(AgentPhase.IDLE, 0, maxIterations))
    val progress: StateFlow<AgentProgress> = _progress.asStateFlow()
    private val cancelRequested = AtomicBoolean(false)

    fun requestCancel() {
        cancelRequested.set(true)
        llmApi.cancelLocalGeneration()
        _progress.value = AgentProgress(
            phase = AgentPhase.IDLE,
            iteration = _progress.value.iteration,
            maxIterations = maxIterations,
            message = "Stopping…",
        )
    }

    private suspend fun ensureNotCancelled() {
        currentCoroutineContext().ensureActive()
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

        val rawPrompt = request.prompt.ifBlank {
            if (request.attachmentPayload.attachments.isNotEmpty()) "Analyze the attached file(s)"
            else request.prompt
        }
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = request.sessionId,
            role = AgentRole.USER,
            content = rawPrompt,
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
                rawPrompt = rawPrompt,
            )
        } catch (e: CancellationException) {
            llmApi.cancelLocalGeneration()
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
        rawPrompt: String,
    ): AgentConversationResult = runCatching {
            ensureNotCancelled()
            _progress.value = AgentProgress(AgentPhase.THINKING, 0, maxIterations, message = "Planning…")

            val config = repository.getLlmConfig()
            val allToolDefs = toolRegistry.definitions()
            val isLocal = config.provider == LlmProvider.LOCAL
            val prefetched = memoryManager.prefetch(rawPrompt)
            val strategies = selfImprovementEngine.getRelevantStrategies(
                rawPrompt,
                browserContext.pageUrl,
            )
            val systemPrompt = promptBuilder.build(
                prefetchedMemory = prefetched,
                strategies = strategies,
                pageUrl = browserContext.pageUrl,
                enabledToolNames = if (isLocal) {
                    LocalToolSelector.select(rawPrompt, allToolDefs, iteration = 1).map { it.name }
                } else {
                    allToolDefs.map { it.name }
                },
                userPrompt = rawPrompt,
            )

            var history = repository.getRecentChatHistory(request.sessionId, 30)
            val (compressedHistory, summary) = contextCompressor.maybeCompress(config, history)
            history = compressedHistory
            if (summary != null) {
                repository.saveCompressionSummary(request.sessionId, summary)
            }

            val budget = IterationBudget(maxIterations)
            val loopMessages = contextCompressor.toApiHistory(history.dropLast(1)).toMutableList().apply {
                add(ChatMessageDto(role = "user", content = rawPrompt))
            }
            val turns = mutableListOf<AgentTurn>()
            val toolContext = ToolExecutionContext(
                sessionId = request.sessionId,
                pageUrl = browserContext.pageUrl,
                pageHtml = browserContext.pageHtml,
                pageText = browserContext.pageText,
                activeTabId = tabManager?.activeTabId(),
                tabManager = tabManager,
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
                    streamPreview = "",
                )
                repository.updateTask(
                    task.copy(progress = iteration.toFloat() / maxIterations),
                    request.sessionId,
                )

                val visionPayload = AttachmentPayload.fromVisionBase64(toolContext.pendingVisionImages)
                val attachmentPayload = when {
                    iteration == 1 -> request.attachmentPayload.merge(visionPayload)
                    visionPayload.hasVisionContent -> visionPayload
                    else -> AttachmentPayload()
                }

                val activeTools = if (isLocal) {
                    LocalToolSelector.select(rawPrompt, allToolDefs, iteration)
                } else {
                    allToolDefs
                }

                val streamBuffer = StringBuilder()
                var lastStreamUiUpdate = 0L
                val completion = llmApi.complete(
                    config = config,
                    systemPrompt = systemPrompt,
                    messages = loopMessages,
                    tools = activeTools,
                    attachmentPayload = attachmentPayload,
                    compactTools = isLocal,
                    onTokenDelta = { delta ->
                        streamBuffer.append(delta)
                        val now = System.currentTimeMillis()
                        if (now - lastStreamUiUpdate >= 120L) {
                            lastStreamUiUpdate = now
                            _progress.value = _progress.value.copy(
                                streamPreview = formatThinkingPreview(streamBuffer.toString()),
                            )
                        }
                    },
                )
                if (streamBuffer.isNotEmpty()) {
                    _progress.value = _progress.value.copy(
                        streamPreview = formatThinkingPreview(streamBuffer.toString()),
                    )
                }
                toolContext.pendingVisionImages.clear()
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
                val priorToolNames = turns.flatMap { it.toolCalls }.map { it.name }
                val browserFirst = SmartExecutionAdvisor.shouldSerializeBrowserTools(toolCalls)
                val orderedCalls = if (browserFirst) {
                    toolCalls.sortedBy { if (it.name.startsWith("browser_")) 0 else 1 }
                } else {
                    toolCalls
                }

                suspend fun executeOne(call: ToolCall): Pair<ToolCall, ToolResult> {
                    ensureNotCancelled()
                    _progress.value = AgentProgress(
                        phase = AgentPhase.EXECUTING_TOOL,
                        iteration = iteration,
                        maxIterations = maxIterations,
                        currentTool = call.name,
                        message = "Running ${call.name}…",
                        streamPreview = "",
                    )
                    val args = parseToolArgs(call.argumentsJson)
                    val raw = toolRegistry.dispatch(call.name, args, toolContext)
                    val output = SmartExecutionAdvisor.augmentToolOutput(
                        call = call,
                        result = ToolResult(call.id, call.name, raw.output, raw.success),
                        context = toolContext,
                        userPrompt = rawPrompt,
                        iteration = iteration,
                        priorToolNames = priorToolNames,
                    )
                    return call to ToolResult(call.id, call.name, output, raw.success)
                }

                if (browserFirst) {
                    orderedCalls.forEach { call ->
                        val (c, result) = executeOne(call)
                        toolResults += result
                        loopMessages += ChatMessageDto(
                            role = "tool",
                            content = result.output,
                            toolCallId = c.id,
                            name = c.name,
                        )
                    }
                } else {
                    coroutineScope {
                        orderedCalls.map { call ->
                            async { executeOne(call) }
                        }.awaitAll().forEach { (call, result) ->
                            toolResults += result
                            loopMessages += ChatMessageDto(
                                role = "tool",
                                content = result.output,
                                toolCallId = call.id,
                                name = call.name,
                            )
                        }
                    }
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
            val taskSuccess = turns.none { turn -> turn.toolResults.any { !it.success } }
            val memoriesLearned = memoryManager.syncTurn(request.sessionId, request.prompt, finalResponse)
            val strategiesUpdated = selfImprovementEngine.reflectAndImprove(
                prompt = request.prompt,
                success = taskSuccess,
                turns = turns,
                pageUrl = browserContext.pageUrl,
            )
            val skillsLearned = postTaskSkillLearner?.learnFromTask(
                prompt = rawPrompt,
                success = taskSuccess,
                turns = turns,
                pageUrl = browserContext.pageUrl,
            ) ?: 0
            trajectoryStore.save(
                sessionId = request.sessionId,
                taskId = taskId,
                prompt = request.prompt,
                success = taskSuccess,
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
                skillsLearned = skillsLearned,
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

    private fun formatThinkingPreview(raw: String): String {
        val thinkOpen = "<think>"
        val thinkClose = "</think>"
        val start = raw.indexOf(thinkOpen)
        if (start >= 0) {
            val contentStart = start + thinkOpen.length
            val end = raw.indexOf(thinkClose, contentStart)
            val thinking = if (end >= 0) {
                raw.substring(contentStart, end)
            } else {
                raw.substring(contentStart)
            }
            return thinking.trim().take(2_500)
        }
        return raw
            .replace(Regex("""<tool_call>.*""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(thinkOpen, "")
            .replace(thinkClose, "")
            .trim()
            .takeLast(1_200)
            .trim()
    }
}