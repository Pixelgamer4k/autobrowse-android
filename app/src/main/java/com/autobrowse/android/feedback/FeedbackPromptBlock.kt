package com.autobrowse.android.feedback

import com.autobrowse.android.domain.model.FeedbackEntry

data class FeedbackPromptBlock(
    val mandatory: List<FeedbackEntry>,
    val contextual: List<FeedbackEntry>,
) {
    fun allEntries(): List<FeedbackEntry> = (mandatory + contextual).distinctBy { it.id }

    fun formatMandatory(maxCharsPerEntry: Int = 420): String {
        if (mandatory.isEmpty()) return ""
        return buildString {
            appendLine("## Mandatory training (ALWAYS apply — every session, every task)")
            mandatory.forEach { entry ->
                appendLine("- [${entry.category}|p${entry.priorityScore}|MANDATORY] ${entry.content.take(maxCharsPerEntry)}")
            }
        }.trim()
    }

    fun formatContextual(maxCharsPerEntry: Int = 360): String {
        if (contextual.isEmpty()) return ""
        return buildString {
            appendLine("## Task-relevant training feedback")
            contextual.forEach { entry ->
                appendLine("- [${entry.category}|p${entry.priorityScore}] ${entry.content.take(maxCharsPerEntry)}")
            }
        }.trim()
    }
}