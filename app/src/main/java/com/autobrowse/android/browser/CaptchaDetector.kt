package com.autobrowse.android.browser

import org.json.JSONArray
import org.json.JSONObject

/**
 * Heuristic CAPTCHA / bot-challenge detection for WebView pages.
 * Does not attempt to solve challenges — only detects and surfaces them to the agent/user.
 */
object CaptchaDetector {
    val DETECTION_SCRIPT = """
        (function() {
            var result = {
                detected: false,
                types: [],
                blocking: false,
                userActionRequired: false,
                provider: null,
                iframeUrls: [],
                pageSignals: [],
                confidence: 'none',
                sitekeys: { recaptcha: null, hcaptcha: null, turnstile: null }
            };
            function firstSitekey(sel) {
                var el = document.querySelector(sel);
                return el ? (el.getAttribute('data-sitekey') || el.getAttribute('data-site-key') || null) : null;
            }
            result.sitekeys.recaptcha = firstSitekey('.g-recaptcha, [data-sitekey]');
            result.sitekeys.hcaptcha = firstSitekey('.h-captcha, [data-hcaptcha-sitekey]');
            result.sitekeys.turnstile = firstSitekey('.cf-turnstile, [data-sitekey]');
            function addType(t) {
                if (result.types.indexOf(t) < 0) result.types.push(t);
                result.detected = true;
            }
            var href = location.href.toLowerCase();
            var text = (document.body ? document.body.innerText : '').toLowerCase();
            var title = (document.title || '').toLowerCase();
            var combined = text + ' ' + title + ' ' + href;

            document.querySelectorAll('iframe[src]').forEach(function(f) {
                var src = (f.src || '').toLowerCase();
                if (src) result.iframeUrls.push(src.slice(0, 200));
                if (src.indexOf('google.com/recaptcha') >= 0 || src.indexOf('recaptcha.net') >= 0) addType('recaptcha');
                if (src.indexOf('hcaptcha.com') >= 0) addType('hcaptcha');
                if (src.indexOf('challenges.cloudflare.com') >= 0) addType('turnstile');
                if (src.indexOf('arkoselabs') >= 0 || src.indexOf('funcaptcha') >= 0) addType('arkose');
            });

            if (document.querySelector('.g-recaptcha, #g-recaptcha, .grecaptcha-badge, [data-sitekey]')) addType('recaptcha');
            if (document.querySelector('.h-captcha, [data-hcaptcha-widget-id]')) addType('hcaptcha');
            if (document.querySelector('.cf-turnstile, [data-turnstile-widget], input[name="cf-turnstile-response"]')) addType('turnstile');
            if (document.querySelector('#px-captcha, .px-captcha, [class*=captcha]')) addType('generic');

            try {
                if (typeof grecaptcha !== 'undefined') addType('recaptcha');
                if (typeof hcaptcha !== 'undefined') addType('hcaptcha');
                if (typeof turnstile !== 'undefined') addType('turnstile');
            } catch (e) {}

            var patterns = [
                ['generic', /verify (you are|you're) (a )?human/],
                ['generic', /security check/],
                ['generic', /unusual traffic/],
                ['generic', /not a robot/],
                ['generic', /complete the (captcha|challenge|verification)/],
                ['generic', /please prove you/],
                ['cloudflare', /checking your browser/],
                ['cloudflare', /just a moment/],
                ['google_block', /unusual traffic from your computer/],
                ['amazon', /enter the characters you see/],
                ['amazon', /opfcaptcha/]
            ];
            patterns.forEach(function(pair) {
                if (pair[1].test(combined)) {
                    addType(pair[0]);
                    result.pageSignals.push(pair[1].source);
                }
            });

            if (href.indexOf('/sorry/') >= 0 || href.indexOf('captcha') >= 0 || href.indexOf('challenge') >= 0) {
                addType('url_challenge');
            }

            var mainContent = text.replace(/\s+/g, ' ').trim();
            var shortPage = mainContent.length < 1400;
            var challengeHeavy = result.detected && (
                shortPage ||
                /verify|robot|captcha|challenge|moment|checking|human/.test(mainContent.slice(0, 900))
            );
            result.blocking = challengeHeavy;
            result.userActionRequired = result.blocking && result.types.some(function(t) {
                return ['recaptcha', 'hcaptcha', 'turnstile', 'arkose', 'generic', 'cloudflare', 'amazon', 'url_challenge'].indexOf(t) >= 0;
            });

            if (result.types.length >= 2) result.confidence = 'high';
            else if (result.types.length === 1) result.confidence = 'medium';
            else if (result.pageSignals.length > 0) result.confidence = 'low';

            result.provider = result.types.length > 0 ? result.types.join('+') : null;
            return JSON.stringify(result);
        })();
    """.trimIndent()

    val ANTI_AUTOMATION_SCRIPT = StealthBrowserConfig.STEALTH_INJECTION

    data class CaptchaSitekeys(
        val recaptcha: String? = null,
        val hcaptcha: String? = null,
        val turnstile: String? = null,
    ) {
        fun primaryType(): String? = when {
            !recaptcha.isNullOrBlank() -> "recaptcha"
            !hcaptcha.isNullOrBlank() -> "hcaptcha"
            !turnstile.isNullOrBlank() -> "turnstile"
            else -> null
        }

        fun primaryKey(): String? = recaptcha ?: hcaptcha ?: turnstile
    }

    data class CaptchaStatus(
        val detected: Boolean,
        val types: List<String>,
        val blocking: Boolean,
        val userActionRequired: Boolean,
        val provider: String?,
        val confidence: String,
        val iframeUrls: List<String>,
        val pageSignals: List<String>,
        val sitekeys: CaptchaSitekeys = CaptchaSitekeys(),
    ) {
        fun formatForAgent(): String = buildString {
            if (!detected) {
                append("no_captcha_detected")
                return@buildString
            }
            appendLine("CAPTCHA_DETECTED: true")
            appendLine("Provider: ${provider ?: "unknown"}")
            appendLine("Confidence: $confidence")
            appendLine("Blocking: $blocking")
            if (types.isNotEmpty()) appendLine("Types: ${types.joinToString(", ")}")
            sitekeys.primaryKey()?.let { appendLine("Sitekey: $it") }
            if (iframeUrls.isNotEmpty()) appendLine("Iframes: ${iframeUrls.take(3).joinToString("; ")}")
            appendLine()
            append("ACTION: On authorized sites call browser_solve_captcha. ")
            append("If not authorized or solver disabled, use browser_wait_for_captcha_clear.")
        }

        fun userBannerMessage(): String? = when {
            !blocking -> null
            else -> "CAPTCHA detected — solving on authorized sites automatically."
        }
    }

    fun parse(raw: String?): CaptchaStatus? {
        if (raw.isNullOrBlank() || raw == "no_webview" || raw.startsWith("error:")) return null
        return runCatching {
            val json = JSONObject(raw)
            CaptchaStatus(
                detected = json.optBoolean("detected"),
                types = json.optJSONArray("types").toStringList(),
                blocking = json.optBoolean("blocking"),
                userActionRequired = json.optBoolean("userActionRequired"),
                provider = json.optString("provider").takeIf { it.isNotBlank() },
                confidence = json.optString("confidence", "none"),
                iframeUrls = json.optJSONArray("iframeUrls").toStringList(),
                pageSignals = json.optJSONArray("pageSignals").toStringList(),
                sitekeys = json.optJSONObject("sitekeys")?.let { keys ->
                    CaptchaSitekeys(
                        recaptcha = keys.optString("recaptcha").takeIf { it.isNotBlank() },
                        hcaptcha = keys.optString("hcaptcha").takeIf { it.isNotBlank() },
                        turnstile = keys.optString("turnstile").takeIf { it.isNotBlank() },
                    )
                } ?: CaptchaSitekeys(),
            )
        }.getOrNull()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }
}