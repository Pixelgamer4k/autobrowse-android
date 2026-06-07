package com.autobrowse.android.agent.tools

import com.autobrowse.android.agent.orchestration.TaskOrchestrator
import com.autobrowse.android.domain.model.AutoBrowseRequest
import com.autobrowse.android.domain.model.BrowserContext
import java.util.UUID

class TodoWriteTool : AgentTool {
    override val name = "todo_write"
    override val description = "Create or update a task plan as a numbered todo list for multi-step automation."
    override val parametersJson = """
        {"type":"object","properties":{"todos":{"type":"array","items":{"type":"string"}},"merge":{"type":"boolean"}},"required":["todos"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        @Suppress("UNCHECKED_CAST")
        val todos = (args["todos"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val formatted = todos.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
        context.extractedData["todo_list"] = formatted
        return ToolExecutionResult("Plan:\n$formatted")
    }
}

class ClarifyTool : AgentTool {
    override val name = "clarify"
    override val description = "Ask the user a clarifying question when the task is ambiguous. Stores question for the agent to surface."
    override val parametersJson = """
        {"type":"object","properties":{"question":{"type":"string"}},"required":["question"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val question = args["question"]?.toString().orEmpty()
        context.extractedData["clarification_needed"] = question
        return ToolExecutionResult("CLARIFICATION NEEDED: $question")
    }
}

class DelegateTaskTool(
    private val orchestratorProvider: () -> TaskOrchestrator,
) : AgentTool {
    override val name = "delegate_task"
    override val description = "Run an independent subtask in parallel (separate agent loop). Returns when complete."
    override val parametersJson = """
        {"type":"object","properties":{"prompt":{"type":"string"},"wait":{"type":"boolean"}},"required":["prompt"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val prompt = args["prompt"]?.toString().orEmpty()
        val wait = args["wait"]?.toString()?.toBooleanStrictOrNull() ?: true
        val taskId = UUID.randomUUID().toString()
        val request = AutoBrowseRequest(prompt = prompt, sessionId = context.sessionId)
        val browserContext = BrowserContext(
            pageUrl = context.pageUrl,
            pageHtml = context.pageHtml,
            pageText = context.pageText,
        )
        return if (wait) {
            val result = orchestratorProvider().runSingle(request, browserContext)
            context.parallelTaskResults[taskId] = result.finalResponse
            ToolExecutionResult(
                if (result.success) result.finalResponse else "Failed: ${result.error}",
                success = result.success,
            )
        } else {
            orchestratorProvider().launch(request, browserContext)
            ToolExecutionResult("Delegated task $taskId (running in background)")
        }
    }
}

class RunParallelTasksTool(
    private val orchestratorProvider: () -> TaskOrchestrator,
) : AgentTool {
    override val name = "run_parallel_tasks"
    override val description = "Run multiple independent prompts in parallel and return all results."
    override val parametersJson = """
        {"type":"object","properties":{"prompts":{"type":"array","items":{"type":"string"}}},"required":["prompts"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        @Suppress("UNCHECKED_CAST")
        val prompts = (args["prompts"] as? List<*>)?.mapNotNull { it?.toString() }?.filter { it.isNotBlank() }.orEmpty()
        if (prompts.isEmpty()) return ToolExecutionResult("prompts required", success = false)
        val browserContext = BrowserContext(
            pageUrl = context.pageUrl,
            pageHtml = context.pageHtml,
            pageText = context.pageText,
        )
        val results = orchestratorProvider().runParallel(
            sessionId = context.sessionId,
            prompts = prompts,
            browserContext = browserContext,
        )
        val output = results.mapIndexed { index, result ->
            "Task ${index + 1}: ${if (result.success) result.finalResponse else result.error}"
        }.joinToString("\n\n")
        return ToolExecutionResult(output)
    }
}