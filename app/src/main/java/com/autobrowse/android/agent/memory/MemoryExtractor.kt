package com.autobrowse.android.agent.memory

import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.domain.model.LlmConfig
import org.json.JSONArray

data class ExtractedMemory(
    val key: String,
    val value: String,
    val category: String,
    val importance: Int,
)

class MemoryExtractor(
    private val llmApi: LlmApiService,
) {
    suspend fun extractFromTurn(
        config: LlmConfig,
        userMessage: String,
        assistantMessage: String,
    ): List<ExtractedMemory> {
        if (config.apiKey.isBlank()) return emptyList()

        val response = runCatching {
            llmApi.chat(
                config = config,
                systemPrompt = """
                    Extract durable facts worth remembering across sessions.
                    Return JSON array only. Each item: {"key","value","category","importance"}
                    category must be one of: USER, MEMORY, PREFERENCE
                    importance 1-10. Return [] if nothing worth storing.
                """.trimIndent(),
                userPrompt = "User: $userMessage\nAssistant: $assistantMessage",
            )
        }.getOrNull() ?: return emptyList()

        return parseMemories(response)
    }

    private fun parseMemories(raw: String): List<ExtractedMemory> {
        val jsonStart = raw.indexOf('[')
        val jsonEnd = raw.lastIndexOf(']')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()

        return runCatching {
            val array = JSONArray(raw.substring(jsonStart, jsonEnd + 1))
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val key = obj.optString("key")
                    val value = obj.optString("value")
                    if (key.isBlank() || value.isBlank()) continue
                    add(
                        ExtractedMemory(
                            key = key,
                            value = value,
                            category = obj.optString("category", "MEMORY"),
                            importance = obj.optInt("importance", 5).coerceIn(1, 10),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}