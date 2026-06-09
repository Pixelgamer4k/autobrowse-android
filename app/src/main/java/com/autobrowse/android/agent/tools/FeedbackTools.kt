package com.autobrowse.android.agent.tools

import com.autobrowse.android.feedback.FeedbackDetector
import com.autobrowse.android.feedback.FeedbackManager

class FeedbackSubmitTool(
    private val feedbackManager: FeedbackManager,
) : AgentTool {
    override val name = "feedback_submit"
    override val description =
        "Save user training feedback about purpose, performance, preferences, sources, speed, tips, and innovation. " +
            "Use when the user coaches you, says 'feedback', or shares how you should behave."
    override val parametersJson = """
        {"type":"object","properties":{"content":{"type":"string"},"category":{"type":"string","enum":["purpose","performance","preference","sources","speed","innovation","general"]},"tags":{"type":"string"}},"required":["content"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val content = args["content"]?.toString()?.trim().orEmpty()
        if (content.isBlank()) {
            return ToolExecutionResult("content is required", success = false)
        }
        val category = args["category"]?.toString()?.takeIf { it.isNotBlank() }
            ?: FeedbackDetector.detectCategory(content)
        val tags = args["tags"]?.toString().orEmpty()
            .ifBlank { FeedbackDetector.extractTags(content) }
        val entry = feedbackManager.submit(
            content = content,
            category = category,
            tags = tags,
            sessionId = context.sessionId,
            source = "agent",
            initialPriority = 1,
        )
        return ToolExecutionResult(
            "Saved feedback [${entry.category}] priority=${entry.priorityScore} id=${entry.id.take(8)}",
        )
    }
}

class FeedbackListTool(
    private val feedbackManager: FeedbackManager,
) : AgentTool {
    override val name = "feedback_list"
    override val description = "List active user training feedback entries sorted by priority."
    override val parametersJson = """
        {"type":"object","properties":{"limit":{"type":"integer"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val limit = args["limit"]?.toString()?.toIntOrNull()?.coerceIn(1, 30) ?: 10
        val entries = feedbackManager.getForPrompt(limit)
        if (entries.isEmpty()) return ToolExecutionResult("No feedback entries yet.")
        val formatted = entries.joinToString("\n") { entry ->
            "- [${entry.category}|p=${entry.priorityScore}] ${entry.content.take(200)}"
        }
        return ToolExecutionResult(formatted)
    }
}