package com.autobrowse.android.agent.core

import com.autobrowse.android.domain.model.ToolDefinition
import com.autobrowse.android.feedback.FeedbackDetector

/**
 * Keeps cloud API requests fast by sending a task-relevant tool subset instead of the full catalog.
 * Full catalog is still available from turn 2 onward when the agent may need niche tools.
 */
object CloudToolSelector {
    private const val MAX_TOOLS_FIRST_TURN = 14
    private const val MAX_TOOLS_LATER = 22

    private val CORE_TOOLS = setOf(
        "browser_search",
        "browser_wait",
        "browser_navigate",
        "browser_snapshot",
        "browser_click",
        "browser_type",
        "browser_scroll",
        "browser_back",
        "browser_tab_open",
        "browser_tab_list",
        "browser_window_arrange",
        "browser_window_focus",
        "browser_window_list",
        "feedback_submit",
        "feedback_list",
        "skills_list",
        "skill_view",
        "memory_remember",
        "memory_recall",
    )

    fun select(
        userPrompt: String,
        allTools: List<ToolDefinition>,
        iteration: Int,
    ): List<ToolDefinition> {
        val byName = allTools.associateBy { it.name }
        val selected = linkedSetOf<String>()
        selected.addAll(CORE_TOOLS)

        val lower = userPrompt.lowercase()
        when {
            lower.contains("tab") || lower.contains("window") -> selected += setOf(
                "browser_tab_close",
                "browser_tab_switch",
                "browser_window_resize",
            )
            lower.contains("form") || lower.contains("fill") || lower.contains("login") ->
                selected += setOf("browser_fill", "browser_select_option", "browser_checkbox_toggle")
            lower.contains("pdf") -> selected += "pdf_generate"
            lower.contains("chart") || lower.contains("plot") -> selected += "chart_generate"
            lower.contains("skill") -> selected += setOf("skill_manage", "skill_creator")
            lower.contains("extract") || lower.contains("scrape") ->
                selected += setOf("extract_data", "browser_extract_table", "browser_get_links")
            lower.contains("summar") -> selected += "summarize"
            lower.contains("parallel") || lower.contains("research") ||
                lower.contains("compare") || lower.contains("side by side") ->
                selected += setOf("run_parallel_tasks", "delegate_task", "browser_readability")
            lower.contains("screenshot") || lower.contains("image") || lower.contains("see") ->
                selected += setOf("browser_screenshot", "browser_vision")
            lower.contains("cookie") || lower.contains("popup") || lower.contains("modal") ->
                selected += setOf(
                    "browser_close_modal",
                    "browser_dismiss_overlays",
                    "browser_accept_cookies",
                )
            lower.contains("captcha") || lower.contains("recaptcha") || lower.contains("hcaptcha") ||
                lower.contains("turnstile") || lower.contains("cloudflare") || lower.contains("verify") ||
                (lower.contains("login") && lower.contains("human")) ->
                selected += setOf(
                    "browser_detect_captcha",
                    "browser_wait_for_captcha_clear",
                )
            lower.contains("price") -> selected += "browser_extract_prices"
            lower.contains("feedback") || FeedbackDetector.isLikelyFeedback(userPrompt) ->
                selected += setOf("feedback_submit", "feedback_list")
        }

        if (iteration > 1) {
            selected += setOf(
                "browser_reload",
                "browser_stop",
                "browser_wait_for_text",
                "browser_wait_for_url",
                "browser_execute_js",
                "browser_detect_captcha",
                "browser_wait_for_captcha_clear",
                "reflect",
                "todo_write",
            )
        }

        val limit = if (iteration <= 1) MAX_TOOLS_FIRST_TURN else MAX_TOOLS_LATER
        return selected
            .mapNotNull { byName[it] }
            .take(limit)
            .ifEmpty { allTools.take(limit) }
    }
}