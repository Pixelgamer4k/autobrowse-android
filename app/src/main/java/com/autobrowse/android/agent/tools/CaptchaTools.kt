package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.CaptchaDetector
import kotlinx.coroutines.delay

class BrowserDetectCaptchaTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_detect_captcha"
    override val description =
        "Detect CAPTCHA or bot-challenge pages (reCAPTCHA, hCaptcha, Cloudflare Turnstile). " +
            "Returns whether user must solve manually in the browser window."
    override val parametersJson = """{"type":"object","properties":{"tab_id":{"type":"string"}}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val raw = browserController.evaluateScript(CaptchaDetector.DETECTION_SCRIPT, tabId) ?: "no_webview"
        val status = CaptchaDetector.parse(raw)
        if (status == null) {
            return ToolExecutionResult(raw, success = raw != "no_webview")
        }
        CaptchaTools.applyStatus(context, status)
        context.extractedData["captcha_detected"] = status.detected.toString()
        context.extractedData["captcha_provider"] = status.provider.orEmpty()
        return ToolExecutionResult(status.formatForAgent(), success = true)
    }
}

class BrowserWaitForCaptchaClearTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_wait_for_captcha_clear"
    override val description =
        "Poll until CAPTCHA/bot challenge clears (user solves it in the browser window). " +
            "Use after telling the user to complete the challenge."
    override val parametersJson = """
        {"type":"object","properties":{"timeout_ms":{"type":"integer","description":"Max wait (default 120000)"},"tab_id":{"type":"string"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val timeout = args["timeout_ms"]?.toString()?.toLongOrNull() ?: 120_000L
        val start = System.currentTimeMillis()
        var lastStatus: CaptchaDetector.CaptchaStatus? = null

        while (System.currentTimeMillis() - start < timeout) {
            val raw = browserController.evaluateScript(CaptchaDetector.DETECTION_SCRIPT, tabId) ?: "no_webview"
            val status = CaptchaDetector.parse(raw)
            lastStatus = status
            if (status == null || !status.userActionRequired) {
                CaptchaTools.applyStatus(context, status ?: CaptchaDetector.CaptchaStatus(
                    detected = false,
                    types = emptyList(),
                    blocking = false,
                    userActionRequired = false,
                    provider = null,
                    confidence = "none",
                    iframeUrls = emptyList(),
                    pageSignals = emptyList(),
                ))
                BrowserToolHelper.refreshPageContext(context, browserController)
                return ToolExecutionResult(
                    "captcha_cleared — page ready to continue.\nURL: ${context.pageUrl.orEmpty()}",
                    success = true,
                )
            }
            delay(2000)
        }

        val msg = lastStatus?.formatForAgent() ?: "timeout waiting for captcha clear"
        return ToolExecutionResult(
            "timeout: captcha still present after ${timeout}ms.\n$msg",
            success = false,
        )
    }
}

object CaptchaTools {
    fun createAll(controller: BrowserController): List<AgentTool> = listOf(
        BrowserDetectCaptchaTool(controller),
        BrowserWaitForCaptchaClearTool(controller),
    )

    fun applyStatus(context: ToolExecutionContext, status: CaptchaDetector.CaptchaStatus) {
        context.captchaDetected = status.detected
        context.captchaUserActionRequired = status.userActionRequired
        context.captchaInfo = status.formatForAgent()
        context.captchaBannerMessage = status.userBannerMessage()
    }

    suspend fun refreshCaptchaState(
        context: ToolExecutionContext,
        browserController: BrowserController,
    ) {
        val tabId = context.activeTabId
        val raw = browserController.evaluateScript(CaptchaDetector.DETECTION_SCRIPT, tabId) ?: return
        val status = CaptchaDetector.parse(raw) ?: return
        applyStatus(context, status)
        if (status.detected) {
            context.extractedData["captcha_detected"] = "true"
            context.extractedData["captcha_provider"] = status.provider.orEmpty()
        }
    }
}