package com.autobrowse.android.feedback

object FeedbackDetector {
    private val explicitPatterns = listOf(
        Regex("""\bfeedback\b""", RegexOption.IGNORE_CASE),
        Regex("""#\s*feedback""", RegexOption.IGNORE_CASE),
        Regex("""my\s+feedback\s*:""", RegexOption.IGNORE_CASE),
        Regex("""training\s+feedback\s*:""", RegexOption.IGNORE_CASE),
        Regex("""coach(?:ing)?\s*:""", RegexOption.IGNORE_CASE),
    )

    private val signalPhrases = listOf(
        "i like",
        "i don't like",
        "i dont like",
        "you should",
        "you shouldn't",
        "you shouldnt",
        "your purpose",
        "when you",
        "how you",
        "faster",
        "better way",
        "best source",
        "best website",
        "certain website",
        "use this site",
        "use these sites",
        "always use",
        "only use",
        "never use",
        "trick",
        "tip",
        "prefer",
        "don't do",
        "dont do",
        "always do",
        "never do",
        "innovation",
        "creative",
        "perform",
        "train you",
        "remember this",
        "from now on",
        "next time",
        "do it like",
        "instead of",
        "too slow",
        "too many steps",
        "waste time",
        "god tier",
        "purpose is",
        "your job is",
        "your role is",
    )

    fun isExplicitFeedback(text: String): Boolean =
        explicitPatterns.any { it.containsMatchIn(text) }

    fun isLikelyFeedback(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (isExplicitFeedback(trimmed)) return true
        val lower = trimmed.lowercase()
        val signalCount = signalPhrases.count { lower.contains(it) }
        val hasDomain = extractDomains(trimmed).isNotEmpty()
        return when {
            hasDomain && signalCount >= 1 -> true
            signalCount >= 2 -> true
            lower.contains("website") && (lower.contains("use") || lower.contains("prefer")) -> true
            else -> false
        }
    }

    fun extractDomains(text: String): List<String> {
        val found = mutableListOf<String>()
        Regex("""(?:https?://)?(?:www\.)?([a-z0-9][-a-z0-9]*\.(?:com|org|net|io|co|edu|gov|dev))""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .forEach { match ->
                val domain = match.groupValues[1].lowercase()
                if (domain !in found) found += domain
            }
        listOf("amazon", "ebay", "youtube", "google", "reddit", "wikipedia", "github", "bestbuy", "walmart")
            .forEach { site ->
                if (text.contains(site, ignoreCase = true) && "$site.com" !in found) {
                    found += "$site.com"
                }
            }
        return found
    }

    fun detectCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("purpose") || lower.contains("mission") ||
                lower.contains("who you are") || lower.contains("your role") ||
                lower.contains("your job") -> "purpose"
            lower.contains("faster") || lower.contains("speed") ||
                lower.contains("quicker") || lower.contains("too slow") ||
                lower.contains("fewer steps") -> "speed"
            lower.contains("source") || lower.contains("website") ||
                lower.contains(".com") || lower.contains(".org") ||
                lower.contains("best site") -> "sources"
            lower.contains("like") || lower.contains("prefer") ||
                lower.contains("don't like") || lower.contains("dont like") ||
                lower.contains("hate") -> "preference"
            lower.contains("perform") || lower.contains("how you") ||
                lower.contains("when you") || lower.contains("should") -> "performance"
            lower.contains("innovat") || lower.contains("creative") ||
                lower.contains("idea") || lower.contains("trick") ||
                lower.contains("tip") -> "innovation"
            else -> "general"
        }
    }

    fun extractTags(text: String): String {
        val lower = text.lowercase()
        val tags = mutableListOf<String>()
        if (isExplicitFeedback(text)) tags += "explicit"
        if (lower.contains("amazon")) tags += "amazon"
        if (lower.contains("youtube")) tags += "youtube"
        if (lower.contains("google")) tags += "google"
        if (lower.contains("window")) tags += "windows"
        if (lower.contains("search")) tags += "search"
        if (lower.contains("compare")) tags += "compare"
        if (lower.contains("research")) tags += "research"
        return tags.distinct().joinToString(",")
    }
}