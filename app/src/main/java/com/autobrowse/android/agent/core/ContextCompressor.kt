package com.autobrowse.android.agent.core

import com.autobrowse.android.data.remote.ChatMessageDto
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.domain.model.ChatMessage
import com.autobrowse.android.domain.model.LlmConfig

class ContextCompressor(
    private val llmApi: LlmApiService,
    private val protectLastN: Int = 12,
    private val triggerThreshold: Int = 40,
) {
    suspend fun maybeCompress(
        config: LlmConfig,
        messages: List<ChatMessage>,
    ): Pair<List<ChatMessage>, String?> {
        if (messages.size < triggerThreshold) return messages to null

        val toSummarize = messages.dropLast(protectLastN)
        val toKeep = messages.takeLast(protectLastN)
        if (toSummarize.isEmpty()) return messages to null

        val transcript = toSummarize.joinToString("\n") { msg ->
            "${msg.role.name.lowercase()}: ${msg.content}"
        }

        val summary = llmApi.chat(
            config = config,
            systemPrompt = "Summarize this conversation history into concise bullet points preserving key facts, decisions, and URLs.",
            userPrompt = transcript.take(12000),
        )

        val summaryMessage = ChatMessage(
            id = "compressed_${System.currentTimeMillis()}",
            sessionId = messages.first().sessionId,
            role = com.autobrowse.android.domain.model.AgentRole.SYSTEM,
            content = "[Compressed history]\n$summary",
        )

        return listOf(summaryMessage) + toKeep to summary
    }

    fun toApiHistory(messages: List<ChatMessage>): List<ChatMessageDto> =
        messages.mapNotNull { msg ->
            when (msg.role) {
                com.autobrowse.android.domain.model.AgentRole.USER ->
                    ChatMessageDto(role = "user", content = msg.content)
                com.autobrowse.android.domain.model.AgentRole.AGENT ->
                    ChatMessageDto(role = "assistant", content = msg.content)
                com.autobrowse.android.domain.model.AgentRole.SYSTEM ->
                    ChatMessageDto(role = "system", content = msg.content)
            }
        }
}