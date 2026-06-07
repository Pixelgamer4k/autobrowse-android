package com.autobrowse.android.domain.model

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class AgentRole {
    USER,
    AGENT,
    SYSTEM,
}

enum class BrowserTabStatus {
    IDLE,
    LOADING,
    ACTIVE,
    ERROR,
    AGENT_CONTROLLED,
}

enum class SkillType {
    WEB_REQUEST,
    DATA_EXTRACTION,
    FORM_FILL,
    SUMMARIZE,
    BACKGROUND_TASK,
}

data class BrowserTab(
    val id: String,
    val url: String,
    val title: String = "New Tab",
    val status: BrowserTabStatus = BrowserTabStatus.IDLE,
    val isAgentControlled: Boolean = false,
    val zIndex: Int = 0,
    val layout: BrowserWindowLayout = BrowserWindowLayout(),
    val windowState: BrowserWindowState = BrowserWindowState.NORMAL,
    val savedLayout: BrowserWindowLayout? = null,
    val desktopMode: Boolean = true,
)

data class AutomationTask(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val skillType: SkillType? = null,
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: AgentRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val attachments: List<StoredAttachment> = emptyList(),
)

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val parentSessionId: String? = null,
    val compressionSummary: String? = null,
)

data class MemoryEntry(
    val id: String,
    val key: String,
    val value: String,
    val category: String = "preference",
    val importance: Int = 5,
    val tags: String = "",
    val source: String = "agent",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class LlmConfig(
    val apiKey: String = "",
    val apiUrl: String = "https://api.openai.com/v1/",
    val modelId: String = "gpt-4o-mini",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
)

data class SkillConfig(
    val type: SkillType,
    val enabled: Boolean = true,
    val displayName: String,
    val description: String,
)

data class AgentAction(
    val type: String,
    val target: String? = null,
    val value: String? = null,
    val reasoning: String? = null,
)

data class AutoBrowseRequest(
    val prompt: String,
    val sessionId: String,
    val targetUrl: String? = null,
    val skills: List<SkillType> = emptyList(),
    val attachmentPayload: AttachmentPayload = AttachmentPayload(),
)

data class AutoBrowseResult(
    val success: Boolean,
    val summary: String,
    val extractedData: Map<String, String> = emptyMap(),
    val actions: List<AgentAction> = emptyList(),
    val error: String? = null,
)