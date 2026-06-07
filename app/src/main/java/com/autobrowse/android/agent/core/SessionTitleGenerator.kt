package com.autobrowse.android.agent.core

import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.LlmProvider

object SessionTitleGenerator {
    private val fillerWords = setOf(
        "please", "can", "you", "could", "would", "the", "a", "an", "to", "for", "me", "my", "and",
    )

    fun heuristicTitle(prompt: String): String {
        val cleaned = prompt
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('"', '\'')
        if (cleaned.isBlank()) return "New chat"

        val words = cleaned.split(" ").filter { it.isNotBlank() }
        val meaningful = if (words.size > 4) {
            words.filterNot { it.lowercase() in fillerWords }
        } else {
            words
        }
        val chosen = (if (meaningful.size >= 3) meaningful else words).take(6).joinToString(" ")
        return when {
            chosen.length <= 48 -> chosen.replaceFirstChar { it.uppercase() }
            else -> chosen.take(45).trimEnd() + "…"
        }
    }

    suspend fun generateTitle(
        llmApi: LlmApiService,
        config: LlmConfig,
        prompt: String,
    ): String {
        if (prompt.isBlank()) return "New chat"
        if (config.provider == LlmProvider.LOCAL || !config.isConfigured()) {
            return heuristicTitle(prompt)
        }
        return try {
            val reply = llmApi.chat(
                config = config,
                systemPrompt = "Generate a very short chat title (3-6 words) summarizing the user's task. " +
                    "Reply with ONLY the title. No quotes, no punctuation at the end.",
                userPrompt = prompt.take(500),
            ).trim()
                .trim('"', '\'')
                .replace(Regex("""[.!?]+$"""), "")
                .take(60)
            reply.ifBlank { heuristicTitle(prompt) }
        } catch (_: Exception) {
            heuristicTitle(prompt)
        }
    }
}