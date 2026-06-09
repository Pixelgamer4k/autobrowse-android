package com.autobrowse.android.feedback

import com.autobrowse.android.data.local.dao.FeedbackDao
import com.autobrowse.android.data.local.entity.FeedbackEntryEntity
import com.autobrowse.android.domain.model.FeedbackEntry
import java.util.UUID

class FeedbackManager(
    private val feedbackDao: FeedbackDao,
) {
    suspend fun captureFromUserMessage(prompt: String, sessionId: String?): Boolean {
        if (!FeedbackDetector.isLikelyFeedback(prompt)) return false
        submit(
            content = prompt.trim(),
            category = FeedbackDetector.detectCategory(prompt),
            tags = FeedbackDetector.extractTags(prompt),
            sessionId = sessionId,
            source = if (FeedbackDetector.isExplicitFeedback(prompt)) "user-explicit" else "auto-detect",
            initialPriority = if (FeedbackDetector.isExplicitFeedback(prompt)) 2 else 1,
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
        val entry = FeedbackEntryEntity(
            id = UUID.randomUUID().toString(),
            content = content.trim(),
            category = category.ifBlank { "general" },
            tags = tags,
            priorityScore = initialPriority,
            upvotes = 0,
            downvotes = 0,
            sessionId = sessionId,
            source = source,
            deleted = false,
            createdAt = now,
            updatedAt = now,
        )
        feedbackDao.upsert(entry)
        return entry.toDomain()
    }

    suspend fun upvote(id: String): FeedbackEntry? {
        val entry = feedbackDao.getById(id) ?: return null
        if (entry.deleted) return null
        val updated = entry.copy(
            upvotes = entry.upvotes + 1,
            priorityScore = entry.priorityScore + 1,
            updatedAt = System.currentTimeMillis(),
        )
        feedbackDao.upsert(updated)
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

    suspend fun searchRelevant(query: String, limit: Int = 6): List<FeedbackEntry> {
        if (query.isBlank()) return emptyList()
        val terms = query.lowercase()
            .split(Regex("""\W+"""))
            .filter { it.length >= 4 }
            .distinct()
            .take(8)
        if (terms.isEmpty()) return emptyList()
        return feedbackDao.getActiveAll()
            .asSequence()
            .map { it.toDomain() }
            .map { entry ->
                val haystack = "${entry.content} ${entry.category} ${entry.tags}".lowercase()
                val score = terms.count { haystack.contains(it) }
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
            feedbackDao.upsert(
                FeedbackEntryEntity(
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
                ),
            )
            imported++
        }
        return imported
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