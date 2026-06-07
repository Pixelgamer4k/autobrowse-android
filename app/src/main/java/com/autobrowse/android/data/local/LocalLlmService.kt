package com.autobrowse.android.data.local

import android.util.Base64
import com.autobrowse.android.data.remote.ChatMessageDto
import com.autobrowse.android.data.remote.LlmCompletion
import com.autobrowse.android.data.remote.ToolCallDto
import com.autobrowse.android.data.remote.ToolCallFunctionDto
import com.autobrowse.android.domain.model.AttachmentPayload
import com.autobrowse.android.domain.model.LlmBackend
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.ToolDefinition
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.MultimodalBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
class LocalLlmService(
    private val modelFileManager: ModelFileManager,
) {
    private val engineMutex = Mutex()
    private var cachedConfigKey: String? = null

    suspend fun testConnection(config: LlmConfig): String = withContext(Dispatchers.IO) {
        requireModelFiles(config)
        ensureEngine(config)
        val response = generateFromMessages(
            config = config,
            systemPrompt = "You are a helpful assistant.",
            messages = listOf(ChatMessageDto(role = "user", content = "Reply with OK.")),
            tools = emptyList(),
            attachmentPayload = AttachmentPayload(),
        )
        val text = response.content.orEmpty()
        if (text.isNotBlank()) {
            "Connected on ${config.backend.name} — model replied: ${text.take(80)}"
        } else {
            "Connected on ${config.backend.name} — model responded successfully."
        }
    }

    suspend fun complete(
        config: LlmConfig,
        systemPrompt: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
        onTokenDelta: ((String) -> Unit)? = null,
    ): LlmCompletion = withContext(Dispatchers.IO) {
        requireModelFiles(config)
        if (messages.isEmpty()) {
            throw IllegalStateException("No messages to send.")
        }
        ensureEngine(config)
        generateFromMessages(config, systemPrompt, messages, tools, attachmentPayload, onTokenDelta)
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
        MultimodalBridge.release()
        LlamaBridge.shutdown()
        cachedConfigKey = null
    }

    private suspend fun generateFromMessages(
        config: LlmConfig,
        systemPrompt: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition>,
        attachmentPayload: AttachmentPayload,
        onTokenDelta: ((String) -> Unit)? = null,
    ): LlmCompletion {
        val enrichedSystem = augmentSystemPrompt(systemPrompt, tools)
        val visionContext = analyzeVisionContext(config, messages, attachmentPayload)
        val promptMessages = buildPromptMessages(messages, visionContext)
        val prompt = LlamaBridge.applyChatTemplate(
            messages = listOf("system" to enrichedSystem) + promptMessages,
            addAssistantPrefix = true,
        ) ?: fallbackPrompt(enrichedSystem, promptMessages)

        val rawText = if (onTokenDelta != null) {
            generateStream(prompt, onTokenDelta)
        } else {
            LlamaBridge.generate(prompt)
        }
        return parseCompletion(rawText, tools.map { it.name }.toSet())
    }

    private fun generateStream(prompt: String, onTokenDelta: (String) -> Unit): String {
        val builder = StringBuilder()
        val latch = java.util.concurrent.CountDownLatch(1)
        var error: String? = null
        LlamaBridge.generateStream(
            prompt,
            object : GenStream {
                override fun onDelta(text: String) {
                    builder.append(text)
                    onTokenDelta(text)
                }

                override fun onComplete() {
                    latch.countDown()
                }

                override fun onError(message: String) {
                    error = message
                    latch.countDown()
                }
            },
        )
        latch.await()
        error?.let { throw IllegalStateException("Local model stream failed: $it") }
        return builder.toString()
    }

    private fun analyzeVisionContext(
        config: LlmConfig,
        messages: List<ChatMessageDto>,
        attachmentPayload: AttachmentPayload,
    ): String? {
        val imageBytes = firstImageBytes(attachmentPayload) ?: return null
        val userPrompt = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val analysisPrompt = buildString {
            append("Describe this image for a browser automation agent. ")
            append("Focus on UI elements, text, links, and actionable items.")
            if (userPrompt.isNotBlank()) {
                append("\nUser task context: ")
                append(userPrompt)
            }
        }
        return runBlockingVisionAnalysis(config, imageBytes, analysisPrompt)
    }

    private fun runBlockingVisionAnalysis(
        config: LlmConfig,
        imageBytes: ByteArray,
        prompt: String,
    ): String? = try {
        MultimodalBridge.release()
        val visionReady = MultimodalBridge.initModel(config.localModelPath, config.localMmprojPath)
        if (!visionReady) return null

        val builder = StringBuilder()
        val latch = java.util.concurrent.CountDownLatch(1)
        var error: String? = null
        MultimodalBridge.analyzeImageBytesStream(
            imageBytes = imageBytes,
            prompt = prompt,
            callback = object : GenStream {
                override fun onDelta(text: String) {
                    builder.append(text)
                }

                override fun onComplete() {
                    latch.countDown()
                }

                override fun onError(message: String) {
                    error = message
                    latch.countDown()
                }
            },
        )
        latch.await()
        MultimodalBridge.release()
        error?.let { return null }
        builder.toString().trim().ifBlank { null }
    } catch (_: Exception) {
        MultimodalBridge.release()
        null
    }

    private suspend fun ensureEngine(config: LlmConfig) = engineMutex.withLock {
        val key = engineCacheKey(config)
        if (cachedConfigKey == key) return@withLock

        LlamaBridge.shutdown()

        val modelReady = LlamaBridge.initGenerateModel(config.localModelPath)
        if (!modelReady) {
            throw IllegalStateException("Failed to load GGUF model at ${config.localModelPath}")
        }

        val gpuLayers = when (config.backend) {
            LlmBackend.CPU -> 0
            LlmBackend.GPU -> -1
            LlmBackend.NPU -> 0
        }

        LlamaBridge.updateGenerateParams(
            temperature = config.temperature,
            maxTokens = config.maxTokens.coerceAtMost(2048),
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            contextLength = 16384,
            numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(6),
            useMmap = true,
            flashAttention = false,
            batchSize = 512,
            gpuLayers = gpuLayers,
        )

        cachedConfigKey = key
    }

    private fun requireModelFiles(config: LlmConfig) {
        if (!modelFileManager.modelFileExists(config.localModelPath)) {
            throw IllegalStateException(
                "GGUF model not found. Download a Q4 vision model on the setup screen.",
            )
        }
        if (!modelFileManager.modelFileExists(config.localMmprojPath)) {
            throw IllegalStateException(
                "Vision projector (mmproj) not found. Download the full model bundle on the setup screen.",
            )
        }
    }

    private fun engineCacheKey(config: LlmConfig): String =
        "${config.localModelPath}|${config.localMmprojPath}|${config.backend}"

    private fun augmentSystemPrompt(base: String, tools: List<ToolDefinition>): String {
        if (tools.isEmpty()) return base
        val toolArray = JSONArray()
        tools.forEach { tool ->
            toolArray.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("parameters", JSONObject(tool.parametersJson)),
                    ),
            )
        }
        return buildString {
            append(base.trim())
            append("\n\n# Tools\n")
            append("You may call tools to complete browser automation tasks.\n")
            append("Available tools (JSON schema):\n")
            append(toolArray.toString(2))
            append(
                "\n\nWhen you need a tool, respond with tool calls using the model's native format. " +
                    "After tool results arrive, continue the task or give the final answer.",
            )
        }
    }

    private fun buildPromptMessages(
        messages: List<ChatMessageDto>,
        visionContext: String?,
    ): List<Pair<String, String>> = buildList {
        messages.forEachIndexed { index, dto ->
            when (dto.role) {
                "user" -> {
                    val content = buildString {
                        if (index == messages.lastIndex && !visionContext.isNullOrBlank()) {
                            append("[Vision analysis]\n")
                            append(visionContext.trim())
                            append("\n\n")
                        }
                        append(dto.content.orEmpty())
                    }
                    add("user" to content)
                }
                "assistant" -> add("assistant" to formatAssistantMessage(dto))
                "tool" -> add("tool" to formatToolMessage(dto))
                "system" -> Unit
            }
        }
    }

    private fun formatAssistantMessage(dto: ChatMessageDto): String = buildString {
        if (!dto.content.isNullOrBlank()) {
            append(dto.content.trim())
        }
        dto.toolCalls.orEmpty().forEach { call ->
            if (isNotEmpty()) append('\n')
            append("<tool_call>\n")
            append(
                JSONObject()
                    .put("name", call.function.name)
                    .put("arguments", JSONObject(call.function.arguments))
                    .toString(),
            )
            append("\n</tool_call>")
        }
    }

    private fun formatToolMessage(dto: ChatMessageDto): String = buildString {
        append("tool: ")
        append(dto.name ?: "unknown")
        append("\n")
        append(dto.content.orEmpty())
    }

    private fun fallbackPrompt(
        systemPrompt: String,
        messages: List<Pair<String, String>>,
    ): String = buildString {
        append("System:\n")
        append(systemPrompt)
        append("\n\n")
        messages.forEach { (role, content) ->
            append(role.replaceFirstChar { it.uppercase() })
            append(":\n")
            append(content)
            append("\n\n")
        }
        append("Assistant:\n")
    }

    private fun parseCompletion(rawText: String, knownToolNames: Set<String>): LlmCompletion {
        val toolCalls = parseToolCalls(rawText, knownToolNames)
        val content = if (toolCalls.isNotEmpty()) {
            stripToolCallMarkup(rawText).ifBlank { null }
        } else {
            rawText.trim().ifBlank { null }
        }
        return LlmCompletion(
            content = content,
            toolCalls = toolCalls,
            finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
        )
    }

    private fun parseToolCalls(text: String, knownToolNames: Set<String>): List<ToolCallDto> {
        val results = linkedMapOf<String, ToolCallDto>()

        val xmlPattern = Regex("""<tool_call>\s*(\{.*?\})\s*</tool_call>""", RegexOption.DOT_MATCHES_ALL)
        xmlPattern.findAll(text).forEach { match ->
            addParsedToolCall(results, match.groupValues[1], knownToolNames)
        }

        val gemmaPatterns = listOf(
            Regex("""<\|tool_call>call:(\w+)\{(.*?)\}<tool_call\|>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""call:(\w+)\{([^}]*)\}"""),
        )
        val argPattern = Regex("""(\w+):(?:<\|"\|>(.*?)<\|"\|>|"([^"]*)"|'([^']*)'|([^,}]*))""")
        for (pattern in gemmaPatterns) {
            for (match in pattern.findAll(text)) {
                val name = match.groupValues[1]
                if (knownToolNames.isNotEmpty() && name !in knownToolNames) continue
                val argsBlock = match.groupValues[2]
                val arguments = linkedMapOf<String, Any?>()
                for (argMatch in argPattern.findAll(argsBlock)) {
                    val key = argMatch.groupValues[1]
                    val value = argMatch.groupValues.drop(2).firstOrNull { it.isNotEmpty() }?.trim().orEmpty()
                    arguments[key] = castArgument(value)
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

    private fun addParsedToolCall(
        results: LinkedHashMap<String, ToolCallDto>,
        jsonBlock: String,
        knownToolNames: Set<String>,
    ) {
        runCatching {
            val json = JSONObject(jsonBlock.trim())
            val name = json.optString("name")
            if (name.isBlank()) return
            if (knownToolNames.isNotEmpty() && name !in knownToolNames) return
            val args = json.optJSONObject("arguments") ?: JSONObject()
            val id = "json_${results.size}_$name"
            results[id] = ToolCallDto(
                id = id,
                function = ToolCallFunctionDto(
                    name = name,
                    arguments = args.toString(),
                ),
            )
        }
    }

    private fun castArgument(value: String): Any? {
        if (value.equals("true", ignoreCase = true)) return true
        if (value.equals("false", ignoreCase = true)) return false
        value.toIntOrNull()?.let { return it }
        value.toDoubleOrNull()?.let { return it }
        return value
    }

    private fun stripToolCallMarkup(text: String): String =
        text
            .replace(Regex("""<tool_call>.*?</tool_call>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<\|tool_call>.*?</tool_call\|>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""call:\w+\{[^}]*\}"""), "")
            .trim()

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
}