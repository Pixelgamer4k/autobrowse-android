package com.autobrowse.android.agent.runtime

import com.autobrowse.android.agent.tools.ToolExecutionContext
import com.autobrowse.android.agent.tools.ToolRegistry

class ToolBridge(
    private val toolRegistry: ToolRegistry,
    private val context: ToolExecutionContext,
) {
    suspend fun call(name: String, args: Map<String, Any?> = emptyMap()): String {
        val result = toolRegistry.dispatch(name, args, context)
        return if (result.success) result.output else "ERROR: ${result.output}"
    }
}