package com.autobrowse.android.agent.core

/**
 * Hermes-style split: [stablePrefix] is byte-stable across runs (prefix-cache friendly),
 * [volatileContext] changes per task (hints, page, task feedback).
 */
data class PromptBundle(
    val stablePrefix: String,
    val volatileContext: String,
    val agentTurns: Int = 1,
) {
    val fullSystem: String = listOf(stablePrefix, volatileContext)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}