package com.autobrowse.android.domain.model

enum class MemoryCategory {
    USER,
    MEMORY,
    PREFERENCE,
    STRATEGY,
    SESSION,
}

enum class AgentPhase {
    IDLE,
    THINKING,
    EXECUTING_TOOL,
    REFLECTING,
    COMPRESSING,
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
)

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class ToolResult(
    val toolCallId: String,
    val name: String,
    val output: String,
    val success: Boolean,
)

data class AgentTurn(
    val iteration: Int,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val assistantContent: String? = null,
    val reasoning: String? = null,
)

data class AgentConversationResult(
    val success: Boolean,
    val finalResponse: String,
    val turns: List<AgentTurn>,
    val actions: List<AgentAction> = emptyList(),
    val extractedData: Map<String, String> = emptyMap(),
    val iterationsUsed: Int = 0,
    val memoriesLearned: Int = 0,
    val strategiesUpdated: Int = 0,
    val skillsLearned: Int = 0,
    val error: String? = null,
)

data class LearnedStrategy(
    val id: String,
    val domain: String,
    val heuristic: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val confidence: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class TrajectoryRecord(
    val id: String,
    val sessionId: String,
    val taskId: String,
    val prompt: String,
    val success: Boolean,
    val turnsJson: String,
    val reflection: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class BrowserContext(
    val pageUrl: String?,
    val pageHtml: String?,
    val pageText: String?,
)

data class AgentProgress(
    val phase: AgentPhase,
    val iteration: Int,
    val maxIterations: Int,
    val currentTool: String? = null,
    val message: String = "",
)