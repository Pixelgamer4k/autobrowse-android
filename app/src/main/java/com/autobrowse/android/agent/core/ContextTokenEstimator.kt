package com.autobrowse.android.agent.core

import com.autobrowse.android.data.remote.ChatMessageDto
import com.autobrowse.android.domain.model.ToolDefinition
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.LlmProvider

object ContextTokenEstimator {
    /** Fast heuristic (~4 chars/token for English). Good enough for UI footer. */
    fun estimateTextTokens(text: String): Int =
        (text.length / 4.0).toInt().coerceAtLeast(if (text.isBlank()) 0 else 1)

    fun estimateMessagesTokens(messages: List<ChatMessageDto>): Int =
        messages.sumOf { estimateTextTokens(it.content.orEmpty()) + overheadPerMessage }

    fun estimateToolsTokens(tools: List<ToolDefinition>): Int =
        tools.sumOf { estimateTextTokens(it.name + it.description + it.parametersJson) + 12 }

    fun estimateRequestTokens(
        stablePrefix: String,
        volatileContext: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition>,
    ): Int = estimateTextTokens(stablePrefix) +
        estimateTextTokens(volatileContext) +
        estimateMessagesTokens(messages) +
        estimateToolsTokens(tools) +
        24

    fun resolveContextWindow(config: LlmConfig): Int = when (config.provider) {
        LlmProvider.LOCAL -> config.maxTokens.coerceIn(4096, 131_072)
        LlmProvider.REMOTE -> ModelContextLimits.resolve(config.modelId)
    }

    private const val overheadPerMessage = 4
}

object ModelContextLimits {
    fun resolve(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("128k") -> 131_072
            id.contains("200k") -> 200_000
            id.contains("1m") || id.contains("1000k") -> 1_000_000
            id.contains("32k") -> 32_768
            id.contains("owl") -> 131_072
            id.contains("gemini") && id.contains("pro") -> 1_000_000
            id.contains("gemini") -> 131_072
            id.contains("claude") && id.contains("opus") -> 200_000
            id.contains("claude") -> 200_000
            id.contains("gpt-4o") -> 128_000
            id.contains("gpt-4") -> 128_000
            id.contains("mini") -> 128_000
            else -> 128_000
        }
    }
}

data class ContextUsageStats(
    val promptTokens: Int,
    val completionTokens: Int,
    val contextWindowTokens: Int,
    val estimated: Boolean,
    val agentTurns: Int,
) {
    val tokensLeft: Int = (contextWindowTokens - promptTokens).coerceAtLeast(0)

    fun formatFooter(): String {
        val mark = if (estimated) "~" else ""
        return buildString {
            appendLine()
            appendLine("---")
            append(
                "Context: ${mark}${formatK(promptTokens)} / ${formatK(contextWindowTokens)} used · " +
                    "${mark}${formatK(tokensLeft)} left · $agentTurns agent turn${if (agentTurns == 1) "" else "s"}",
            )
            if (completionTokens > 0) {
                append(" · ${mark}${formatK(completionTokens)} output")
            }
        }
    }

    private fun formatK(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 10_000 -> String.format("%.1fk", n / 1_000.0)
        else -> n.toString()
    }
}