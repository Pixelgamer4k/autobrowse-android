package com.autobrowse.android.domain.model

enum class CaptchaSolverProvider {
    CAPSOLVER,
    TWOCAPTCHA,
}

data class CaptchaConfig(
    val enabled: Boolean = false,
    val provider: CaptchaSolverProvider = CaptchaSolverProvider.CAPSOLVER,
    val apiKey: String = "",
    /** Comma-separated hostnames the user authorizes for automated solving, e.g. amazon.com,staging.myapp.com */
    val authorizedDomains: String = "",
    val useAndroidFingerprint: Boolean = true,
    /** Optional http(s) proxy for solver API + WebView (residential recommended). */
    val proxyUrl: String = "",
) {
    fun authorizedHostList(): List<String> =
        authorizedDomains.split(",", ";", "\n")
            .map { it.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.") }
            .filter { it.isNotBlank() }

    fun isConfigured(): Boolean = enabled && apiKey.isNotBlank() && authorizedHostList().isNotEmpty()
}