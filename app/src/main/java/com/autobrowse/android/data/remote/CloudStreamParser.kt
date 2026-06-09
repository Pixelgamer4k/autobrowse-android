package com.autobrowse.android.data.remote

import com.squareup.moshi.Moshi
import okio.BufferedSource

internal class ToolCallStreamAccumulator {
    private data class MutableCall(
        var id: String = "",
        var name: String = "",
        var arguments: StringBuilder = StringBuilder(),
    )

    private val calls = linkedMapOf<Int, MutableCall>()

    fun append(deltas: List<StreamToolCallDeltaDto>) {
        deltas.forEach { delta ->
            val index = delta.index ?: return@forEach
            val call = calls.getOrPut(index) { MutableCall() }
            delta.id?.let { call.id = it }
            delta.function?.name?.let { call.name = it }
            delta.function?.arguments?.let { call.arguments.append(it) }
        }
    }

    fun build(): List<ToolCallDto> = calls.values.mapNotNull { call ->
        if (call.name.isBlank()) return@mapNotNull null
        ToolCallDto(
            id = call.id.ifBlank { "stream_${call.name}" },
            function = ToolCallFunctionDto(
                name = call.name,
                arguments = call.arguments.toString().ifBlank { "{}" },
            ),
        )
    }
}

internal class CloudStreamAccumulator(
    moshi: Moshi,
    private val onTokenDelta: ((String) -> Unit)?,
) {
    private val chunkAdapter = moshi.adapter(StreamChunkResponse::class.java)
    private val content = StringBuilder()
    private val toolAccumulator = ToolCallStreamAccumulator()
    var finishReason: String? = null
        private set
    var usage: UsageDto? = null
        private set

    fun processLine(line: String) {
        if (!line.startsWith("data:")) return
        val payload = line.removePrefix("data:").trim()
        if (payload.isBlank() || payload == "[DONE]") return
        val chunk = chunkAdapter.fromJson(payload) ?: return
        chunk.usage?.let { usage = it }
        val choice = chunk.choices.firstOrNull() ?: return
        choice.finishReason?.let { finishReason = it }
        choice.delta?.content?.let { delta ->
            content.append(delta)
            onTokenDelta?.invoke(delta)
        }
        choice.delta?.toolCalls?.let { toolAccumulator.append(it) }
    }

    fun toCompletion(): LlmCompletion {
        val toolCalls = toolAccumulator.build()
        val u = usage
        return LlmCompletion(
            content = content.toString().ifBlank { null },
            toolCalls = toolCalls,
            finishReason = finishReason ?: if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
            promptTokens = u?.promptTokens ?: 0,
            completionTokens = u?.completionTokens ?: 0,
            totalTokens = u?.totalTokens ?: 0,
            usageFromApi = u != null,
        )
    }
}

internal object CloudStreamParser {
    fun parseStream(
        source: BufferedSource,
        moshi: Moshi,
        onTokenDelta: (String) -> Unit,
    ): LlmCompletion {
        val accumulator = CloudStreamAccumulator(moshi, onTokenDelta)
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            accumulator.processLine(line)
        }
        return accumulator.toCompletion()
    }
}