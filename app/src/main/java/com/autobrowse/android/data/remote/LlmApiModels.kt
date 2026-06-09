package com.autobrowse.android.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Float = 0.7f,
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    val stream: Boolean = false,
    val tools: List<ToolSchemaDto>? = null,
    @Json(name = "tool_choice") val toolChoice: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    @Json(name = "tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @Json(name = "tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class ToolSchemaDto(
    val type: String = "function",
    val function: ToolFunctionDto,
)

@JsonClass(generateAdapter = true)
data class ToolFunctionDto(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>,
)

@JsonClass(generateAdapter = true)
data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunctionDto,
)

@JsonClass(generateAdapter = true)
data class ToolCallFunctionDto(
    val name: String,
    val arguments: String,
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val choices: List<ChoiceDto>,
    val usage: UsageDto? = null,
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    val message: ChatMessageDto,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class UsageDto(
    @Json(name = "prompt_tokens") val promptTokens: Int = 0,
    @Json(name = "completion_tokens") val completionTokens: Int = 0,
    @Json(name = "total_tokens") val totalTokens: Int = 0,
)

data class LlmCompletion(
    val content: String?,
    val toolCalls: List<ToolCallDto>,
    val finishReason: String?,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val usageFromApi: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class StreamChunkResponse(
    val choices: List<StreamChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
)

@JsonClass(generateAdapter = true)
data class StreamChoiceDto(
    val delta: StreamDeltaDto? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class StreamDeltaDto(
    val content: String? = null,
    @Json(name = "tool_calls") val toolCalls: List<StreamToolCallDeltaDto>? = null,
)

@JsonClass(generateAdapter = true)
data class StreamToolCallDeltaDto(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: StreamToolFunctionDeltaDto? = null,
)

@JsonClass(generateAdapter = true)
data class StreamToolFunctionDeltaDto(
    val name: String? = null,
    val arguments: String? = null,
)