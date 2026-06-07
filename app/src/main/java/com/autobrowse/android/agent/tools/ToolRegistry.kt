package com.autobrowse.android.agent.tools

import com.autobrowse.android.domain.model.ToolDefinition

class ToolRegistry(tools: List<AgentTool>) {
    private val toolMap = tools.associateBy { it.name }

    fun definitions(): List<ToolDefinition> = toolMap.values.map { it.toDefinition() }

    fun get(name: String): AgentTool? = toolMap[name]

    suspend fun dispatch(
        name: String,
        args: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val tool = toolMap[name]
            ?: return ToolExecutionResult(output = "Unknown tool: $name", success = false)
        return runCatching { tool.execute(args, context) }
            .getOrElse { ToolExecutionResult(output = "Tool error: ${it.message}", success = false) }
    }
}