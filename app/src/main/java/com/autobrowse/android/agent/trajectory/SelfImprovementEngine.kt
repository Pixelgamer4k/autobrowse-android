package com.autobrowse.android.agent.trajectory

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.data.local.dao.StrategyDao
import com.autobrowse.android.data.local.entity.StrategyEntity
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.AgentTurn
import com.autobrowse.android.domain.model.LearnedStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SelfImprovementEngine(
    private val strategyDao: StrategyDao,
    private val repository: AutobrowseRepository,
    private val llmApi: LlmApiService,
    private val memoryManager: MemoryManager,
) {
    suspend fun reflectAndImprove(
        prompt: String,
        success: Boolean,
        turns: List<AgentTurn>,
        pageUrl: String?,
    ): Int = withContext(Dispatchers.IO) {
        val config = repository.getLlmConfig()
        if (config.apiKey.isBlank()) return@withContext 0

        val turnSummary = turns.joinToString("\n") { turn ->
            val tools = turn.toolCalls.joinToString { it.name }
            val results = turn.toolResults.joinToString { "${it.name}:${it.success}" }
            "iter ${turn.iteration} tools=[$tools] results=[$results]"
        }

        val reflection = runCatching {
            llmApi.chat(
                config = config,
                systemPrompt = """
                    You are a self-improvement engine for a browser agent.
                    Analyze the task trajectory and produce:
                    1. One sentence: what went right or wrong
                    2. One reusable heuristic for similar future tasks
                    Format:
                    ANALYSIS: ...
                    HEURISTIC: ...
                """.trimIndent(),
                userPrompt = """
                    Task: $prompt
                    Success: $success
                    Page: ${pageUrl ?: "unknown"}
                    Trajectory:
                    $turnSummary
                """.trimIndent(),
            )
        }.getOrNull() ?: return@withContext 0

        val heuristic = reflection.substringAfter("HEURISTIC:", "")
            .trim()
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .ifBlank { reflection.take(200) }

        if (heuristic.isBlank()) return@withContext 0

        val domain = inferDomain(prompt, pageUrl)
        val existing = strategyDao.findByHeuristic(heuristic)
        val now = System.currentTimeMillis()

        if (existing != null) {
            val updated = existing.copy(
                successCount = existing.successCount + if (success) 1 else 0,
                failureCount = existing.failureCount + if (success) 0 else 1,
                confidence = computeConfidence(
                    existing.successCount + if (success) 1 else 0,
                    existing.failureCount + if (success) 0 else 1,
                ),
                updatedAt = now,
            )
            strategyDao.upsert(updated)
        } else {
            strategyDao.upsert(
                StrategyEntity(
                    id = UUID.randomUUID().toString(),
                    domain = domain,
                    heuristic = heuristic,
                    successCount = if (success) 1 else 0,
                    failureCount = if (success) 0 else 1,
                    confidence = if (success) 0.6f else 0.3f,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        memoryManager.remember(
            key = "strategy_${domain}_${now}",
            value = heuristic,
            category = "STRATEGY",
            importance = if (success) 8 else 6,
            tags = domain,
            source = "self_improvement",
        )

        1
    }

    suspend fun getRelevantStrategies(prompt: String, pageUrl: String?, limit: Int = 5): List<LearnedStrategy> =
        withContext(Dispatchers.IO) {
            val domain = inferDomain(prompt, pageUrl)
            val domainStrategies = strategyDao.getByDomain(domain, limit)
            val topStrategies = if (domainStrategies.size >= limit) {
                domainStrategies
            } else {
                (domainStrategies + strategyDao.getTop(limit)).distinctBy { it.id }.take(limit)
            }
            topStrategies.map { it.toDomain() }
        }

    private fun computeConfidence(successes: Int, failures: Int): Float {
        val total = successes + failures
        if (total == 0) return 0.5f
        return (successes.toFloat() / total).coerceIn(0.1f, 0.99f)
    }

    private fun inferDomain(prompt: String, pageUrl: String?): String {
        val lower = (prompt + " " + (pageUrl ?: "")).lowercase()
        return when {
            lower.contains("youtube") || lower.contains("youtu.be") -> "youtube.com"
            lower.contains("search") || lower.contains("find") || lower.contains("look for") -> "search"
            lower.contains("form") || lower.contains("fill") -> "form_fill"
            lower.contains("extract") || lower.contains("scrape") -> "extraction"
            lower.contains("summar") || lower.contains("research") -> "research"
            lower.contains("login") || lower.contains("sign in") -> "auth"
            pageUrl != null -> pageUrl.substringAfter("://").substringBefore("/").take(32)
            else -> "general"
        }
    }

    private fun StrategyEntity.toDomain() = LearnedStrategy(
        id = id,
        domain = domain,
        heuristic = heuristic,
        successCount = successCount,
        failureCount = failureCount,
        confidence = confidence,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}