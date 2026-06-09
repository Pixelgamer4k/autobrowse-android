package com.autobrowse.android.agent.core

import com.autobrowse.android.domain.model.ToolDefinition
import com.autobrowse.android.feedback.FeedbackDetector

/**
 * Keeps on-device prompts small enough for fast first-token latency on mobile.
 * Remote/cloud agents still receive the full tool catalog.
 */
object LocalToolSelector {
    private const val MAX_TOOLS_FIRST_TURN = 10
    private const val MAX_TOOLS_LATER = 14

    private val CORE_TOOLS = setOf(
        "browser_search",
        "browser_wait",
        "browser_navigate",
        "browser_snapshot",
        "browser_click",
        "browser_type",
        "browser_scroll",
        "browser_back",
    )

    fun select(
        userPrompt: String,
        allTools: List<ToolDefinition>,
        iteration: Int,
    ): List<ToolDefinition> {
        val byName = allTools.associateBy { it.name }
        val selected = linkedSetOf<String>()

        CORE_TOOLS.forEach { name -> selected += name }

        val lower = userPrompt.lowercase()
        when {
            lower.contains("tab") || lower.contains("window") -> selected += setOf(
                "browser_tab_open",
                "browser_tab_close",
                "browser_tab_switch",
                "browser_tab_list",
                "browser_window_arrange",
                "browser_window_focus",
                "browser_window_list",
            )
            lower.contains("form") || lower.contains("fill") || lower.contains("login") ->
                selected += setOf("browser_fill", "browser_select_option", "browser_checkbox_toggle")
            lower.contains("pdf") -> selected += "pdf_generate"
            lower.contains("chart") || lower.contains("plot") -> selected += "chart_generate"
            lower.contains("skill") -> selected += setOf("skills_list", "skill_view")
            lower.contains("feedback") || FeedbackDetector.isLikelyFeedback(userPrompt) ->
                selected += setOf("feedback_submit", "feedback_list")
            lower.contains("extract") || lower.contains("scrape") -> selected += "extract_data"
            lower.contains("summar") -> selected += "summarize"
            lower.contains("parallel") || lower.contains("research") ||
                lower.contains("compare") || lower.contains("side by side") ->
                selected += setOf(
                    "run_parallel_tasks",
                    "delegate_task",
                    "browser_window_arrange",
                    "browser_window_list",
                )
            lower.contains("screenshot") || lower.contains("image") || lower.contains("see") ->
                selected += setOf("browser_screenshot", "browser_vision")
            lower.contains("wait") -> selected += setOf(
                "browser_wait_for_text",
                "browser_wait_for_url",
                "browser_wait_for_element",
            )
            lower.contains("cookie") || lower.contains("popup") || lower.contains("modal") ->
                selected += setOf("browser_close_modal", "browser_get_cookies_notice")
            lower.contains("captcha") || lower.contains("recaptcha") || lower.contains("login") ->
                selected += setOf("browser_detect_captcha", "browser_solve_captcha")
            lower.contains("price") -> selected += "browser_extract_prices"
            lower.contains("email") -> selected += "browser_extract_emails"
        }

        if (iteration > 1) {
            selected += setOf(
                "browser_reload",
                "browser_stop",
                "browser_element_visible",
                "browser_compare_url",
            )
        }

        val limit = if (iteration <= 1) MAX_TOOLS_FIRST_TURN else MAX_TOOLS_LATER
        return selected
            .mapNotNull { byName[it] }
            .take(limit)
            .ifEmpty { allTools.take(limit) }
    }
}