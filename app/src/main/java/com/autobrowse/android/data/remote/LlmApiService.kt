package com.autobrowse.android.data.remote

import com.autobrowse.android.domain.model.AttachmentPayload
import com.autobrowse.android.domain.model.LlmConfig
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
import java.util.concurrent.TimeUnit

class LlmApiService {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)
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
    ): LlmCompletion = withContext(Dispatchers.IO) {
        require(config.apiKey.isNotBlank()) { "API key is required. Configure it in Settings." }

        val apiMessages = buildList {
            add(ChatMessageDto(role = "system", content = systemPrompt))
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
                maxTokens = config.maxTokens,
                tools = toolSchemas,
                toolChoice = if (toolSchemas != null) "auto" else null,
            ),
        )

        val multimodalIndex = apiMessages.lastIndex
        val lastMessage = apiMessages.lastOrNull()
        val requestBody = if (
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
        }.toRequestBody("application/json".toMediaType())

        val baseUrl = config.apiUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response from LLM API")

            if (!response.isSuccessful) {
                throw IllegalStateException("LLM API error (${response.code}): $body")
            }

            val parsed = responseAdapter.fromJson(body)
                ?: throw IllegalStateException("Failed to parse LLM response")

            val choice = parsed.choices.firstOrNull()
                ?: throw IllegalStateException("No completion returned from LLM")

            LlmCompletion(
                content = choice.message.content,
                toolCalls = choice.message.toolCalls.orEmpty(),
                finishReason = choice.finishReason,
            )
        }
    }
}