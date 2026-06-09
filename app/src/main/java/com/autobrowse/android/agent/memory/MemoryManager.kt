package com.autobrowse.android.agent.memory

import com.autobrowse.android.data.local.dao.MemoryDao
import com.autobrowse.android.data.local.dao.TrajectoryDao
import com.autobrowse.android.data.local.entity.MemoryEntryEntity
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MemoryManager(
    private val memoryDao: MemoryDao,
    private val trajectoryDao: TrajectoryDao,
    private val repository: AutobrowseRepository,
    private val llmApi: LlmApiService,
    private val extractor: MemoryExtractor = MemoryExtractor(llmApi),
) {
    suspend fun prefetch(query: String): String = withContext(Dispatchers.IO) {
        val memories = search(query, limit = 6)
        if (memories.isEmpty()) return@withContext ""
        memories.joinToString("\n") { "- [${it.category}] ${it.key}: ${it.value}" }
    }

    suspend fun search(query: String, limit: Int = 10): List<MemoryEntry> = withContext(Dispatchers.IO) {
        val ftsQuery = query.split(Regex("\\s+"))
            .filter { it.length > 2 }
            .joinToString(" OR ") { "$it*" }

        val ftsResults = runCatching {
            if (ftsQuery.isNotBlank()) memoryDao.searchFts(ftsQuery, limit) else emptyList()
        }.getOrDefault(emptyList())

        val likeResults = query.split(Regex("\\s+"))
            .flatMap { term -> memoryDao.searchLike(term, limit) }

        (ftsResults + likeResults)
            .distinctBy { it.id }
            .sortedByDescending { it.importance }
            .take(limit)
            .map { it.toDomain() }
    }

    suspend fun remember(
        key: String,
        value: String,
        category: String = "MEMORY",
        importance: Int = 5,
        tags: String = "",
        source: String = "agent",
    ) = withContext(Dispatchers.IO) {
        val existing = memoryDao.getByKey(key)
        val now = System.currentTimeMillis()
        memoryDao.upsert(
            MemoryEntryEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                key = key,
                value = value,
                category = category,
                importance = importance.coerceIn(1, 10),
                tags = tags,
                source = source,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun getUserProfileBlock(): String = withContext(Dispatchers.IO) {
        val user = memoryDao.getByCategory("USER")
        val prefs = memoryDao.getByCategory("PREFERENCE")
        buildString {
            if (user.isNotEmpty()) {
                appendLine("## User Profile")
                user.forEach { appendLine("- ${it.key}: ${it.value}") }
            }
            if (prefs.isNotEmpty()) {
                appendLine("## Preferences")
                prefs.forEach { appendLine("- ${it.key}: ${it.value}") }
            }
        }.trim()
    }

    suspend fun getMemoryBlock(): String = withContext(Dispatchers.IO) {
        val memories = memoryDao.getByCategory("MEMORY").take(12)
        if (memories.isEmpty()) return@withContext ""
        buildString {
            appendLine("## Long-term Memory")
            memories.forEach { appendLine("- ${it.key}: ${it.value}") }
        }.trim()
    }

    /**
     * Hermes-style bounded curated memory — fixed slots, char cap, always injected in stable prefix.
     */
    suspend fun getBoundedCuratedBlock(maxChars: Int = 900): String = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        memoryDao.getByCategory("USER").take(2).forEach {
            lines += "USER ${it.key}: ${it.value.take(80)}"
        }
        memoryDao.getByCategory("PREFERENCE").take(3).forEach {
            lines += "PREF ${it.key}: ${it.value.take(72)}"
        }
        memoryDao.getTop(5).forEach {
            if (it.category !in setOf("USER", "PREFERENCE", "SESSION")) {
                lines += "MEM ${it.key}: ${it.value.take(72)}"
            }
        }
        if (lines.isEmpty()) return@withContext ""
        val body = lines.distinct().take(8).joinToString("\n") { "- $it" }
        truncateToBudget("## Curated memory\n$body", maxChars)
    }

    private fun truncateToBudget(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars - 3) + "..."
    }

    suspend fun syncTurnFast(
        userMessage: String,
        assistantMessage: String,
    ): Int = withContext(Dispatchers.IO) {
        val extracted = extractFastPatterns(userMessage, assistantMessage)
        extracted.forEach { item ->
            remember(
                key = item.key,
                value = item.value,
                category = item.category,
                importance = item.importance,
                source = "fast_extraction",
            )
        }
        remember(
            key = "session_note_${System.currentTimeMillis()}",
            value = "User: ${userMessage.take(120)} → Agent: ${assistantMessage.take(200)}",
            category = "SESSION",
            importance = 3,
            source = "sync_turn",
        )
        extracted.size
    }

    suspend fun syncTurn(
        sessionId: String,
        userMessage: String,
        assistantMessage: String,
    ): Int = syncTurnFast(userMessage, assistantMessage)

    private fun extractFastPatterns(
        userMessage: String,
        assistantMessage: String,
    ): List<ExtractedMemory> {
        val results = mutableListOf<ExtractedMemory>()
        val text = "$userMessage $assistantMessage"

        Regex("""(?i)remember(?:\s+that)?\s+(.+?)[\.\!\?]?\s*$""")
            .find(userMessage)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length > 3 }?.let {
                results += ExtractedMemory("user_remember", it.take(200), "MEMORY", 8)
            }

        Regex("""(?i)my name is\s+([A-Za-z][A-Za-z\s'-]{1,40})""")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.let {
                results += ExtractedMemory("user_name", it, "USER", 9)
            }

        Regex("""(?i)I prefer\s+(.+?)[\.\!\?]?\s*$""")
            .find(userMessage)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length > 3 }?.let {
                results += ExtractedMemory("preference_${it.hashCode()}", it.take(160), "PREFERENCE", 7)
            }

        Regex("""(?i)always\s+(.+?)[\.\!\?]?\s*$""")
            .find(userMessage)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length > 5 }?.let {
                results += ExtractedMemory("habit_${it.hashCode()}", it.take(160), "PREFERENCE", 6)
            }

        return results.distinctBy { it.key }
    }

    suspend fun searchSessions(query: String, sessionId: String, limit: Int): List<String> =
        withContext(Dispatchers.IO) {
            memoryDao.getByCategory("SESSION")
                .filter { it.value.contains(query, ignoreCase = true) || it.key.contains(query, ignoreCase = true) }
                .take(limit)
                .map { it.value }
        }

    private fun MemoryEntryEntity.toDomain() = MemoryEntry(
        id = id,
        key = key,
        value = value,
        category = category,
        importance = importance,
        tags = tags,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}