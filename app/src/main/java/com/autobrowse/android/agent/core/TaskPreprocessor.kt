package com.autobrowse.android.agent.core

object TaskPreprocessor {
    fun hintsForPrompt(prompt: String): List<String> = buildHints(prompt)

    fun matchedBuiltinSkillNames(prompt: String): List<String> {
        val lower = prompt.lowercase()
        val skills = linkedSetOf<String>()
        if (lower.contains("youtube") || lower.contains("youtu.be")) skills.add("youtube-search")
        if (lower.contains("google") && lower.contains("search")) skills.add("google-search")
        if (lower.contains("search") || lower.contains("find") || lower.contains("look up") || lower.contains("look for")) {
            skills.add("site-search")
        }
        if (lower.contains("form") || lower.contains("fill") || lower.contains("login")) skills.add("form-filling")
        if (lower.contains("github")) skills.add("github-search")
        if (lower.contains("wikipedia") || lower.contains("wiki")) skills.add("wikipedia-research")
        if (lower.contains("stackoverflow") || lower.contains("stack overflow")) skills.add("stackoverflow-qa")
        if (lower.contains("amazon") || lower.contains("shop") || lower.contains("buy")) skills.add("amazon-shopping")
        if (lower.contains("cookie") || lower.contains("consent")) skills.add("cookie-consent")
        if (lower.contains("job") || lower.contains("career")) skills.add("job-search")
        if (lower.contains("price") || lower.contains("compare")) skills.add("price-compare")
        if (lower.contains("weather")) skills.add("weather-check")
        if (lower.contains("research") || lower.contains("summar") || lower.contains("compare")) {
            skills.add("parallel-research")
            skills.add("news-research")
        }
        if (lower.contains("scrape") || lower.contains("extract")) skills.add("data-extraction")
        if (lower.contains("pdf")) skills.add("pdf-generation")
        if (lower.contains("chart") || lower.contains("plot")) skills.add("matplotlib-charts")
        skills.add("autobrowse")
        return skills.toList()
    }

    fun matchedSkillNames(prompt: String, allSkills: List<com.autobrowse.android.skills.SkillMetadata> = emptyList()): List<String> {
        val builtin = matchedBuiltinSkillNames(prompt)
        val learned = allSkills
            .filter { it.category == "learned" }
            .filter { scoreSkillMatch(it, prompt) >= 2 }
            .sortedByDescending { scoreSkillMatch(it, prompt) }
            .take(3)
            .map { it.name }
        return (learned + builtin).distinct()
    }

    fun scoreSkillMatch(skill: com.autobrowse.android.skills.SkillMetadata, prompt: String): Int {
        val lower = prompt.lowercase()
        val tokens = lower.split(Regex("""\W+""")).filter { it.length >= 3 }.toSet()
        var score = 0
        val nameTokens = skill.name.lowercase().split("-")
        nameTokens.forEach { if (it in tokens || it in lower) score += 2 }
        skill.description.lowercase().split(Regex("""\W+"""))
            .filter { it.length >= 4 }
            .forEach { if (it in tokens) score += 1 }
        skill.triggers.forEach { trigger ->
            if (lower.contains(trigger.lowercase())) score += 3
        }
        return score
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

        if (lower.contains("research") || lower.contains("summar") || lower.contains("compare")) {
            hints += "Research: use web_fetch for articles, browser_search for discovery, run_parallel_tasks for multi-source compare."
            hints += "Load parallel-research skill via skill_view if stuck."
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
        lower.contains("github") -> "github"
        lower.contains("wikipedia") || lower.contains("wiki") -> "wikipedia"
        lower.contains("stackoverflow") -> "stackoverflow"
        lower.contains("linkedin") -> "linkedin"
        lower.contains("ebay") -> "ebay"
        lower.contains("imdb") -> "imdb"
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