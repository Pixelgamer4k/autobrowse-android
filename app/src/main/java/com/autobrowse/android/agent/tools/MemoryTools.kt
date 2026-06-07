package com.autobrowse.android.agent.tools

import com.autobrowse.android.agent.memory.MemoryManager

class MemoryRememberTool(
    private val memoryManager: MemoryManager,
) : AgentTool {
    override val name = "memory_remember"
    override val description = "Store a long-term memory about the user or a learned fact for future sessions."
    override val parametersJson = """
        {"type":"object","properties":{"key":{"type":"string"},"value":{"type":"string"},"category":{"type":"string","enum":["USER","MEMORY","PREFERENCE","STRATEGY"]},"importance":{"type":"integer"}},"required":["key","value"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val key = args["key"]?.toString().orEmpty()
        val value = args["value"]?.toString().orEmpty()
        val category = args["category"]?.toString() ?: "MEMORY"
        val importance = args["importance"]?.toString()?.toIntOrNull() ?: 7
        if (key.isBlank() || value.isBlank()) {
            return ToolExecutionResult("key and value are required", success = false)
        }
        memoryManager.remember(key, value, category, importance, source = "agent_tool")
        return ToolExecutionResult("Remembered [$category] $key")
    }
}

class MemoryRecallTool(
    private val memoryManager: MemoryManager,
) : AgentTool {
    override val name = "memory_recall"
    override val description = "Search long-term memory for relevant facts and user preferences."
    override val parametersJson = """
        {"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val query = args["query"]?.toString().orEmpty()
        if (query.isBlank()) return ToolExecutionResult("query is required", success = false)
        val results = memoryManager.search(query, limit = 8)
        if (results.isEmpty()) return ToolExecutionResult("No memories found for: $query")
        val formatted = results.joinToString("\n") { "- [${it.category}] ${it.key}: ${it.value}" }
        return ToolExecutionResult(formatted)
    }
}

class SessionSearchTool(
    private val memoryManager: MemoryManager,
) : AgentTool {
    override val name = "session_search"
    override val description = "Search past conversation summaries and session notes."
    override val parametersJson = """
        {"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val query = args["query"]?.toString().orEmpty()
        val results = memoryManager.searchSessions(query, context.sessionId, limit = 5)
        if (results.isEmpty()) return ToolExecutionResult("No session notes found.")
        return ToolExecutionResult(results.joinToString("\n"))
    }
}