package com.autobrowse.android.agent.tools

import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.CaptchaDetector
import com.autobrowse.android.browser.CaptchaSolverService
import com.autobrowse.android.browser.CaptchaTokenInjector
import com.autobrowse.android.data.repository.AutobrowseRepository
import kotlinx.coroutines.delay

class BrowserDetectCaptchaTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_detect_captcha"
    override val description =
        "Detect CAPTCHA/bot challenges and extract sitekeys (reCAPTCHA, hCaptcha, Turnstile)."
    override val parametersJson = """{"type":"object","properties":{"tab_id":{"type":"string"}}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val status = detectStatus(browserController, tabId)
        if (status == null) {
            return ToolExecutionResult("no_webview", success = false)
        }
        applyStatus(context, status)
        return ToolExecutionResult(status.formatForAgent(), success = true)
    }
}

class BrowserSolveCaptchaTool(
    private val browserController: BrowserController,
    private val repository: AutobrowseRepository,
    private val solver: CaptchaSolverService,
) : AgentTool {
    override val name = "browser_solve_captcha"
    override val description =
        "Solve CAPTCHA on authorized sites via CapSolver or 2Captcha, inject token into page. " +
            "Requires CAPTCHA solver configured in Settings with domain allowlist."
    override val parametersJson = """
        {"type":"object","properties":{"tab_id":{"type":"string"},"captcha_type":{"type":"string","enum":["recaptcha","hcaptcha","turnstile"]}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val config = repository.getCaptchaConfig()
        if (!config.isConfigured()) {
            return ToolExecutionResult(
                "CAPTCHA solver not configured. Enable in Settings + API key + authorized domains allowlist.",
                success = false,
            )
        }
        val pageUrl = browserController.getCurrentUrl(tabId).orEmpty()
        if (!solver.isAuthorized(pageUrl, config)) {
            return ToolExecutionResult(
                "Blocked: $pageUrl not in authorized domain allowlist. Add host in Settings → CAPTCHA Solver.",
                success = false,
            )
        }
        val status = detectStatus(browserController, tabId)
            ?: return ToolExecutionResult("no_webview", success = false)
        if (!status.detected) {
            return ToolExecutionResult("no_captcha_detected", success = true)
        }
        val captchaType = args["captcha_type"]?.toString()
            ?: status.sitekeys.primaryType()
            ?: status.types.firstOrNull { it in setOf("recaptcha", "hcaptcha", "turnstile") }
            ?: "recaptcha"
        val siteKey = when (captchaType) {
            "hcaptcha" -> status.sitekeys.hcaptcha
            "turnstile" -> status.sitekeys.turnstile
            else -> status.sitekeys.recaptcha
        }
        if (siteKey.isNullOrBlank()) {
            return ToolExecutionResult(
                "Could not extract sitekey for $captchaType — try browser_screenshot + browser_detect_captcha again.",
                success = false,
            )
        }
        val solved = solver.solve(
            CaptchaSolverService.SolveRequest(
                pageUrl = pageUrl,
                captchaType = captchaType,
                siteKey = siteKey,
            ),
            config,
        )
        val injectScript = when (captchaType) {
            "hcaptcha" -> CaptchaTokenInjector.injectHcaptcha(solved.token)
            "turnstile" -> CaptchaTokenInjector.injectTurnstile(solved.token)
            else -> CaptchaTokenInjector.injectRecaptchaV2(solved.token)
        }
        val injectResult = browserController.evaluateScript(injectScript, tabId) ?: "inject_failed"
        delay(1500)
        BrowserToolHelper.refreshPageContext(context, browserController)
        val after = detectStatus(browserController, tabId)
        if (after != null) applyStatus(context, after)
        return ToolExecutionResult(
            "solved_via_${solved.provider}\ninject=$injectResult\nURL=${context.pageUrl.orEmpty()}\n" +
                (after?.formatForAgent() ?: ""),
            success = after?.blocking != true,
        )
    }
}

class BrowserWaitForCaptchaClearTool(
    private val browserController: BrowserController,
) : AgentTool {
    override val name = "browser_wait_for_captcha_clear"
    override val description = "Poll until CAPTCHA clears (fallback when automated solver unavailable)."
    override val parametersJson = """
        {"type":"object","properties":{"timeout_ms":{"type":"integer"},"tab_id":{"type":"string"}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val tabId = args["tab_id"]?.toString() ?: context.activeTabId
        val timeout = args["timeout_ms"]?.toString()?.toLongOrNull() ?: 120_000L
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            val status = detectStatus(browserController, tabId)
            if (status == null || !status.blocking) {
                if (status != null) applyStatus(context, status)
                BrowserToolHelper.refreshPageContext(context, browserController)
                return ToolExecutionResult("captcha_cleared — URL: ${context.pageUrl.orEmpty()}", success = true)
            }
            delay(2000)
        }
        return ToolExecutionResult("timeout waiting for captcha clear", success = false)
    }
}

object CaptchaTools {
    fun createAll(
        controller: BrowserController,
        repository: AutobrowseRepository,
        solver: CaptchaSolverService = CaptchaSolverService(),
    ): List<AgentTool> = listOf(
        BrowserDetectCaptchaTool(controller),
        BrowserSolveCaptchaTool(controller, repository, solver),
        BrowserWaitForCaptchaClearTool(controller),
    )

    suspend fun detectStatus(
        browserController: BrowserController,
        tabId: String?,
    ): CaptchaDetector.CaptchaStatus? {
        val raw = browserController.evaluateScript(CaptchaDetector.DETECTION_SCRIPT, tabId) ?: return null
        return CaptchaDetector.parse(raw)
    }

    fun applyStatus(context: ToolExecutionContext, status: CaptchaDetector.CaptchaStatus) {
        context.captchaDetected = status.detected
        context.captchaUserActionRequired = status.blocking
        context.captchaInfo = status.formatForAgent()
        context.captchaBannerMessage = status.userBannerMessage()
    }

    suspend fun refreshCaptchaState(
        context: ToolExecutionContext,
        browserController: BrowserController,
    ) {
        val status = detectStatus(browserController, context.activeTabId) ?: return
        applyStatus(context, status)
        if (status.detected) {
            context.extractedData["captcha_detected"] = "true"
            context.extractedData["captcha_provider"] = status.provider.orEmpty()
        }
    }
}