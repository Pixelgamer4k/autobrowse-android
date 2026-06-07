package com.autobrowse.android.browser

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AddressBarNavigation {
    private val domainPattern = Regex(
        """^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(/.*)?$""",
    )
    private val ipPattern = Regex(
        """^\d{1,3}(\.\d{1,3}){3}(:\d+)?(/.*)?$""",
    )

    fun resolve(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        return if (isDirectNavigation(trimmed)) {
            normalizeUrl(trimmed)
        } else {
            val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
            "https://www.google.com/search?q=$encoded"
        }
    }

    fun normalizeUrl(input: String): String = when {
        input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) -> input
        input.startsWith("about:", ignoreCase = true) ||
            input.startsWith("file:", ignoreCase = true) -> input
        else -> "https://$input"
    }

    fun urlsMatch(left: String?, right: String?): Boolean {
        return normalizeForCompare(left) == normalizeForCompare(right)
    }

    fun normalizeForCompare(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .lowercase()
    }

    private fun isDirectNavigation(input: String): Boolean {
        if (input.contains(' ')) return false
        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
        ) {
            return true
        }
        if (input.startsWith("localhost", ignoreCase = true) ||
            input.startsWith("about:", ignoreCase = true) ||
            input.startsWith("file:", ignoreCase = true)
        ) {
            return true
        }
        if (ipPattern.matches(input)) return true
        return domainPattern.matches(input)
    }
}