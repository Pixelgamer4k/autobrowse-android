package com.autobrowse.android.browser

import com.autobrowse.android.domain.model.CaptchaConfig
import com.autobrowse.android.domain.model.CaptchaSolverProvider
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class CaptchaSolverService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class SolveRequest(
        val pageUrl: String,
        val captchaType: String,
        val siteKey: String,
    )

    data class SolveResult(
        val token: String,
        val provider: String,
    )

    fun isAuthorized(pageUrl: String, config: CaptchaConfig): Boolean {
        val hosts = config.authorizedHostList()
        if (hosts.isEmpty()) return false
        val host = runCatching { URI(pageUrl).host?.lowercase() }.getOrNull() ?: return false
        return hosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    suspend fun solve(request: SolveRequest, config: CaptchaConfig): SolveResult {
        require(config.isConfigured()) { "CAPTCHA solver not configured (enable + API key + authorized domains)" }
        require(isAuthorized(request.pageUrl, config)) {
            "Host not in authorized domain allowlist — automated solving blocked"
        }
        return when (config.provider) {
            CaptchaSolverProvider.CAPSOLVER -> solveCapSolver(request, config.apiKey)
            CaptchaSolverProvider.TWOCAPTCHA -> solve2Captcha(request, config.apiKey)
        }
    }

    private suspend fun solveCapSolver(request: SolveRequest, apiKey: String): SolveResult {
        val taskType = when (request.captchaType) {
            "hcaptcha" -> "HCaptchaTaskProxyLess"
            "turnstile" -> "AntiTurnstileTaskProxyLess"
            else -> "ReCaptchaV2TaskProxyLess"
        }
        val task = JSONObject().apply {
            put("type", taskType)
            put("websiteURL", request.pageUrl)
            put("websiteKey", request.siteKey)
        }
        val createBody = JSONObject().apply {
            put("clientKey", apiKey)
            put("task", task)
        }
        val createResp = postJson("https://api.capsolver.com/createTask", createBody.toString())
        val taskId = createResp.optString("taskId")
        check(taskId.isNotBlank()) { "CapSolver createTask failed: ${createResp.optString("errorDescription")}" }

        repeat(40) {
            delay(3000)
            val pollBody = JSONObject().apply {
                put("clientKey", apiKey)
                put("taskId", taskId)
            }
            val poll = postJson("https://api.capsolver.com/getTaskResult", pollBody.toString())
            when (poll.optString("status")) {
                "ready" -> {
                    val token = poll.optJSONObject("solution")?.optString("gRecaptchaResponse")
                        ?: poll.optJSONObject("solution")?.optString("token")
                        ?: poll.optJSONObject("solution")?.optString("text")
                    check(!token.isNullOrBlank()) { "CapSolver returned empty token" }
                    return SolveResult(token = token, provider = "capsolver")
                }
                "failed" -> error("CapSolver failed: ${poll.optString("errorDescription")}")
            }
        }
        error("CapSolver timeout waiting for solution")
    }

    private suspend fun solve2Captcha(request: SolveRequest, apiKey: String): SolveResult {
        val method = when (request.captchaType) {
            "hcaptcha" -> "hcaptcha"
            "turnstile" -> "turnstile"
            else -> "userrecaptcha"
        }
        val submitUrl = buildString {
            append("https://2captcha.com/in.php?json=1&key=")
            append(apiKey)
            append("&method=")
            append(method)
            append("&sitekey=")
            append(request.siteKey)
            append("&pageurl=")
            append(java.net.URLEncoder.encode(request.pageUrl, "UTF-8"))
        }
        val submit = getJson(submitUrl)
        check(submit.optInt("status") == 1) {
            "2Captcha submit failed: ${submit.optString("request")}"
        }
        val captchaId = submit.optString("request")
        repeat(40) {
            delay(5000)
            val pollUrl = "https://2captcha.com/res.php?json=1&key=$apiKey&action=get&id=$captchaId"
            val poll = getJson(pollUrl)
            if (poll.optString("status") == "1") {
                val token = poll.optString("request")
                check(token.isNotBlank()) { "2Captcha returned empty token" }
                return SolveResult(token = token, provider = "2captcha")
            }
            if (poll.optString("request") != "CAPCHA_NOT_READY") {
                error("2Captcha failed: ${poll.optString("request")}")
            }
        }
        error("2Captcha timeout waiting for solution")
    }

    private fun postJson(url: String, body: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            check(response.isSuccessful) { "HTTP ${response.code}: $text" }
            return JSONObject(text)
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            check(response.isSuccessful) { "HTTP ${response.code}: $text" }
            return JSONObject(text)
        }
    }
}