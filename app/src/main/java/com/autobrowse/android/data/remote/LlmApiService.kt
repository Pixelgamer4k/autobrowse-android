package com.autobrowse.android.data.remote

import com.autobrowse.android.agent.core.PromptBundle
import com.autobrowse.android.data.local.LocalLlmService
import com.autobrowse.android.domain.model.AttachmentPayload
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.LlmProvider
import com.autobrowse.android.domain.model.ToolDefinition
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LlmApiService(
    private val localLlmService: LocalLlmService? = null,
) {
    fun cancelLocalGeneration() {
        localLlmService?.cancelActiveGeneration()
    }

    suspend fun warmUpLocalModel(config: LlmConfig) {
        if (config.provider == LlmProvider.LOCAL) {
            localLlmService?.warmUp(config)
        }
    }

    suspend fun prepareLocalEngine(config: LlmConfig) {
        if (config.provider == LlmProvider.LOCAL) {
            localLlmService?.prepareEngine(config)
        }
    }
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)
    private val streamChunkAdapter = moshi.adapter(StreamChunkResponse::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    private val testClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(config: LlmConfig): String = withContext(Dispatchers.IO) {
        if (config.provider == LlmProvider.LOCAL) {
            return@withContext localLlmService?.testConnection(config)
                ?: throw IllegalStateException("Local LiteRT inference is not available.")
        }
        require(config.apiKey.isNotBlank()) { "API token is required." }
        require(config.apiUrl.isNotBlank()) { "API URL is required." }
        require(config.modelId.isNotBlank()) { "Model ID is required." }

        val requestBody = requestAdapter.toJson(
            ChatCompletionRequest(
                model = config.modelId,
                messages = listOf(
                    ChatMessageDto(role = "user", content = "Reply with OK."),
                ),
                temperature = 0f,
                maxTokens = 5,
            ),
        ).toRequestBody("application/json".toMediaType())

        val baseUrl = config.apiUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        testClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response from LLM API")

            if (!response.isSuccessful) {
                throw IllegalStateException("Connection failed (${response.code}): $body")
            }

            val parsed = responseAdapter.fromJson(body)
                ?: throw IllegalStateException("Failed to parse LLM response")

            val reply = parsed.choices.firstOrNull()?.message?.content?.trim()
            if (!reply.isNullOrBlank()) {
                "Connected — model replied: $reply"
            } else {
                "Connected — model responded successfully."
            }
        }
    }

    suspend fun chat(
        config: LlmConfig,
        systemPrompt: String,
        userPrompt: String,
        history: List<ChatMessageDto> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
    ): String = withContext(Dispatchers.IO) {
        complete(
            config = config,
            systemPrompt = systemPrompt,
            messages = history + ChatMessageDto(role = "user", content = userPrompt),
            attachmentPayload = attachmentPayload,
        ).content ?: throw IllegalStateException("No completion returned from LLM")
    }

    suspend fun complete(
        config: LlmConfig,
        systemPrompt: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
        onTokenDelta: ((String) -> Unit)? = null,
        compactTools: Boolean = false,
    ): LlmCompletion = complete(
        config = config,
        promptBundle = PromptBundle(stablePrefix = systemPrompt, volatileContext = ""),
        messages = messages,
        tools = tools,
        attachmentPayload = attachmentPayload,
        onTokenDelta = onTokenDelta,
        compactTools = compactTools,
    )

    suspend fun complete(
        config: LlmConfig,
        promptBundle: PromptBundle,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
        onTokenDelta: ((String) -> Unit)? = null,
        compactTools: Boolean = false,
    ): LlmCompletion = withContext(Dispatchers.IO) {
        if (config.provider == LlmProvider.LOCAL) {
            return@withContext localLlmService?.complete(
                config = config,
                systemPrompt = promptBundle.fullSystem,
                messages = messages,
                tools = tools,
                attachmentPayload = attachmentPayload,
                onTokenDelta = onTokenDelta,
                compactTools = compactTools,
            ) ?: throw IllegalStateException("Local LiteRT inference is not available.")
        }
        require(config.apiKey.isNotBlank()) { "API token is required. Configure it on the setup screen." }

        val apiMessages = buildList {
            add(ChatMessageDto(role = "system", content = promptBundle.stablePrefix))
            if (promptBundle.volatileContext.isNotBlank()) {
                add(ChatMessageDto(role = "system", content = promptBundle.volatileContext))
            }
            addAll(messages)
        }

        val toolSchemas = tools.map { tool ->
            ToolSchemaDto(
                function = ToolFunctionDto(
                    name = tool.name,
                    description = tool.description,
                    parameters = mapAdapter.fromJson(tool.parametersJson) ?: emptyMap(),
                ),
            )
        }.takeIf { it.isNotEmpty() }

        val baseRequest = requestAdapter.toJson(
            ChatCompletionRequest(
                model = config.modelId,
                messages = apiMessages,
                temperature = config.temperature,
                maxTokens = config.maxTokens.coerceIn(512, 4096),
                tools = toolSchemas,
                toolChoice = if (toolSchemas != null) "auto" else null,
                stream = onTokenDelta != null,
            ),
        )

        val multimodalIndex = apiMessages.lastIndex
        val lastMessage = apiMessages.lastOrNull()
        val requestJson = if (
            attachmentPayload.attachments.isNotEmpty() &&
            lastMessage?.role == "user" &&
            lastMessage.content != null
        ) {
            val multimodal = MultimodalMessageBuilder.buildUserMessageJson(
                role = "user",
                text = lastMessage.content,
                payload = attachmentPayload,
            )
            MultimodalMessageBuilder.injectIntoRequestBody(
                baseJson = baseRequest,
                multimodalIndices = mapOf(multimodalIndex + 1 to multimodal),
            )
        } else {
            baseRequest
        }

        val finalJson = if (onTokenDelta != null) {
            JSONObject(requestJson).put("stream", true).toString()
        } else {
            requestJson
        }

        val baseUrl = config.apiUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(finalJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IllegalStateException("LLM API error (${response.code}): $errorBody")
            }

            val responseBody = response.body
                ?: throw IllegalStateException("Empty response from LLM API")

            if (onTokenDelta != null) {
                return@withContext responseBody.source().use { source ->
                    CloudStreamParser.parseStream(source, moshi, onTokenDelta)
                }
            }

            val body = responseBody.string()
            val parsed = responseAdapter.fromJson(body)
                ?: throw IllegalStateException("Failed to parse LLM response")

            val choice = parsed.choices.firstOrNull()
                ?: throw IllegalStateException("No completion returned from LLM")

            val usage = parsed.usage
            LlmCompletion(
                content = choice.message.content,
                toolCalls = choice.message.toolCalls.orEmpty(),
                finishReason = choice.finishReason,
                promptTokens = usage?.promptTokens ?: 0,
                completionTokens = usage?.completionTokens ?: 0,
                totalTokens = usage?.totalTokens ?: 0,
                usageFromApi = usage != null,
            )
        }
    }
}