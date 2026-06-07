package com.autobrowse.android.agent.core

object TaskPreprocessor {
    fun augmentPrompt(prompt: String): String {
        val hints = buildHints(prompt)
        if (hints.isEmpty()) return prompt
        return buildString {
            appendLine("[Autobrowse task hints — follow these for reliability]")
            hints.forEach { appendLine("- $it") }
            appendLine()
            append("User task: $prompt")
        }
    }

    fun matchedSkillNames(prompt: String): List<String> {
        val lower = prompt.lowercase()
        val skills = linkedSetOf<String>()
        if (lower.contains("youtube") || lower.contains("youtu.be")) skills.add("youtube-search")
        if (lower.contains("google") && lower.contains("search")) skills.add("google-search")
        if (lower.contains("search") || lower.contains("find") || lower.contains("look up") || lower.contains("look for")) {
            skills.add("site-search")
        }
        if (lower.contains("form") || lower.contains("fill") || lower.contains("login")) skills.add("form-filling")
        skills.add("autobrowse")
        return skills.toList()
    }

    private fun buildHints(prompt: String): List<String> {
        val lower = prompt.lowercase()
        val hints = mutableListOf<String>()

        if (containsSearchIntent(lower)) {
            val query = extractSearchQuery(prompt)
            val site = detectSite(lower)
            if (site != null && query != null) {
                hints += "Use browser_search(site=\"$site\", query=\"$query\") — NEVER type in the search box on $site."
                hints += "Then browser_wait(2000) and browser_snapshot to verify results appear in page text."
                hints += "Success = URL contains search params OR snapshot text shows result titles matching \"$query\"."
            } else if (query != null) {
                hints += "Use browser_search(query=\"$query\") for direct search URL navigation."
                hints += "Then browser_wait and browser_snapshot to verify."
            } else {
                hints += "For search tasks, prefer browser_search over browser_type in search boxes."
            }
        }

        if (lower.contains("youtube")) {
            hints += "YouTube uses contenteditable search — typing often fails. Always use browser_search(site=\"youtube\", query=...)."
        }

        if (hints.isNotEmpty()) {
            hints += "Keep tool calls minimal: search tasks should complete in 3-5 steps (search → wait → snapshot → done)."
            hints += "If snapshot shows results, STOP and summarize — do not keep clicking."
        }

        return hints.distinct()
    }

    private fun containsSearchIntent(lower: String): Boolean =
        lower.contains("search") || lower.contains("find") || lower.contains("look for") ||
            lower.contains("look up") || lower.contains("query")

    private fun detectSite(lower: String): String? = when {
        lower.contains("youtube") || lower.contains("youtu.be") -> "youtube"
        lower.contains("google") -> "google"
        lower.contains("bing") -> "bing"
        lower.contains("duckduckgo") || lower.contains("ddg") -> "duckduckgo"
        lower.contains("reddit") -> "reddit"
        lower.contains("twitter") || lower.contains("x.com") -> "twitter"
        lower.contains("amazon") -> "amazon"
        else -> null
    }

    fun extractSearchQuery(prompt: String): String? {
        val patterns = listOf(
            Regex("""(?:search(?:\s+for)?|find|look\s+for|look\s+up|query)\s+(.+?)(?:\s+on\s+\w+)?$""", RegexOption.IGNORE_CASE),
            Regex("""(?:youtube|google|bing).*?(?:search|find)\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?:open|go\s+to)\s+\w+\s+and\s+search\s+(.+)$""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(prompt.trim())
            if (match != null) {
                val query = match.groupValues[1].trim()
                    .removeSuffix(".")
                    .removePrefix("\"")
                    .removeSuffix("\"")
                if (query.length >= 2) return query
            }
        }
        return null
    }
}