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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class LocalLlmService(
    private val modelFileManager: ModelFileManager,
) {
    private companion object {
        const val MAX_LOCAL_PROMPT_CHARS = 6_000
        const val LOCAL_CONTEXT_LENGTH = 2048
        const val LOCAL_MAX_OUTPUT_TOKENS = 512
    }

    private val engineMutex = Mutex()
    private val inferenceMutex = Mutex()
    private var cachedConfigKey: String? = null
    private var primedConfigKey: String? = null
    private val generationCancelled = AtomicBoolean(false)

    /**
     * Abort in-flight native generation and reset KV state so the next prompt can run safely.
     */
    fun cancelActiveGeneration() {
        generationCancelled.set(true)
        runCatching { LlamaBridge.nativeCancelGenerate() }
        runCatching { LlamaBridge.sessionReset() }
        runCatching { MultimodalBridge.release() }
    }

    suspend fun warmUp(config: LlmConfig) = prepareEngine(config)

    /** Load model weights and run a 1-token prime so the first user prompt is not paying cold-start cost. */
    suspend fun prepareEngine(config: LlmConfig) = withContext(Dispatchers.IO) {
        if (!config.localModelPath.isNotBlank() || !config.localMmprojPath.isNotBlank()) return@withContext
        inferenceMutex.withLock {
            runCatching {
                requireModelFiles(config)
                ensureEngine(config)
                primeEngine(config)
            }
        }
    }

    suspend fun testConnection(config: LlmConfig): String = withContext(Dispatchers.IO) {
        requireModelFiles(config)
        runInference {
            ensureEngine(config)
            val response = generateFromMessages(
                config = config,
                systemPrompt = "You are a helpful assistant.",
                messages = listOf(ChatMessageDto(role = "user", content = "Reply with OK.")),
                tools = emptyList(),
                attachmentPayload = AttachmentPayload(),
                compactTools = true,
            )
            val text = response.content.orEmpty()
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
        requireModelFiles(config)
        if (messages.isEmpty()) {
            throw IllegalStateException("No messages to send.")
        }
        runInference {
            ensureEngine(config)
            generateFromMessages(
                config,
                systemPrompt,
                messages,
                tools,
                attachmentPayload,
                onTokenDelta,
                compactTools,
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
        compactTools = true,
    ).content ?: throw IllegalStateException("No completion returned from local model.")

    fun close() {
        cancelActiveGeneration()
        LlamaBridge.shutdown()
        cachedConfigKey = null
    }

    private suspend fun <T> runInference(block: suspend () -> T): T = inferenceMutex.withLock {
        generationCancelled.set(false)
        try {
            block()
        } catch (e: CancellationException) {
            cancelActiveGeneration()
            throw e
        } finally {
            runCatching { LlamaBridge.sessionReset() }
        }
    }

    private suspend fun generateFromMessages(
        config: LlmConfig,
        systemPrompt: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition>,
        attachmentPayload: AttachmentPayload,
        onTokenDelta: ((String) -> Unit)? = null,
        compactTools: Boolean = false,
    ): LlmCompletion {
        runCatching { LlamaBridge.sessionReset() }

        val enrichedSystem = augmentSystemPrompt(systemPrompt, tools, compactTools)
        val visionContext = analyzeVisionContext(config, messages, attachmentPayload)
        val promptMessages = buildPromptMessages(messages, visionContext)
        val templated = LlamaBridge.applyChatTemplate(
            messages = listOf("system" to enrichedSystem) + promptMessages,
            addAssistantPrefix = true,
        ) ?: fallbackPrompt(enrichedSystem, promptMessages)
        val prompt = capPrompt(templated, compactTools)

        val rawText = if (onTokenDelta != null) {
            generateStream(prompt, onTokenDelta)
        } else {
            generateBlocking(prompt)
        }
        return parseCompletion(rawText, tools.map { it.name }.toSet())
    }

    private fun generateBlocking(prompt: String): String {
        if (generationCancelled.get()) throw CancellationException("Generation cancelled")
        return LlamaBridge.generate(prompt)
    }

    private fun generateStream(prompt: String, onTokenDelta: (String) -> Unit): String {
        val builder = StringBuilder()
        val latch = java.util.concurrent.CountDownLatch(1)
        var error: String? = null

        LlamaBridge.generateStream(
            prompt,
            object : GenStream {
                override fun onDelta(text: String) {
                    if (generationCancelled.get()) return
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
        if (generationCancelled.get()) {
            throw CancellationException("Generation cancelled")
        }
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
            append("Describe this image briefly for a browser agent. ")
            append("List UI elements, text, and links.")
            if (userPrompt.isNotBlank()) {
                append("\nTask: ")
                append(userPrompt.take(200))
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
                    if (generationCancelled.get()) return
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
        if (generationCancelled.get()) return null
        error?.let { return null }
        builder.toString().trim().ifBlank { null }?.take(1500)
    } catch (_: Exception) {
        MultimodalBridge.release()
        null
    }

    private suspend fun ensureEngine(config: LlmConfig) = engineMutex.withLock {
        val key = engineCacheKey(config)
        if (cachedConfigKey == key) return@withLock

        LlamaBridge.shutdown()

        val gpuLayers = when (config.backend) {
            LlmBackend.CPU -> 0
            LlmBackend.GPU -> -1
            LlmBackend.NPU -> 0
        }

        // Params must be set before init — contextLength/batch/gpuLayers apply at load time.
        LlamaBridge.updateGenerateParams(
            temperature = config.temperature.coerceIn(0.4f, 0.9f),
            maxTokens = config.maxTokens.coerceIn(128, LOCAL_MAX_OUTPUT_TOKENS),
            topP = 0.8f,
            topK = 20,
            repeatPenalty = 1.05f,
            contextLength = LOCAL_CONTEXT_LENGTH,
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            useMmap = true,
            flashAttention = false,
            batchSize = 64,
            gpuLayers = gpuLayers,
        )

        val modelReady = LlamaBridge.initGenerateModel(config.localModelPath)
        if (!modelReady) {
            throw IllegalStateException("Failed to load GGUF model at ${config.localModelPath}")
        }

        cachedConfigKey = key
        primedConfigKey = null
    }

    private fun primeEngine(config: LlmConfig) {
        val key = engineCacheKey(config)
        if (primedConfigKey == key) return
        runCatching {
            LlamaBridge.sessionReset()
            LlamaBridge.generate("OK")
            LlamaBridge.sessionReset()
        }
        primedConfigKey = key
    }

    private fun capPrompt(prompt: String, compact: Boolean): String {
        if (!compact || prompt.length <= MAX_LOCAL_PROMPT_CHARS) return prompt
        return prompt.take(MAX_LOCAL_PROMPT_CHARS)
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

    private fun augmentSystemPrompt(
        base: String,
        tools: List<ToolDefinition>,
        compact: Boolean,
    ): String {
        if (tools.isEmpty()) return base
        return buildString {
            append(base.trim())
            append("\n\n# Tools\n")
            append("Call tools using the model's native tool-call format.\n")
            if (compact) {
                append("Tools: ")
                append(tools.joinToString(", ") { it.name })
                append('\n')
            } else {
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
                append(toolArray.toString(2))
            }
            append(
                "\nAfter tool results, continue or give the final answer. " +
                    "Prefer browser_search for site searches.",
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
                            append("[Vision]\n")
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
        append(dto.content.orEmpty().take(2000))
    }

    private fun fallbackPrompt(
        systemPrompt: String,
        messages: List<Pair<String, String>>,
    ): String = buildString {
        append("System:\n")
        append(systemPrompt.take(6000))
        append("\n\n")
        messages.forEach { (role, content) ->
            append(role.replaceFirstChar { it.uppercase() })
            append(":\n")
            append(content.take(3000))
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