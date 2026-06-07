package com.autobrowse.android.data.repository

import com.autobrowse.android.data.local.AutobrowseDatabase
import com.autobrowse.android.data.local.entity.AutomationTaskEntity
import com.autobrowse.android.data.local.entity.BrowserTabEntity
import com.autobrowse.android.data.local.entity.ChatMessageEntity
import com.autobrowse.android.data.local.entity.MemoryEntryEntity
import com.autobrowse.android.data.local.entity.SessionEntity
import com.autobrowse.android.data.local.entity.StrategyEntity
import com.autobrowse.android.attachments.AttachmentJson
import com.autobrowse.android.data.settings.SecureSettingsStore
import com.autobrowse.android.domain.model.AgentRole
import com.autobrowse.android.domain.model.AutomationTask
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.ChatMessage
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.Session
import com.autobrowse.android.domain.model.SkillType
import com.autobrowse.android.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AutobrowseRepository(
    private val database: AutobrowseDatabase,
    private val settingsStore: SecureSettingsStore,
) {
    private val sessionDao = database.sessionDao()
    private val chatDao = database.chatMessageDao()
    private val memoryDao = database.memoryDao()
    private val taskDao = database.automationTaskDao()
    private val tabDao = database.browserTabDao()
    private val strategyDao = database.strategyDao()

    fun observeSessions(): Flow<List<Session>> =
        sessionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeChat(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    fun observeTasks(sessionId: String): Flow<List<AutomationTask>> =
        taskDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    fun observeTabs(sessionId: String): Flow<List<BrowserTab>> =
        tabDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    fun observeMemory(): Flow<List<MemoryEntry>> =
        memoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeStrategies(): Flow<List<LearnedStrategy>> =
        strategyDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getOrCreateActiveSession(): Session {
        val existing = sessionDao.getActive()
        if (existing != null) {
            if (tabDao.countBySession(existing.id) == 0) {
                seedDefaultTab(existing.id)
            }
            return existing.toDomain()
        }

        sessionDao.deactivateAll()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = "Session ${System.currentTimeMillis()}",
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis(),
            isActive = true,
        )
        sessionDao.upsert(session)
        seedDefaultTab(session.id)
        return session.toDomain()
    }

    suspend fun touchSession(sessionId: String) {
        val session = sessionDao.getActive() ?: return
        if (session.id == sessionId) {
            sessionDao.update(session.copy(lastActiveAt = System.currentTimeMillis()))
        }
    }

    suspend fun saveCompressionSummary(sessionId: String, summary: String) {
        val session = sessionDao.getActive() ?: return
        if (session.id == sessionId) {
            sessionDao.update(session.copy(compressionSummary = summary))
        }
    }

    suspend fun saveChatMessage(message: ChatMessage) {
        chatDao.insert(message.toEntity())
        touchSession(message.sessionId)
    }

    suspend fun getRecentChatHistory(sessionId: String, limit: Int = 20): List<ChatMessage> =
        chatDao.getRecent(sessionId, limit).reversed().map { it.toDomain() }

    suspend fun saveTask(task: AutomationTask, sessionId: String) {
        taskDao.upsert(task.toEntity(sessionId))
    }

    suspend fun updateTask(task: AutomationTask, sessionId: String) {
        taskDao.update(task.toEntity(sessionId))
    }

    suspend fun saveTab(tab: BrowserTab, sessionId: String) {
        tabDao.upsert(tab.toEntity(sessionId))
    }

    suspend fun deleteTab(tabId: String) {
        tabDao.delete(tabId)
    }

    suspend fun remember(
        key: String,
        value: String,
        category: String = "preference",
        importance: Int = 5,
        tags: String = "",
        source: String = "agent",
    ) {
        val existing = memoryDao.getByKey(key)
        val now = System.currentTimeMillis()
        val entry = MemoryEntryEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            key = key,
            value = value,
            category = category,
            importance = importance,
            tags = tags,
            source = source,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        memoryDao.upsert(entry)
    }

    suspend fun recall(key: String): String? = memoryDao.getByKey(key)?.value

    suspend fun getMemoryContext(): String {
        val entries = memoryDao.getTop(15)
        if (entries.isEmpty()) return ""
        return entries.joinToString("\n") { "- [${it.category}] ${it.key}: ${it.value}" }
    }

    suspend fun getLlmConfig(): LlmConfig = settingsStore.getLlmConfig()

    suspend fun saveLlmConfig(config: LlmConfig) = settingsStore.saveLlmConfig(config)

    suspend fun getEnabledSkills(): Set<SkillType> =
        settingsStore.getEnabledSkills().mapNotNull { runCatching { SkillType.valueOf(it) }.getOrNull() }.toSet()

    suspend fun saveEnabledSkills(skills: Set<SkillType>) =
        settingsStore.saveEnabledSkills(skills.map { it.name }.toSet())

    private suspend fun seedDefaultTab(sessionId: String) {
        val layout = BrowserWindowLayout.defaultForIndex(0)
        val tab = BrowserTabEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            url = "https://www.google.com",
            title = "New Tab",
            status = BrowserTabStatus.IDLE.name,
            isAgentControlled = false,
            zIndex = 0,
            lastVisitedAt = System.currentTimeMillis(),
            offsetX = layout.offsetX,
            offsetY = layout.offsetY,
            widthFraction = layout.widthFraction,
            heightFraction = layout.heightFraction,
            desktopMode = true,
        )
        tabDao.upsert(tab)
    }
}

private fun SessionEntity.toDomain() = Session(
    id, title, createdAt, lastActiveAt, isActive, parentSessionId, compressionSummary,
)

private fun ChatMessageEntity.toDomain() = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = AgentRole.valueOf(role),
    content = content,
    timestamp = timestamp,
    attachments = AttachmentJson.deserialize(metadataJson),
)

private fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    content = content,
    timestamp = timestamp,
    metadataJson = if (attachments.isNotEmpty()) AttachmentJson.serialize(attachments) else "{}",
)

private fun AutomationTaskEntity.toDomain() = AutomationTask(
    id = id,
    title = title,
    description = description,
    status = TaskStatus.valueOf(status),
    progress = progress,
    createdAt = createdAt,
    skillType = skillType?.let { SkillType.valueOf(it) },
)

private fun AutomationTask.toEntity(sessionId: String) = AutomationTaskEntity(
    id = id,
    sessionId = sessionId,
    title = title,
    description = description,
    status = status.name,
    progress = progress,
    createdAt = createdAt,
    skillType = skillType?.name,
)

private fun BrowserTabEntity.toDomain() = BrowserTab(
    id = id,
    url = url,
    title = title,
    status = BrowserTabStatus.valueOf(status),
    isAgentControlled = isAgentControlled,
    zIndex = zIndex,
    layout = BrowserWindowLayout(offsetX, offsetY, widthFraction, heightFraction),
    desktopMode = desktopMode,
)

private fun BrowserTab.toEntity(sessionId: String) = BrowserTabEntity(
    id = id,
    sessionId = sessionId,
    url = url,
    title = title,
    status = status.name,
    isAgentControlled = isAgentControlled,
    zIndex = zIndex,
    lastVisitedAt = System.currentTimeMillis(),
    offsetX = layout.offsetX,
    offsetY = layout.offsetY,
    widthFraction = layout.widthFraction,
    heightFraction = layout.heightFraction,
    desktopMode = desktopMode,
)

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