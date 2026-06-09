package com.autobrowse.android.feedback

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.data.local.dao.FeedbackDao
import com.autobrowse.android.data.local.entity.FeedbackEntryEntity
import com.autobrowse.android.domain.model.FeedbackEntry
import java.util.UUID

class FeedbackManager(
    private val feedbackDao: FeedbackDao,
    private val memoryManager: MemoryManager? = null,
) {
    companion object {
        private val MANDATORY_CATEGORIES = setOf("sources", "purpose", "preference", "performance")
        private val TASK_INTENTS = mapOf(
            "shopping" to listOf("buy", "shop", "amazon", "price", "cart", "product", "ebay", "deal"),
            "research" to listOf("research", "compare", "find", "search", "look", "source", "article"),
            "login" to listOf("login", "sign", "auth", "password", "account"),
        )
    }

    suspend fun captureFromUserMessage(prompt: String, sessionId: String?): Boolean {
        if (!FeedbackDetector.isLikelyFeedback(prompt)) return false
        submit(
            content = prompt.trim(),
            category = FeedbackDetector.detectCategory(prompt),
            tags = FeedbackDetector.extractTags(prompt),
            sessionId = sessionId,
            source = if (FeedbackDetector.isExplicitFeedback(prompt)) "user-explicit" else "auto-detect",
            initialPriority = priorityForCategory(FeedbackDetector.detectCategory(prompt), explicit = true),
        )
        return true
    }

    suspend fun submit(
        content: String,
        category: String = "general",
        tags: String = "",
        sessionId: String? = null,
        source: String = "user",
        initialPriority: Int = 0,
    ): FeedbackEntry {
        val now = System.currentTimeMillis()
        val boosted = initialPriority.takeIf { it > 0 }
            ?: priorityForCategory(category, explicit = source.contains("explicit"))
        val entry = FeedbackEntryEntity(
            id = UUID.randomUUID().toString(),
            content = content.trim(),
            category = category.ifBlank { "general" },
            tags = tags.ifBlank { FeedbackDetector.extractTags(content) },
            priorityScore = boosted,
            upvotes = 0,
            downvotes = 0,
            sessionId = sessionId,
            source = source,
            deleted = false,
            createdAt = now,
            updatedAt = now,
        )
        feedbackDao.upsert(entry)
        syncToMemory(entry.toDomain())
        return entry.toDomain()
    }

    suspend fun upvote(id: String): FeedbackEntry? {
        val entry = feedbackDao.getById(id) ?: return null
        if (entry.deleted) return null
        val updated = entry.copy(
            upvotes = entry.upvotes + 1,
            priorityScore = entry.priorityScore + 2,
            updatedAt = System.currentTimeMillis(),
        )
        feedbackDao.upsert(updated)
        syncToMemory(updated.toDomain())
        return updated.toDomain()
    }

    suspend fun downvote(id: String): FeedbackEntry? {
        val entry = feedbackDao.getById(id) ?: return null
        if (entry.deleted) return null
        val updated = entry.copy(
            downvotes = entry.downvotes + 1,
            priorityScore = entry.priorityScore - 1,
            updatedAt = System.currentTimeMillis(),
        )
        feedbackDao.upsert(updated)
        return updated.toDomain()
    }

    suspend fun delete(id: String): Boolean {
        val entry = feedbackDao.getById(id) ?: return false
        if (entry.deleted) return false
        feedbackDao.softDelete(id, System.currentTimeMillis())
        return true
    }

    suspend fun getForPrompt(limit: Int = 15): List<FeedbackEntry> =
        feedbackDao.getTopForPrompt(limit).map { it.toDomain() }

    suspend fun buildPromptBlock(userPrompt: String, maxContextual: Int = 6): FeedbackPromptBlock {
        val all = feedbackDao.getActiveAll().map { it.toDomain() }
        val mandatory = all.filter { isMandatory(it) }
            .sortedWith(compareByDescending<FeedbackEntry> { it.priorityScore }.thenByDescending { it.updatedAt })

        val contextual = linkedSetOf<FeedbackEntry>()
        searchRelevant(userPrompt, limit = maxContextual).forEach { contextual.add(it) }
        getForPrompt(maxContextual).forEach { contextual.add(it) }
        mandatory.forEach { contextual.remove(it) }

        return FeedbackPromptBlock(
            mandatory = mandatory,
            contextual = contextual.take(maxContextual),
        )
    }

    suspend fun searchRelevant(query: String, limit: Int = 8): List<FeedbackEntry> {
        if (query.isBlank()) return emptyList()
        val lower = query.lowercase()
        val terms = lower.split(Regex("""\W+"""))
            .filter { it.length >= 3 }
            .distinct()
            .take(12)
        val intents = TASK_INTENTS.filter { (_, words) -> words.any { lower.contains(it) } }.keys

        return feedbackDao.getActiveAll()
            .asSequence()
            .map { it.toDomain() }
            .map { entry ->
                val haystack = "${entry.content} ${entry.category} ${entry.tags}".lowercase()
                var score = 0
                terms.forEach { term -> if (haystack.contains(term)) score += 2 }
                entry.tags.split(",").filter { it.isNotBlank() }.forEach { tag ->
                    if (lower.contains(tag.trim().lowercase())) score += 4
                }
                FeedbackDetector.extractDomains(entry.content).forEach { domain ->
                    if (lower.contains(domain)) score += 5
                }
                if (entry.category in MANDATORY_CATEGORIES && intents.isNotEmpty()) score += 3
                if (entry.category == "sources" && intents.contains("shopping")) score += 4
                if (entry.category == "sources" && intents.contains("research")) score += 3
                entry to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<FeedbackEntry, Int>> { it.second }
                    .thenByDescending { it.first.priorityScore },
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    suspend fun buildExportBundle(): FeedbackBundle {
        val entries = feedbackDao.getForExport().map { entity ->
            ExportedFeedbackEntry(
                id = entity.id,
                content = entity.content,
                category = entity.category,
                tags = entity.tags,
                priorityScore = entity.priorityScore,
                upvotes = entity.upvotes,
                downvotes = entity.downvotes,
                sessionId = entity.sessionId,
                source = entity.source,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }
        return FeedbackBundle(entries = entries)
    }

    suspend fun exportJson(): String =
        FeedbackSerializer.toJson(buildExportBundle())

    suspend fun importJson(json: String, merge: Boolean = true): Int {
        val bundle = FeedbackSerializer.fromJson(json)
        if (!merge) {
            feedbackDao.getActiveAll().forEach { existing ->
                feedbackDao.softDelete(existing.id, System.currentTimeMillis())
            }
        }
        var imported = 0
        for (exported in bundle.entries) {
            val now = System.currentTimeMillis()
            val entity = FeedbackEntryEntity(
                id = exported.id.ifBlank { UUID.randomUUID().toString() },
                content = exported.content,
                category = exported.category,
                tags = exported.tags,
                priorityScore = exported.priorityScore,
                upvotes = exported.upvotes,
                downvotes = exported.downvotes,
                sessionId = exported.sessionId,
                source = exported.source,
                deleted = false,
                createdAt = exported.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = exported.updatedAt.takeIf { it > 0 } ?: now,
            )
            feedbackDao.upsert(entity)
            syncToMemory(entity.toDomain())
            imported++
        }
        return imported
    }

    private fun isMandatory(entry: FeedbackEntry): Boolean =
        entry.category in MANDATORY_CATEGORIES ||
            entry.priorityScore >= 4 ||
            entry.source == "user-explicit" ||
            FeedbackDetector.isExplicitFeedback(entry.content)

    private fun priorityForCategory(category: String, explicit: Boolean): Int = when {
        explicit -> 6
        category == "sources" -> 5
        category == "purpose" -> 5
        category == "preference" -> 4
        category == "performance" -> 4
        category == "speed" -> 3
        else -> 2
    }

    private suspend fun syncToMemory(entry: FeedbackEntry) {
        val memory = memoryManager ?: return
        if (!isMandatory(entry) && entry.priorityScore < 3) return
        memory.remember(
            key = "feedback:${entry.category}:${entry.id.take(8)}",
            value = entry.content,
            category = "FEEDBACK",
            importance = entry.priorityScore.coerceIn(7, 10),
            tags = entry.tags,
            source = "feedback-sync",
        )
    }

    private fun FeedbackEntryEntity.toDomain() = FeedbackEntry(
        id = id,
        content = content,
        category = category,
        tags = tags,
        priorityScore = priorityScore,
        upvotes = upvotes,
        downvotes = downvotes,
        sessionId = sessionId,
        source = source,
        deleted = deleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}