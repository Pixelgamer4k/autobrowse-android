package com.autobrowse.android.data.local

import android.content.Context
import android.util.Base64
import com.autobrowse.android.agent.tools.parseToolArgs
import com.autobrowse.android.data.remote.ChatMessageDto
import com.autobrowse.android.data.remote.LlmCompletion
import com.autobrowse.android.data.remote.ToolCallDto
import com.autobrowse.android.data.remote.ToolCallFunctionDto
import com.autobrowse.android.domain.model.AttachmentPayload
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LlmBackend
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.ToolDefinition
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class LocalLlmService(
    private val context: Context,
    private val modelFileManager: ModelFileManager,
) {
    private val engineMutex = Mutex()
    private val inferenceMutex = Mutex()
    private var cachedEngine: Engine? = null
    private var cachedConfigKey: String? = null
    private var primedConfigKey: String? = null
    private val generationCancelled = AtomicBoolean(false)

    fun cancelActiveGeneration() {
        generationCancelled.set(true)
    }

    suspend fun warmUp(config: LlmConfig) = prepareEngine(config)

    suspend fun prepareEngine(config: LlmConfig) = withContext(Dispatchers.IO) {
        if (config.localModelPath.isBlank()) return@withContext
        inferenceMutex.withLock {
            runCatching {
                requireModelFile(config)
                ensureEngine(config)
                primeEngine(config)
            }
        }
    }

    suspend fun testConnection(config: LlmConfig): String = withContext(Dispatchers.IO) {
        requireModelFile(config)
        withConversation(config) { conversation ->
            val response = conversation.sendMessage("Reply with OK.")
            val text = messageText(response).orEmpty()
            if (text.isNotBlank()) {
                "Connected on ${config.backend.name} — model replied: ${text.take(80)}"
            } else {
                "Connected on ${config.backend.name} — model responded successfully."
            }
        }
    }

    suspend fun complete(
        config: LlmConfig,
        systemPrompt: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
        onTokenDelta: ((String) -> Unit)? = null,
        compactTools: Boolean = false,
    ): LlmCompletion = withContext(Dispatchers.IO) {
        requireModelFile(config)
        if (messages.isEmpty()) {
            throw IllegalStateException("No messages to send.")
        }

        val knownToolNames = tools.map { it.name }.toSet()
        val (history, outgoing) = resolveConversationTurn(messages, attachmentPayload)

        runInference {
            withConversation(
                config = config,
                systemPrompt = systemPrompt,
                history = history,
                tools = tools,
            ) { conversation ->
                if (onTokenDelta != null) {
                    messageToCompletion(
                        response = streamMessage(conversation, outgoing, onTokenDelta),
                        knownToolNames = knownToolNames,
                    )
                } else {
                    messageToCompletion(
                        response = conversation.sendMessage(outgoing),
                        knownToolNames = knownToolNames,
                    )
                }
            }
        }
    }

    suspend fun chat(
        config: LlmConfig,
        systemPrompt: String,
        userPrompt: String,
        history: List<ChatMessageDto> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
    ): String = complete(
        config = config,
        systemPrompt = systemPrompt,
        messages = history + ChatMessageDto(role = "user", content = userPrompt),
        attachmentPayload = attachmentPayload,
        compactTools = true,
    ).content ?: throw IllegalStateException("No completion returned from local model.")

    fun close() {
        cancelActiveGeneration()
        cachedEngine?.close()
        cachedEngine = null
        cachedConfigKey = null
        primedConfigKey = null
    }

    private suspend fun <T> runInference(block: suspend () -> T): T = inferenceMutex.withLock {
        generationCancelled.set(false)
        try {
            block()
        } catch (e: CancellationException) {
            cancelActiveGeneration()
            throw e
        }
    }

    private suspend fun streamMessage(
        conversation: com.google.ai.edge.litertlm.Conversation,
        outgoing: Message,
        onTokenDelta: (String) -> Unit,
    ): Message {
        var lastMessage: Message? = null
        var previousText = ""
        conversation.sendMessageAsync(outgoing)
            .catch { error ->
                if (!generationCancelled.get()) throw error
            }
            .collect { chunk ->
                if (generationCancelled.get()) {
                    throw CancellationException("Generation cancelled")
                }
                lastMessage = chunk
                val fullText = messageText(chunk).orEmpty()
                if (fullText.length > previousText.length) {
                    val delta = fullText.substring(previousText.length)
                    if (delta.isNotEmpty()) onTokenDelta(delta)
                    previousText = fullText
                }
            }
        return lastMessage ?: throw IllegalStateException("Local model stream returned no message.")
    }

    private suspend fun <T> withConversation(
        config: LlmConfig,
        systemPrompt: String = "You are a helpful assistant.",
        history: List<Message> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
        block: suspend (com.google.ai.edge.litertlm.Conversation) -> T,
    ): T = withEngine(config) { engine ->
        val toolProviders = tools.map { definition -> tool(SchemaOnlyOpenApiTool(definition)) }
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = history,
                tools = toolProviders,
                automaticToolCalling = false,
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = config.temperature.coerceIn(0.2f, 1.0f).toDouble(),
                ),
            ),
        ).use { conversation ->
            block(conversation)
        }
    }

    private suspend fun <T> withEngine(config: LlmConfig, block: suspend (Engine) -> T): T =
        engineMutex.withLock {
            val key = engineCacheKey(config)
            val engine = if (cachedConfigKey == key && cachedEngine != null) {
                cachedEngine!!
            } else {
                cachedEngine?.close()
                val backend = toBackend(config.backend)
                val created = Engine(
                    EngineConfig(
                        modelPath = config.localModelPath,
                        backend = backend,
                        visionBackend = visionBackend(config.backend),
                        // Gemma .litertlm requires audio on CPU even when text runs on GPU/NPU.
                        audioBackend = Backend.CPU(),
                        maxNumTokens = contextTokens(config),
                        cacheDir = context.cacheDir.absolutePath,
                    ),
                )
                created.initialize()
                cachedEngine = created
                cachedConfigKey = key
                primedConfigKey = null
                created
            }
            block(engine)
        }

    private suspend fun primeEngine(config: LlmConfig) {
        val key = engineCacheKey(config)
        if (primedConfigKey == key) return
        runCatching {
            withEngine(config) { engine ->
                engine.createConversation().use { conversation ->
                    conversation.sendMessage("OK")
                }
            }
        }
        primedConfigKey = key
    }

    private suspend fun ensureEngine(config: LlmConfig) {
        withEngine(config) { }
    }

    private fun contextTokens(config: LlmConfig): Int =
        config.maxTokens.coerceIn(4096, LocalLlmCatalog.MAX_CONTEXT_TOKENS)

    private fun visionBackend(backend: LlmBackend): Backend? = when (backend) {
        LlmBackend.GPU, LlmBackend.NPU -> Backend.GPU()
        LlmBackend.CPU -> Backend.CPU()
    }

    private fun requireModelFile(config: LlmConfig) {
        if (!modelFileManager.modelFileExists(config.localModelPath)) {
            throw IllegalStateException(
                "LiteRT model not found. Download a .litertlm file on the setup screen.",
            )
        }
    }

    private fun engineCacheKey(config: LlmConfig): String =
        "${config.localModelPath}|${config.backend}|${config.localModel}|${contextTokens(config)}"

    private fun resolveConversationTurn(
        messages: List<ChatMessageDto>,
        attachmentPayload: AttachmentPayload,
    ): Pair<List<Message>, Message> {
        val historyDtos = messages.dropLast(1)
        val last = messages.last()
        val history = historyDtos.mapNotNull { dtoToLiteRtMessage(it) }

        return when (last.role) {
            "user" -> history to buildUserMessage(last.content.orEmpty(), attachmentPayload)
            "tool" -> {
                val trailingTools = messages.takeLastWhile { it.role == "tool" }
                val beforeTools = messages.dropLast(trailingTools.size)
                val rebuiltHistory = beforeTools.mapNotNull { dtoToLiteRtMessage(it) }
                val toolResponses = trailingTools.map { dto ->
                    Content.ToolResponse(
                        name = dto.name ?: "unknown",
                        response = dto.content.orEmpty(),
                    )
                }
                rebuiltHistory to Message.tool(Contents.of(toolResponses))
            }
            else -> throw IllegalStateException(
                "Unsupported trailing message role for local completion: ${last.role}",
            )
        }
    }

    private fun buildUserMessage(text: String, attachmentPayload: AttachmentPayload): Message {
        val imageBytes = firstImageBytes(attachmentPayload)
        return if (imageBytes != null) {
            Message.user(
                Contents.of(
                    Content.ImageBytes(imageBytes),
                    Content.Text(text),
                ),
            )
        } else {
            Message.user(text)
        }
    }

    private fun dtoToLiteRtMessage(dto: ChatMessageDto): Message? = when (dto.role) {
        "user" -> Message.user(dto.content.orEmpty())
        "assistant" -> {
            val toolCalls = dto.toolCalls.orEmpty().map { call ->
                com.google.ai.edge.litertlm.ToolCall(
                    name = call.function.name,
                    arguments = parseToolArgs(call.function.arguments),
                )
            }
            val contents = dto.content?.takeIf { it.isNotBlank() }?.let { Contents.of(it) } ?: Contents.of("")
            Message.model(contents = contents, toolCalls = toolCalls)
        }
        "tool" -> Message.tool(
            Contents.of(
                Content.ToolResponse(
                    name = dto.name ?: "unknown",
                    response = dto.content.orEmpty(),
                ),
            ),
        )
        "system" -> null
        else -> null
    }

    private fun messageToCompletion(response: Message, knownToolNames: Set<String>): LlmCompletion {
        val nativeToolCalls = response.toolCalls.mapIndexed { index, toolCall ->
            toToolCallDto(toolCall.name, toolCall.arguments, index)
        }

        val rawText = messageText(response)
        val textToolCalls = if (nativeToolCalls.isEmpty() && !rawText.isNullOrBlank()) {
            parseGemmaToolCallsFromText(rawText, knownToolNames)
        } else {
            emptyList()
        }

        val toolCalls = nativeToolCalls.ifEmpty { textToolCalls }
        val content = if (toolCalls.isNotEmpty()) {
            stripToolCallMarkup(rawText).ifBlank { null }
        } else {
            rawText
        }

        return LlmCompletion(
            content = content,
            toolCalls = toolCalls,
            finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
        )
    }

    private fun toToolCallDto(name: String, arguments: Map<String, Any?>, index: Int): ToolCallDto =
        ToolCallDto(
            id = "call_${index}_$name",
            function = ToolCallFunctionDto(
                name = name,
                arguments = JSONObject(arguments).toString(),
            ),
        )

    private fun parseGemmaToolCallsFromText(text: String, knownToolNames: Set<String>): List<ToolCallDto> {
        val patterns = listOf(
            Regex("""<\|tool_call>call:(\w+)\{(.*?)\}<tool_call\|>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""call:(\w+)\{([^}]*)\}"""),
        )
        val argPattern = Regex("""(\w+):(?:<\|"\|>(.*?)<\|"\|>|"([^"]*)"|'([^']*)'|([^,}]*))""")

        val results = linkedMapOf<String, ToolCallDto>()
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val name = match.groupValues[1]
                if (knownToolNames.isNotEmpty() && name !in knownToolNames) continue
                val argsBlock = match.groupValues[2]
                val arguments = linkedMapOf<String, Any?>()
                for (argMatch in argPattern.findAll(argsBlock)) {
                    val key = argMatch.groupValues[1]
                    val value = argMatch.groupValues.drop(2).firstOrNull { it.isNotEmpty() }?.trim().orEmpty()
                    arguments[key] = castGemmaArgument(value)
                }
                val id = "text_${results.size}_$name"
                results[id] = ToolCallDto(
                    id = id,
                    function = ToolCallFunctionDto(
                        name = name,
                        arguments = JSONObject(arguments).toString(),
                    ),
                )
            }
        }
        return results.values.toList()
    }

    private fun castGemmaArgument(value: String): Any? {
        if (value.equals("true", ignoreCase = true)) return true
        if (value.equals("false", ignoreCase = true)) return false
        value.toIntOrNull()?.let { return it }
        value.toDoubleOrNull()?.let { return it }
        return value
    }

    private fun stripToolCallMarkup(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(Regex("""<\|tool_call>.*?</tool_call\|>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""call:\w+\{[^}]*\}"""), "")
            .trim()
    }

    private fun messageText(message: Message): String? {
        val text = message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
            .trim()
        return text.ifBlank { message.toString().trim().ifBlank { null } }
    }

    private fun firstImageBytes(payload: AttachmentPayload): ByteArray? {
        payload.attachments.forEach { attachment ->
            attachment.imageBase64Parts.forEach { dataUrl ->
                decodeBase64Image(dataUrl)?.let { return it }
            }
        }
        return null
    }

    private fun decodeBase64Image(dataUrl: String): ByteArray? = runCatching {
        val payload = dataUrl.substringAfter(",", dataUrl)
        Base64.decode(payload, Base64.DEFAULT)
    }.getOrNull()

    private fun toBackend(backend: LlmBackend): Backend = when (backend) {
        LlmBackend.CPU -> Backend.CPU()
        LlmBackend.GPU -> Backend.GPU()
        LlmBackend.NPU -> Backend.NPU(
            nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
        )
    }

    private class SchemaOnlyOpenApiTool(
        private val definition: ToolDefinition,
    ) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String =
            JSONObject()
                .put("name", definition.name)
                .put("description", definition.description)
                .put("parameters", JSONObject(definition.parametersJson))
                .toString()

        override fun execute(paramsJsonString: String): String = "{}"
    }
}