package com.autobrowse.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autobrowse.android.data.local.entity.AutomationTaskEntity
import com.autobrowse.android.data.local.entity.FeedbackEntryEntity
import com.autobrowse.android.data.local.entity.BrowserTabEntity
import com.autobrowse.android.data.local.entity.ChatMessageEntity
import com.autobrowse.android.data.local.entity.MemoryEntryEntity
import com.autobrowse.android.data.local.entity.SessionEntity
import com.autobrowse.android.data.local.entity.StrategyEntity
import com.autobrowse.android.data.local.entity.TrajectoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, pinnedAt DESC, lastActiveAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, pinnedAt DESC, lastActiveAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("UPDATE sessions SET isActive = 0")
    suspend fun deactivateAll()

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT * FROM sessions
        WHERE id != :excludeId
        ORDER BY isPinned DESC, pinnedAt DESC, lastActiveAt DESC
        LIMIT 1
        """,
    )
    suspend fun getFallbackSession(excludeId: String): SessionEntity?
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId AND LOWER(content) LIKE '%' || LOWER(:term) || '%'
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun findContentMatch(sessionId: String, term: String): ChatMessageEntity?
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE category = :category ORDER BY importance DESC, updatedAt DESC")
    suspend fun getByCategory(category: String): List<MemoryEntryEntity>

    @Query("SELECT * FROM memory_entries ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTop(limit: Int): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MemoryEntryEntity)

    @Query("SELECT * FROM memory_entries WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryEntryEntity?

    @Query(
        """
        SELECT m.* FROM memory_entries m
        JOIN memory_fts fts ON m.rowid = fts.rowid
        WHERE memory_fts MATCH :query
        ORDER BY m.importance DESC, m.updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchFts(query: String, limit: Int): List<MemoryEntryEntity>

    @Query("SELECT * FROM memory_entries WHERE value LIKE '%' || :term || '%' OR key LIKE '%' || :term || '%' ORDER BY importance DESC LIMIT :limit")
    suspend fun searchLike(term: String, limit: Int): List<MemoryEntryEntity>
}

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategies ORDER BY confidence DESC, updatedAt DESC")
    fun observeAll(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM strategies WHERE domain = :domain ORDER BY confidence DESC LIMIT :limit")
    suspend fun getByDomain(domain: String, limit: Int): List<StrategyEntity>

    @Query("SELECT * FROM strategies ORDER BY confidence DESC, successCount DESC LIMIT :limit")
    suspend fun getTop(limit: Int): List<StrategyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(strategy: StrategyEntity)

    @Query("SELECT * FROM strategies WHERE heuristic = :heuristic LIMIT 1")
    suspend fun findByHeuristic(heuristic: String): StrategyEntity?
}

@Dao
interface FeedbackDao {
    @Query(
        """
        SELECT * FROM feedback_entries
        WHERE deleted = 0
        ORDER BY priorityScore DESC, updatedAt DESC
        """,
    )
    fun observeActive(): Flow<List<FeedbackEntryEntity>>

    @Query(
        """
        SELECT * FROM feedback_entries
        WHERE deleted = 0
        ORDER BY priorityScore DESC, updatedAt DESC
        """,
    )
    suspend fun getActiveAll(): List<FeedbackEntryEntity>

    @Query(
        """
        SELECT * FROM feedback_entries
        WHERE deleted = 0
        ORDER BY priorityScore DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getTopForPrompt(limit: Int): List<FeedbackEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FeedbackEntryEntity)

    @Query("SELECT * FROM feedback_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FeedbackEntryEntity?

    @Query(
        """
        UPDATE feedback_entries
        SET deleted = 1, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun softDelete(id: String, now: Long)

    @Query(
        """
        SELECT * FROM feedback_entries
        WHERE deleted = 0
        ORDER BY priorityScore DESC, updatedAt DESC
        """,
    )
    suspend fun getForExport(): List<FeedbackEntryEntity>
}

@Dao
interface TrajectoryDao {
    @Query("SELECT * FROM trajectories WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(sessionId: String, limit: Int): List<TrajectoryEntity>

    @Query("SELECT * FROM trajectories WHERE success = 0 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getFailures(limit: Int): List<TrajectoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trajectory: TrajectoryEntity)

    @Query("DELETE FROM trajectories WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

@Dao
interface AutomationTaskDao {
    @Query("SELECT * FROM automation_tasks WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: String): Flow<List<AutomationTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: AutomationTaskEntity)

    @Update
    suspend fun update(task: AutomationTaskEntity)

    @Query("DELETE FROM automation_tasks WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

@Dao
interface BrowserTabDao {
    @Query("SELECT * FROM browser_tabs WHERE sessionId = :sessionId ORDER BY zIndex ASC")
    fun observeBySession(sessionId: String): Flow<List<BrowserTabEntity>>

    @Query("SELECT COUNT(*) FROM browser_tabs WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: BrowserTabEntity)

    @Query("DELETE FROM browser_tabs WHERE id = :tabId")
    suspend fun delete(tabId: String)

    @Query("DELETE FROM browser_tabs WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}