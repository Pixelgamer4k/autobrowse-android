package com.autobrowse.android.agent.training

import com.autobrowse.android.data.local.dao.StrategyDao
import com.autobrowse.android.data.local.entity.StrategyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class TrainingDataSeeder(private val strategyDao: StrategyDao) {

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (strategyDao.getTop(1).isNotEmpty()) return@withContext
        BUNDLED_STRATEGIES.forEach { strategyDao.upsert(it) }
    }

    companion object {
        private val now = System.currentTimeMillis()

        val BUNDLED_STRATEGIES = listOf(
            strategy(
                domain = "youtube.com",
                heuristic = "YouTube search: use browser_search(site=youtube, query=...) — never type in the search box. Verify with snapshot showing video titles.",
                confidence = 0.95f,
                successes = 10,
            ),
            strategy(
                domain = "search",
                heuristic = "All site searches: browser_search → browser_wait(2000) → browser_snapshot. Stop when results visible. 3-5 tool calls max.",
                confidence = 0.92f,
                successes = 10,
            ),
            strategy(
                domain = "google.com",
                heuristic = "Google search: browser_search(site=google, query=...). Check URL has q= param and snapshot shows result headings.",
                confidence = 0.9f,
                successes = 8,
            ),
            strategy(
                domain = "general",
                heuristic = "After browser_navigate or browser_click, always browser_wait then browser_snapshot before next action.",
                confidence = 0.88f,
                successes = 8,
            ),
            strategy(
                domain = "general",
                heuristic = "Prefer @eN refs from browser_snapshot over CSS selectors. Re-snapshot after every navigation.",
                confidence = 0.85f,
                successes = 7,
            ),
            strategy(
                domain = "form_fill",
                heuristic = "Forms: snapshot → find input refs → browser_type → browser_press Enter → snapshot to verify.",
                confidence = 0.82f,
                successes = 6,
            ),
            strategy(
                domain = "extraction",
                heuristic = "Extract data only after browser_snapshot confirms correct page loaded.",
                confidence = 0.8f,
                successes = 5,
            ),
            strategy(
                domain = "research",
                heuristic = "Multi-site research: browser_tab_open per site, browser_search in each, summarize with extract_data.",
                confidence = 0.78f,
                successes = 5,
            ),
        )

        private fun strategy(
            domain: String,
            heuristic: String,
            confidence: Float,
            successes: Int,
        ) = StrategyEntity(
            id = UUID.randomUUID().toString(),
            domain = domain,
            heuristic = heuristic,
            successCount = successes,
            failureCount = 1,
            confidence = confidence,
            createdAt = now,
            updatedAt = now,
        )
    }
}