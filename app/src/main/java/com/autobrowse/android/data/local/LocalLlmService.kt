package com.autobrowse.android.data.local

import android.content.Context
import com.autobrowse.android.agent.tools.parseToolArgs
import com.autobrowse.android.data.remote.ChatMessageDto
import com.autobrowse.android.data.remote.LlmCompletion
import com.autobrowse.android.data.remote.ToolCallDto
import com.autobrowse.android.data.remote.ToolCallFunctionDto
import com.autobrowse.android.domain.model.AttachmentPayload
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalLlmService(
    private val context: Context,
    private val modelFileManager: ModelFileManager,
) {
    private val engineMutex = Mutex()
    private var cachedEngine: Engine? = null
    private var cachedConfigKey: String? = null

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
    ): LlmCompletion = withContext(Dispatchers.IO) {
        requireModelFile(config)
        if (attachmentPayload.attachments.isNotEmpty()) {
            throw IllegalStateException("Local LiteRT-LM models do not support attachments yet.")
        }
        if (messages.isEmpty()) {
            throw IllegalStateException("No messages to send.")
        }

        val knownToolNames = tools.map { it.name }.toSet()
        val (history, outgoing) = resolveConversationTurn(messages)

        withConversation(
            config = config,
            systemPrompt = systemPrompt,
            history = history,
            tools = tools,
        ) { conversation ->
            messageToCompletion(
                response = conversation.sendMessage(outgoing),
                knownToolNames = knownToolNames,
            )
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
    ).content ?: throw IllegalStateException("No completion returned from local model.")

    fun close() {
        cachedEngine?.close()
        cachedEngine = null
        cachedConfigKey = null
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
                    temperature = config.temperature.toDouble(),
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
                val created = Engine(
                    EngineConfig(
                        modelPath = config.localModelPath,
                        backend = toBackend(config.backend),
                        cacheDir = context.cacheDir.absolutePath,
                    ),
                )
                created.initialize()
                cachedEngine = created
                cachedConfigKey = key
                created
            }
            block(engine)
        }

    private fun requireModelFile(config: LlmConfig) {
        if (!modelFileManager.modelFileExists(config.localModelPath)) {
            throw IllegalStateException(
                "Model file not found. Download a .litertlm file and import it on the setup screen.",
            )
        }
    }

    private fun engineCacheKey(config: LlmConfig): String =
        "${config.localModelPath}|${config.backend}|${config.localModel}"

    private fun resolveConversationTurn(messages: List<ChatMessageDto>): Pair<List<Message>, Message> {
        val historyDtos = messages.dropLast(1)
        val last = messages.last()
        val history = historyDtos.mapNotNull { dtoToLiteRtMessage(it) }

        return when (last.role) {
            "user" -> history to Message.user(last.content.orEmpty())
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

    private fun dtoToLiteRtMessage(dto: ChatMessageDto): Message? = when (dto.role) {
        "user" -> Message.user(dto.content.orEmpty())
        "assistant" -> {
            val toolCalls = dto.toolCalls.orEmpty().map { call ->
                com.google.ai.edge.litertlm.ToolCall(
                    name = call.function.name,
                    arguments = parseToolArgs(call.function.arguments),
                )
            }
            if (dto.content.isNullOrBlank()) {
                Message.model(toolCalls = toolCalls)
            } else {
                Message.model(contents = Contents.of(dto.content), toolCalls = toolCalls)
            }
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