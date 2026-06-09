package com.autobrowse.android.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autobrowse.android.AutobrowseApplication
import com.autobrowse.android.agent.core.SessionTitleGenerator
import com.autobrowse.android.browser.AddressBarNavigation
import com.autobrowse.android.browser.VirtualDisplayConfig
import com.autobrowse.android.domain.model.AppUiConfig
import com.autobrowse.android.downloads.DownloadItem
import com.autobrowse.android.downloads.DownloadStatus
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.FloatingWindowEngine
import com.autobrowse.android.browser.TabInfo
import com.autobrowse.android.browser.WindowArrangement
import com.autobrowse.android.browser.WindowInfo
import com.autobrowse.android.domain.model.AgentPhase
import com.autobrowse.android.domain.model.AgentProgress
import com.autobrowse.android.domain.model.AutoBrowseRequest
import com.autobrowse.android.domain.model.BrowserContext
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout

import com.autobrowse.android.domain.model.withFrame
import com.autobrowse.android.domain.model.ChatMessage
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.data.local.ModelDownloadProgress
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.LlmProvider
import com.autobrowse.android.domain.model.DeviceContextDefaults
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LocalLlmModel
import com.autobrowse.android.domain.model.CaptchaConfig
import com.autobrowse.android.domain.model.CaptchaSolverProvider
import com.autobrowse.android.domain.model.FeedbackEntry
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.PendingAttachment
import com.autobrowse.android.domain.model.Session
import com.autobrowse.android.domain.model.SessionListItem
import com.autobrowse.android.domain.model.SkillConfig
import com.autobrowse.android.domain.model.SkillType
import com.autobrowse.android.skills.SkillMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class LlmConnectionTestState(
    val isTesting: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean? = null,
)

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val model: LocalLlmModel? = null,
    val progress: ModelDownloadProgress = ModelDownloadProgress(),
    val error: String? = null,
)

data class LocalModelBusyState(
    val isBusy: Boolean = false,
    val message: String? = null,
)

data class SkillTransferState(
    val message: String? = null,
    val isSuccess: Boolean? = null,
)

data class FeedbackTransferState(
    val message: String? = null,
    val isSuccess: Boolean? = null,
)

data class MainUiState(
    val session: Session? = null,
    val sessions: List<Session> = emptyList(),
    val showSessionsPanel: Boolean = false,
    val tabs: List<BrowserTab> = emptyList(),
    val windowFrames: Map<String, BrowserWindowFrame> = emptyMap(),
    val activeTabId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val memory: List<MemoryEntry> = emptyList(),
    val llmConfig: LlmConfig = LlmConfig(),
    val skillConfigs: List<SkillConfig> = emptyList(),
    val enabledSkills: Set<SkillType> = emptySet(),
    val chatInput: String = "",
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isAgentThinking: Boolean = false,
    val agentProgress: AgentProgress? = null,
    val strategies: List<LearnedStrategy> = emptyList(),
    val agentSkills: List<SkillMetadata> = emptyList(),
    val showSettings: Boolean = false,
    val showLlmSetup: Boolean = false,
    val llmSetupFromSettings: Boolean = false,
    val llmConnectionTest: LlmConnectionTestState = LlmConnectionTestState(),
    val modelDownload: ModelDownloadState = ModelDownloadState(),
    val localModelBusy: LocalModelBusyState = LocalModelBusyState(),
    val downloadedLocalModels: Set<LocalLlmModel> = emptySet(),
    val skillTransfer: SkillTransferState = SkillTransferState(),
    val feedbackEntries: List<FeedbackEntry> = emptyList(),
    val feedbackTransfer: FeedbackTransferState = FeedbackTransferState(),
    val captchaConfig: CaptchaConfig = CaptchaConfig(),
    val appUiConfig: AppUiConfig = AppUiConfig(),
    val showDownloadsPanel: Boolean = false,
    val downloads: List<DownloadItem> = emptyList(),
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutobrowseApplication
    private val repository = app.repository
    private val agentLoop = app.agentLoop
    private val attachmentStore = app.attachmentStore
    private val attachmentProcessor = app.attachmentProcessor
    private val llmApi = app.llmApi
    val browserController: BrowserController = app.browserController

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var sessionId: String? = null
    private val activeSessionId = MutableStateFlow<String?>(null)
    private val persistJobs = mutableMapOf<String, Job>()
    private var agentJob: Job? = null
    private var modelDownloadJob: Job? = null

    init {
        wireTabManager()
        wireWindowManager()
        viewModelScope.launch {
            val session = repository.createNewSession()
            val llmConfig = repository.getLlmConfig()
            val appUiConfig = repository.getAppUiConfig()
            VirtualDisplayConfig.resolutionScale = appUiConfig.coercedScale()
            _uiState.update {
                it.copy(
                    session = session,
                    llmConfig = llmConfig,
                    appUiConfig = appUiConfig,
                    skillConfigs = app.skillRegistry.allSkillConfigs(),
                    enabledSkills = repository.getEnabledSkills(),
                    showLlmSetup = !llmConfig.isConfigured(),
                    downloadedLocalModels = app.modelFileManager.downloadedModels(),
                    error = setupBannerMessage(llmConfig),
                )
            }
            activeSessionId.value = session.id
            refreshAgentSkills()
            if (llmConfig.isConfigured()) {
                llmApi.warmUpLocalModel(llmConfig)
            }
        }

        viewModelScope.launch {
            repository.observeSessions().collect { sessions ->
                _uiState.update { state ->
                    val refreshedSession = state.session?.let { current ->
                        sessions.find { it.id == current.id } ?: current
                    }
                    state.copy(sessions = sessions, session = refreshedSession)
                }
            }
        }

        viewModelScope.launch {
            activeSessionId.filterNotNull().collectLatest { id ->
                sessionId = id
                resetSessionUi()
                coroutineScope {
                    launch {
                        repository.observeTabs(id).collect { dbTabs ->
                            _uiState.update { state ->
                                val merged = FloatingWindowEngine.mergeDbTabs(
                                    dbTabs = dbTabs,
                                    currentTabs = state.tabs,
                                    currentFrames = state.windowFrames,
                                )
                                state.copy(
                                    tabs = merged.tabs,
                                    windowFrames = merged.frames,
                                    activeTabId = state.activeTabId?.takeIf { tabId ->
                                        merged.tabs.any { it.id == tabId }
                                    } ?: merged.tabs.maxByOrNull { it.zIndex }?.id,
                                )
                            }
                        }
                    }
                    launch {
                        repository.observeChat(id).collect { messages ->
                            _uiState.update { it.copy(messages = messages) }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.observeMemory().collect { memory ->
                _uiState.update { it.copy(memory = memory) }
            }
        }
        viewModelScope.launch {
            repository.observeStrategies().collect { strategies ->
                _uiState.update { it.copy(strategies = strategies) }
            }
        }
        viewModelScope.launch {
            repository.observeFeedback().collect { feedback ->
                _uiState.update { it.copy(feedbackEntries = feedback) }
            }
        }
        viewModelScope.launch {
            agentLoop.progress.collect { progress ->
                _uiState.update {
                    it.copy(
                        agentProgress = progress,
                        isAgentThinking = progress.phase != AgentPhase.IDLE,
                    )
                }
            }
        }

        viewModelScope.launch {
            app.downloadsManager.items.collect { downloads ->
                _uiState.update { it.copy(downloads = downloads) }
            }
        }
    }

    private fun resetSessionUi() {
        _uiState.update { state ->
            state.copy(
                tabs = emptyList(),
                windowFrames = emptyMap(),
                activeTabId = null,
                messages = emptyList(),
                chatInput = "",
                pendingAttachments = emptyList(),
            )
        }
    }

    fun toggleSessionsPanel() {
        _uiState.update { it.copy(showSessionsPanel = !it.showSessionsPanel) }
    }

    fun closeSessionsPanel() {
        _uiState.update { it.copy(showSessionsPanel = false, showDownloadsPanel = false) }
    }

    fun toggleDownloadsPanel() {
        val opening = !_uiState.value.showDownloadsPanel
        _uiState.update {
            it.copy(
                showDownloadsPanel = opening,
                showSessionsPanel = if (opening) true else it.showSessionsPanel,
            )
        }
        if (opening) refreshDownloads()
    }

    fun closeDownloadsPanel() {
        _uiState.update { it.copy(showDownloadsPanel = false) }
    }

    fun refreshDownloads() {
        viewModelScope.launch { app.downloadsManager.refresh() }
    }

    fun openDownload(item: DownloadItem) {
        val path = item.path ?: return
        if (item.status != DownloadStatus.COMPLETED) return
        viewModelScope.launch {
            runCatching { openLocalFile(path, item.mimeType) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        app.downloadsManager.cancel(item.id)
    }

    fun deleteDownload(item: DownloadItem) {
        app.downloadsManager.delete(item)
    }

    suspend fun searchSessions(query: String): List<SessionListItem> =
        repository.searchSessions(query)

    fun createNewSession() {
        viewModelScope.launch {
            val session = repository.createNewSession()
            _uiState.update { it.copy(session = session, showSessionsPanel = false) }
            activeSessionId.value = session.id
        }
    }

    fun switchSession(targetSessionId: String) {
        if (targetSessionId == _uiState.value.session?.id) {
            closeSessionsPanel()
            return
        }
        viewModelScope.launch {
            val session = repository.activateSession(targetSessionId) ?: return@launch
            _uiState.update { it.copy(session = session, showSessionsPanel = false) }
            activeSessionId.value = session.id
        }
    }

    fun pinSession(sessionId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.setSessionPinned(sessionId, pinned)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val replacement = repository.deleteSession(sessionId)
            if (replacement != null) {
                _uiState.update {
                    it.copy(
                        session = replacement,
                        showSessionsPanel = false,
                        error = null,
                    )
                }
                activeSessionId.value = replacement.id
            } else if (_uiState.value.sessions.size <= 1) {
                _uiState.update {
                    it.copy(error = "Keep at least one session.")
                }
            }
        }
    }

    fun updateChatInput(value: String) {
        _uiState.update { it.copy(chatInput = value) }
    }

    fun addAttachment(attachment: PendingAttachment) {
        _uiState.update { state ->
            if (state.pendingAttachments.size >= 5) state
            else state.copy(pendingAttachments = state.pendingAttachments + attachment)
        }
    }

    fun removeAttachment(id: String) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filter { it.id != id })
        }
    }

    fun toggleSettings(show: Boolean) {
        if (show) {
            refreshAgentSkills()
            refreshCaptchaConfig()
        }
        _uiState.update { it.copy(showSettings = show) }
    }

    fun updateCaptchaConfig(config: CaptchaConfig) {
        _uiState.update { it.copy(captchaConfig = config) }
    }

    fun saveCaptchaConfig() {
        viewModelScope.launch {
            repository.saveCaptchaConfig(_uiState.value.captchaConfig)
        }
    }

    private fun refreshCaptchaConfig() {
        viewModelScope.launch {
            val config = repository.getCaptchaConfig()
            _uiState.update { it.copy(captchaConfig = config) }
        }
    }

    private fun refreshAgentSkills() {
        viewModelScope.launch {
            val skills = app.skillStore.listSkills()
            _uiState.update { it.copy(agentSkills = skills) }
        }
    }

    fun selectTab(tabId: String) {
        browserController.setActiveTab(tabId)
        _uiState.update { state ->
            val maxZ = state.tabs.maxOfOrNull { it.zIndex } ?: 0
            val tabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(zIndex = maxZ + 1) else tab
            }
            state.copy(tabs = tabs, activeTabId = tabId)
        }
    }

    fun commitWindowGeometry(tabId: String, layout: BrowserWindowLayout, persist: Boolean = true) {
        _uiState.update { state ->
            val fallbackTab = state.tabs.find { it.id == tabId }
            val frames = FloatingWindowEngine.reconcileFrames(
                state.tabs,
                FloatingWindowEngine.commitLayout(
                    frames = state.windowFrames,
                    tabId = tabId,
                    layout = layout,
                    fallbackTab = fallbackTab,
                ),
            )
            state.copy(
                windowFrames = frames,
                tabs = applyFramesToTabs(state.tabs, frames),
            )
        }
        if (persist) schedulePersist(tabId)
    }

    fun moveWindow(tabId: String, layout: BrowserWindowLayout) {
        commitWindowGeometry(tabId, layout)
    }

    fun resizeWindow(tabId: String, layout: BrowserWindowLayout) {
        commitWindowGeometry(tabId, layout)
    }

    fun arrangeWindowsForAgent(
        focusTabId: String,
        tabIds: List<String>? = null,
        persist: Boolean = true,
    ): List<WindowInfo> {
        val ids = (tabIds ?: _uiState.value.tabs.map { it.id })
            .filter { id -> _uiState.value.tabs.any { it.id == id } }
        if (ids.isEmpty()) return emptyList()

        val layouts = WindowArrangement.arrange(ids, focusTabId)
        selectTab(focusTabId)

        _uiState.update { state ->
            val maxZ = state.tabs.maxOfOrNull { it.zIndex } ?: 0
            val tabs = state.tabs.map { tab ->
                when (tab.id) {
                    focusTabId -> tab.copy(zIndex = maxZ + 1)
                    in ids -> tab.copy(zIndex = ids.indexOf(tab.id).coerceAtLeast(0))
                    else -> tab
                }
            }
            state.copy(tabs = tabs)
        }

        layouts.forEach { (tabId, layout) ->
            commitWindowGeometry(tabId, layout, persist = false)
        }
        if (persist) {
            ids.forEach { schedulePersist(it) }
        }
        return listWindowsForAgent()
    }

    fun resizeWindowForAgent(
        tabId: String,
        widthFraction: Float,
        offsetX: Float? = null,
        offsetY: Float? = null,
    ): WindowInfo {
        val current = _uiState.value.windowFrames[tabId]?.layout
            ?: _uiState.value.tabs.find { it.id == tabId }?.layout
        val layout = WindowArrangement.resize(widthFraction, offsetX, offsetY, current)
        commitWindowGeometry(tabId, layout)
        return windowInfoForTab(
            _uiState.value.tabs.find { it.id == tabId }
                ?: throw IllegalArgumentException("Tab not found: $tabId"),
            isActive = _uiState.value.activeTabId == tabId,
        )
    }

    fun updateResolutionScale(scale: Float) {
        viewModelScope.launch {
            val updated = _uiState.value.appUiConfig.copy(resolutionScale = scale)
            repository.saveAppUiConfig(updated)
            VirtualDisplayConfig.resolutionScale = updated.coercedScale()
            _uiState.update { it.copy(appUiConfig = updated) }
        }
    }

    fun updateMaxAgentIterations(iterations: Float) {
        viewModelScope.launch {
            val updated = _uiState.value.appUiConfig.copy(maxAgentIterations = iterations.toInt())
            repository.saveAppUiConfig(updated)
            _uiState.update { it.copy(appUiConfig = updated) }
        }
    }

    fun captureTabScreenshot(tabId: String) {
        viewModelScope.launch {
            browserController.setActiveTab(tabId)
            delay(100)
            val b64 = browserController.captureScreenshotBase64(tabId) ?: run {
                _uiState.update {
                    it.copy(error = "Screenshot failed — wait for the page to finish loading, then try again")
                }
                return@launch
            }
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val dir = java.io.File(getApplication<Application>().filesDir, "screenshots").apply { mkdirs() }
            val tab = _uiState.value.tabs.find { it.id == tabId }
            val safe = tab?.title?.replace(Regex("""[^\w.-]"""), "_")?.take(24) ?: "window"
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = java.io.File(dir, "shot_${safe}_$stamp.jpg")
            file.writeBytes(bytes)
            app.downloadsManager.registerScreenshot(file.absolutePath, tabId)
        }
    }

    fun refreshTab(tabId: String) {
        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return
        browserController.loadUrl(tab.url, tabId)
        bringTabToFront(tabId)
    }

    private fun bringTabToFront(tabId: String) {
        browserController.setActiveTab(tabId)
        _uiState.update { state ->
            val maxZ = state.tabs.maxOfOrNull { it.zIndex } ?: 0
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(zIndex = maxZ + 1) else tab
                },
                activeTabId = tabId,
            )
        }
    }

    private fun applyFramesToTabs(
        tabs: List<BrowserTab>,
        frames: Map<String, BrowserWindowFrame>,
    ): List<BrowserTab> = tabs.map { tab ->
        frames[tab.id]?.let { tab.withFrame(it) } ?: tab
    }

    private fun tabForPersist(tabId: String): BrowserTab? {
        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return null
        val frame = _uiState.value.windowFrames[tabId] ?: return tab
        return tab.withFrame(frame)
    }

    private fun schedulePersist(tabId: String) {
        persistJobs[tabId]?.cancel()
        persistJobs[tabId] = viewModelScope.launch {
            delay(400)
            persistWindowNow(tabId)
            persistJobs.remove(tabId)
        }
    }

    private fun persistWindowNow(tabId: String) {
        val session = sessionId ?: return
        val tab = tabForPersist(tabId) ?: return
        viewModelScope.launch {
            repository.saveTab(tab, session)
        }
    }

    fun closeTab(tabId: String) {
        val session = sessionId ?: return
        viewModelScope.launch {
            repository.deleteTab(tabId)
            _uiState.update { state ->
                val remaining = state.tabs.filter { it.id != tabId }
                val frames = FloatingWindowEngine.removeFrame(state.windowFrames, tabId)
                val nextActive = if (state.activeTabId == tabId) {
                    remaining.maxByOrNull { it.zIndex }?.id
                } else {
                    state.activeTabId
                }
                nextActive?.let { browserController.setActiveTab(it) }
                state.copy(tabs = remaining, windowFrames = frames, activeTabId = nextActive)
            }
        }
    }

    fun toggleMaximizeTab(tabId: String) {
        _uiState.update { state ->
            val fallbackTab = state.tabs.find { it.id == tabId }
            val frames = FloatingWindowEngine.reconcileFrames(
                state.tabs,
                FloatingWindowEngine.toggleMaximize(
                    frames = state.windowFrames,
                    tabId = tabId,
                    fallbackTab = fallbackTab,
                ),
            )
            state.copy(
                windowFrames = frames,
                tabs = applyFramesToTabs(state.tabs, frames),
                activeTabId = tabId,
            )
        }
        browserController.setActiveTab(tabId)
        persistWindowNow(tabId)
    }

    fun minimizeTab(tabId: String) {
        _uiState.update { state ->
            val fallbackTab = state.tabs.find { it.id == tabId }
            val frames = FloatingWindowEngine.reconcileFrames(
                state.tabs,
                FloatingWindowEngine.minimize(
                    frames = state.windowFrames,
                    tabId = tabId,
                    fallbackTab = fallbackTab,
                ),
            )
            state.copy(
                windowFrames = frames,
                tabs = applyFramesToTabs(state.tabs, frames),
                activeTabId = tabId,
            )
        }
        browserController.setActiveTab(tabId)
        persistWindowNow(tabId)
    }

    fun addTab(url: String = "https://www.google.com") {
        val id = sessionId ?: return
        viewModelScope.launch {
            val index = _uiState.value.tabs.size
            val maxZ = _uiState.value.tabs.maxOfOrNull { it.zIndex } ?: 0
            val tab = BrowserTab(
                id = UUID.randomUUID().toString(),
                url = url,
                title = "New Tab",
                status = BrowserTabStatus.IDLE,
                zIndex = maxZ + 1,
                layout = BrowserWindowLayout.defaultForIndex(index),
                desktopMode = true,
            )
            _uiState.update { state ->
                val frames = FloatingWindowEngine.addFrameForTab(state.windowFrames, tab)
                state.copy(
                    activeTabId = tab.id,
                    windowFrames = frames,
                )
            }
            browserController.setActiveTab(tab.id)
            val frame = _uiState.value.windowFrames[tab.id] ?: BrowserWindowFrame.fromTab(tab)
            repository.saveTab(tab.withFrame(frame), id)
        }
    }

    fun updateTabMetadata(
        tabId: String,
        url: String? = null,
        title: String? = null,
        status: BrowserTabStatus? = null,
    ) {
        val id = sessionId ?: return
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = url ?: tab.url,
                            title = title ?: tab.title,
                            status = status ?: tab.status,
                        )
                    } else {
                        tab
                    }
                },
            )
        }
        viewModelScope.launch {
            val tab = _uiState.value.tabs.find { it.id == tabId } ?: return@launch
            val frame = _uiState.value.windowFrames[tabId]
            val toSave = frame?.let { tab.withFrame(it) } ?: tab
            repository.saveTab(
                toSave.copy(
                    url = url ?: toSave.url,
                    title = title ?: toSave.title,
                    status = status ?: toSave.status,
                ),
                id,
            )
        }
    }

    fun stopAgent() {
        agentLoop.requestCancel()
        llmApi.cancelLocalGeneration()
        agentJob?.cancel()
        app.taskOrchestrator.cancelAll()
        _uiState.update {
            it.copy(
                isAgentThinking = false,
                agentProgress = AgentProgress(AgentPhase.IDLE, 0, 20, message = "Stopped"),
            )
        }
    }

    private fun wireTabManager() {
        app.tabManager.openTabHandler = { url -> openTabForAgent(url) }
        app.tabManager.closeTabHandler = { tabId -> closeTabForAgent(tabId) }
        app.tabManager.switchTabHandler = { tabId -> switchTabForAgent(tabId) }
        app.tabManager.listTabsHandler = { listTabsForAgent() }
        app.tabManager.activeTabIdHandler = { _uiState.value.activeTabId }
    }

    private fun wireWindowManager() {
        app.windowManager.arrangeHandler = { focusTabId, tabIds ->
            withContext(Dispatchers.Main) {
                arrangeWindowsForAgent(focusTabId, tabIds)
            }
        }
        app.windowManager.resizeHandler = { tabId, width, offsetX, offsetY ->
            withContext(Dispatchers.Main) {
                resizeWindowForAgent(tabId, width, offsetX, offsetY)
            }
        }
        app.windowManager.focusHandler = { tabId ->
            withContext(Dispatchers.Main) {
                selectTab(tabId)
                val tab = _uiState.value.tabs.find { it.id == tabId }
                    ?: throw IllegalArgumentException("Tab not found: $tabId")
                windowInfoForTab(tab, isActive = true)
            }
        }
        app.windowManager.listHandler = { listWindowsForAgent() }
    }

    private suspend fun openTabForAgent(url: String?): TabInfo = withContext(Dispatchers.Main) {
        val session = sessionId ?: throw IllegalStateException("No active session") // used for saveTab
        val index = _uiState.value.tabs.size
        val maxZ = _uiState.value.tabs.maxOfOrNull { it.zIndex } ?: 0
        val tab = BrowserTab(
            id = UUID.randomUUID().toString(),
            url = url ?: "https://www.google.com",
            title = "Agent Tab",
            status = BrowserTabStatus.IDLE,
            zIndex = maxZ + 1,
            layout = BrowserWindowLayout.defaultForIndex(index),
            desktopMode = true,
        )
        val frame = BrowserWindowFrame.fromTab(tab)
        _uiState.update { state ->
            val frames = FloatingWindowEngine.addFrameForTab(state.windowFrames, tab)
            val tabs = if (state.tabs.any { it.id == tab.id }) {
                state.tabs
            } else {
                state.tabs + tab.withFrame(frame)
            }
            state.copy(activeTabId = tab.id, windowFrames = frames, tabs = tabs)
        }
        browserController.setActiveTab(tab.id)
        url?.let { browserController.loadUrl(it, tab.id) }
        val savedFrame = _uiState.value.windowFrames[tab.id] ?: frame
        repository.saveTab(tab.withFrame(savedFrame), session)
        val tabIds = _uiState.value.tabs.map { it.id }
        if (tabIds.size >= 2) {
            arrangeWindowsForAgent(focusTabId = tab.id, tabIds = tabIds)
        }
        tabToInfo(tab, isActive = true)
    }

    private suspend fun closeTabForAgent(tabId: String?): TabInfo? = withContext(Dispatchers.Main) {
        if (sessionId == null) return@withContext null
        val targetId = tabId ?: _uiState.value.activeTabId ?: return@withContext null
        val closing = _uiState.value.tabs.find { it.id == targetId } ?: return@withContext null
        repository.deleteTab(targetId)
        _uiState.update { state ->
            val remaining = state.tabs.filter { it.id != targetId }
            val frames = FloatingWindowEngine.removeFrame(state.windowFrames, targetId)
            val nextActive = if (state.activeTabId == targetId) {
                remaining.maxByOrNull { it.zIndex }?.id
            } else {
                state.activeTabId
            }
            nextActive?.let { browserController.setActiveTab(it) }
            state.copy(tabs = remaining, windowFrames = frames, activeTabId = nextActive)
        }
        tabToInfo(closing, isActive = false)
    }

    private suspend fun switchTabForAgent(tabId: String): TabInfo = withContext(Dispatchers.Main) {
        selectTab(tabId)
        val tab = _uiState.value.tabs.find { it.id == tabId }
            ?: throw IllegalArgumentException("Tab not found: $tabId")
        tabToInfo(tab, isActive = true)
    }

    private fun listTabsForAgent(): List<TabInfo> {
        val active = _uiState.value.activeTabId
        return _uiState.value.tabs.map { tabToInfo(it, isActive = it.id == active) }
    }

    private fun tabToInfo(tab: BrowserTab, isActive: Boolean) = TabInfo(
        id = tab.id,
        url = tab.url,
        title = tab.title,
        isActive = isActive,
    )

    private fun listWindowsForAgent(): List<WindowInfo> {
        val active = _uiState.value.activeTabId
        return _uiState.value.tabs.map { tab ->
            windowInfoForTab(tab, isActive = tab.id == active)
        }
    }

    private fun windowInfoForTab(tab: BrowserTab, isActive: Boolean): WindowInfo {
        val frame = _uiState.value.windowFrames[tab.id] ?: BrowserWindowFrame.fromTab(tab)
        return WindowInfo(
            tabId = tab.id,
            title = tab.title,
            url = tab.url,
            isActive = isActive,
            zIndex = tab.zIndex,
            windowState = frame.windowState,
            layout = frame.effectiveLayout(),
        )
    }

    fun sendMessage() {
        val prompt = _uiState.value.chatInput.trim()
        val attachments = _uiState.value.pendingAttachments
        val id = sessionId ?: return
        if ((prompt.isBlank() && attachments.isEmpty()) || _uiState.value.isAgentThinking) return

        agentJob?.cancel()
        llmApi.cancelLocalGeneration()
        agentJob = viewModelScope.launch {
            val llmConfig = repository.getLlmConfig()
            if (!llmConfig.isConfigured()) {
                _uiState.update {
                    it.copy(
                        error = setupBannerMessage(llmConfig),
                        showLlmSetup = true,
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    chatInput = "",
                    pendingAttachments = emptyList(),
                    isAgentThinking = true,
                    agentProgress = AgentProgress(
                        phase = AgentPhase.THINKING,
                        iteration = 0,
                        maxIterations = _uiState.value.appUiConfig.coercedMaxIterations(),
                        message = if (llmConfig.provider == LlmProvider.LOCAL) {
                            "Local model (experimental — may take several minutes)…"
                        } else {
                            "Preparing…"
                        },
                    ),
                    error = null,
                )
            }
            try {
                val prepareModel = async {
                    if (llmConfig.provider == LlmProvider.LOCAL) {
                        llmApi.prepareLocalEngine(llmConfig)
                    }
                }
                val stored = attachments.map { pending ->
                    attachmentStore.persist(getApplication(), pending)
                }
                val payload = attachmentProcessor.processAll(stored)
                prepareModel.await()

                val pageUrl = browserController.getCurrentUrl()
                val pageHtml = if (llmConfig.provider == LlmProvider.LOCAL) null else browserController.getPageHtml()
                val pageText = if (llmConfig.provider == LlmProvider.LOCAL) null else browserController.getPageText()

                val result = app.taskOrchestrator.runSingle(
                    request = AutoBrowseRequest(
                        prompt = prompt,
                        sessionId = id,
                        attachmentPayload = payload,
                    ),
                    browserContext = BrowserContext(
                        pageUrl = pageUrl,
                        pageHtml = pageHtml,
                        pageText = pageText,
                    ),
                )

                if (result.actions.isNotEmpty()) {
                    browserController.executeActions(result.actions)
                }
            } catch (e: CancellationException) {
                // AgentLoop records the stopped state and message.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                agentJob = null
                _uiState.update { it.copy(isAgentThinking = false) }
                refreshAgentSkills()
                if (prompt.isNotBlank()) {
                    launch {
                        runCatching {
                            val config = repository.getLlmConfig()
                            val title = SessionTitleGenerator.generateTitle(llmApi, config, prompt)
                            repository.renameSession(id, title)
                        }
                    }
                }
            }
        }
    }

    fun saveLlmConfig(config: LlmConfig) {
        viewModelScope.launch {
            if (config.provider == LlmProvider.LOCAL && config.localModelPath.isNotBlank()) {
                setLocalModelBusy(true, "Loading model…")
                try {
                    repository.saveLlmConfig(config)
                    llmApi.warmUpLocalModel(config)
                } finally {
                    setLocalModelBusy(false)
                }
            } else {
                repository.saveLlmConfig(config)
            }
            val fromSettings = _uiState.value.llmSetupFromSettings
            _uiState.update {
                it.copy(
                    llmConfig = config,
                    showLlmSetup = !config.isConfigured(),
                    showSettings = fromSettings && config.isConfigured(),
                    llmSetupFromSettings = false,
                    llmConnectionTest = LlmConnectionTestState(),
                    downloadedLocalModels = app.modelFileManager.downloadedModels(),
                    error = setupBannerMessage(config),
                )
            }
        }
    }

    fun downloadLocalModel(model: LocalLlmModel) {
        modelDownloadJob?.cancel()
        modelDownloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    modelDownload = ModelDownloadState(
                        isDownloading = true,
                        model = model,
                        progress = ModelDownloadProgress(message = "Connecting…"),
                    ),
                )
            }
            try {
                val modelPath = app.modelFileManager.downloadModel(model) { progress ->
                    _uiState.update { state ->
                        state.copy(
                            modelDownload = state.modelDownload.copy(
                                isDownloading = true,
                                model = model,
                                progress = progress,
                            ),
                        )
                    }
                }
                val updatedConfig = _uiState.value.llmConfig.copy(
                    provider = LlmProvider.LOCAL,
                    localModel = model,
                    localModelPath = modelPath,
                    maxTokens = DeviceContextDefaults.defaultContextTokens(getApplication(), model),
                )
                repository.saveLlmConfig(updatedConfig)
                _uiState.update {
                    it.copy(
                        llmConfig = updatedConfig,
                        modelDownload = ModelDownloadState(
                            isDownloading = false,
                            model = model,
                            progress = ModelDownloadProgress(
                                percent = 1f,
                                message = "Saved to phone storage.",
                            ),
                        ),
                        downloadedLocalModels = app.modelFileManager.downloadedModels(),
                        llmConnectionTest = LlmConnectionTestState(
                            message = "Downloaded ${modelPath.substringAfterLast('/')}",
                            isSuccess = true,
                        ),
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        modelDownload = ModelDownloadState(
                            isDownloading = false,
                            model = model,
                            error = e.message ?: "Download failed.",
                        ),
                    )
                }
            } finally {
                modelDownloadJob = null
            }
        }
    }

    fun cancelModelDownload() {
        modelDownloadJob?.cancel()
        modelDownloadJob = null
        _uiState.update {
            it.copy(modelDownload = ModelDownloadState(model = it.modelDownload.model))
        }
    }

    fun clearSkillTransferMessage() {
        _uiState.update { it.copy(skillTransfer = SkillTransferState()) }
    }

    fun showSkillTransfer(message: String, isSuccess: Boolean) {
        _uiState.update {
            it.copy(skillTransfer = SkillTransferState(message = message, isSuccess = isSuccess))
        }
    }

    suspend fun buildLearnedSkillsExport(): Pair<String, Int> = withContext(Dispatchers.IO) {
        val bundle = app.skillStore.exportLearnedSkillsBundle()
        val json = com.autobrowse.android.skills.LearnedSkillsSerializer.toJson(bundle)
        json to bundle.skills.size
    }

    fun learnedSkillsExportFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "autobrowse-learned-skills-$stamp.json"
    }

    suspend fun createLearnedSkillsShareUri(json: String): Uri = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val exportDir = java.io.File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = java.io.File(exportDir, learnedSkillsExportFileName())
        file.writeText(json)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun buildLearnedSkillsShareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Multiwindow Autobrowser learned skills")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun saveLearnedSkillsExport(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = app.skillStore.exportLearnedSkillsJson()
                val count = com.autobrowse.android.skills.LearnedSkillsSerializer
                    .fromJson(json).skills.size
                if (count == 0) {
                    _uiState.update {
                        it.copy(
                            skillTransfer = SkillTransferState(
                                message = "No learned skills to export yet. Run browsing tasks first.",
                                isSuccess = false,
                            ),
                        )
                    }
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not write export file.")
                }
                _uiState.update {
                    it.copy(
                        skillTransfer = SkillTransferState(
                            message = "Exported $count learned skill${if (count == 1) "" else "s"}.",
                            isSuccess = true,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        skillTransfer = SkillTransferState(
                            message = e.message ?: "Export failed.",
                            isSuccess = false,
                        ),
                    )
                }
            }
        }
    }

    fun upvoteFeedback(id: String) {
        viewModelScope.launch {
            app.feedbackManager.upvote(id)
        }
    }

    fun downvoteFeedback(id: String) {
        viewModelScope.launch {
            app.feedbackManager.downvote(id)
        }
    }

    fun deleteFeedback(id: String) {
        viewModelScope.launch {
            app.feedbackManager.delete(id)
        }
    }

    fun clearFeedbackTransferMessage() {
        _uiState.update { it.copy(feedbackTransfer = FeedbackTransferState()) }
    }

    fun showFeedbackTransfer(message: String, isSuccess: Boolean) {
        _uiState.update {
            it.copy(feedbackTransfer = FeedbackTransferState(message = message, isSuccess = isSuccess))
        }
    }

    suspend fun buildFeedbackExport(): Pair<String, Int> = withContext(Dispatchers.IO) {
        val json = app.feedbackManager.exportJson()
        val count = com.autobrowse.android.feedback.FeedbackSerializer.fromJson(json).entries.size
        json to count
    }

    fun feedbackExportFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "autobrowse-feedback-$stamp.json"
    }

    suspend fun createFeedbackShareUri(json: String): Uri = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val exportDir = java.io.File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = java.io.File(exportDir, feedbackExportFileName())
        file.writeText(json)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun buildFeedbackShareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Multiwindow Autobrowser training feedback")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun saveFeedbackExport(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = app.feedbackManager.exportJson()
                val count = com.autobrowse.android.feedback.FeedbackSerializer.fromJson(json).entries.size
                if (count == 0) {
                    _uiState.update {
                        it.copy(
                            feedbackTransfer = FeedbackTransferState(
                                message = "No feedback to export yet. Coach the agent in chat first.",
                                isSuccess = false,
                            ),
                        )
                    }
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not write export file.")
                }
                _uiState.update {
                    it.copy(
                        feedbackTransfer = FeedbackTransferState(
                            message = "Exported $count feedback entr${if (count == 1) "y" else "ies"} (priority order).",
                            isSuccess = true,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        feedbackTransfer = FeedbackTransferState(
                            message = e.message ?: "Export failed.",
                            isSuccess = false,
                        ),
                    )
                }
            }
        }
    }

    fun importFeedback(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: throw IllegalStateException("Could not read import file.")
                }
                val imported = app.feedbackManager.importJson(json, merge = true)
                _uiState.update {
                    it.copy(
                        feedbackTransfer = FeedbackTransferState(
                            message = if (imported == 0) {
                                "No feedback entries imported."
                            } else {
                                "Imported $imported feedback entr${if (imported == 1) "y" else "ies"}."
                            },
                            isSuccess = imported > 0,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        feedbackTransfer = FeedbackTransferState(
                            message = e.message ?: "Import failed.",
                            isSuccess = false,
                        ),
                    )
                }
            }
        }
    }

    fun importLearnedSkills(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: throw IllegalStateException("Could not read import file.")
                }
                val imported = app.skillStore.importLearnedSkillsJson(json, merge = true)
                refreshAgentSkills()
                _uiState.update {
                    it.copy(
                        skillTransfer = SkillTransferState(
                            message = if (imported == 0) {
                                "No new skills imported."
                            } else {
                                "Imported $imported learned skill${if (imported == 1) "" else "s"}."
                            },
                            isSuccess = imported > 0,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        skillTransfer = SkillTransferState(
                            message = e.message ?: "Import failed.",
                            isSuccess = false,
                        ),
                    )
                }
            }
        }
    }

    fun importLocalModel(uri: Uri, model: LocalLlmModel) {
        viewModelScope.launch {
            setLocalModelBusy(true, "Importing model…")
            try {
                val modelPath = app.modelFileManager.importModel(uri, model)
                val updatedConfig = _uiState.value.llmConfig.copy(
                    provider = LlmProvider.LOCAL,
                    localModel = model,
                    localModelPath = modelPath,
                    maxTokens = DeviceContextDefaults.defaultContextTokens(getApplication(), model),
                )
                setLocalModelBusy(true, "Loading model…")
                llmApi.prepareLocalEngine(updatedConfig)
                repository.saveLlmConfig(updatedConfig)
                _uiState.update {
                    it.copy(
                        llmConfig = updatedConfig,
                        downloadedLocalModels = app.modelFileManager.downloadedModels(),
                        llmConnectionTest = LlmConnectionTestState(
                            message = "Ready · ${modelPath.substringAfterLast('/')}",
                            isSuccess = true,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        llmConnectionTest = LlmConnectionTestState(
                            isTesting = false,
                            message = e.message ?: "Import failed.",
                            isSuccess = false,
                        ),
                    )
                }
            } finally {
                setLocalModelBusy(false)
            }
        }
    }

    fun deleteLocalModel(model: LocalLlmModel) {
        viewModelScope.launch {
            app.modelFileManager.deleteModel(model)
            val current = _uiState.value.llmConfig
            val clearedPath = if (
                current.localModel == model &&
                current.localModelPath.isNotBlank()
            ) {
                app.localLlmService.close()
                ""
            } else {
                current.localModelPath
            }
            val updated = current.copy(localModelPath = clearedPath)
            repository.saveLlmConfig(updated)
            _uiState.update {
                it.copy(
                    llmConfig = updated,
                    downloadedLocalModels = app.modelFileManager.downloadedModels(),
                    llmConnectionTest = LlmConnectionTestState(
                        message = "Deleted ${LocalLlmCatalog.infoFor(model).displayName}",
                        isSuccess = true,
                    ),
                    error = if (updated.isConfigured()) null else setupBannerMessage(updated),
                    showLlmSetup = !updated.isConfigured(),
                )
            }
        }
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    private fun openLocalFile(path: String, mimeType: String) {
        val context = getApplication<Application>()
        val file = java.io.File(path)
        if (!file.exists()) error("File not found")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open file"))
    }

    private fun setupBannerMessage(config: LlmConfig): String? = when {
        config.isConfigured() -> null
        config.provider == LlmProvider.LOCAL ->
            "Download a LiteRT Gemma model on the setup screen to use on-device inference."
        else -> "Add your cloud API token on the setup screen to use the agent (recommended)."
    }

    fun openLlmSetup(fromSettings: Boolean = false) {
        _uiState.update {
            it.copy(
                showLlmSetup = true,
                showSettings = false,
                llmSetupFromSettings = fromSettings,
                llmConnectionTest = LlmConnectionTestState(),
            )
        }
    }

    fun closeLlmSetup() {
        _uiState.update {
            it.copy(
                showLlmSetup = false,
                showSettings = it.llmSetupFromSettings,
                llmSetupFromSettings = false,
                llmConnectionTest = LlmConnectionTestState(),
            )
        }
    }

    fun testLlmConnection(config: LlmConfig) {
        viewModelScope.launch {
            val testingMessage = if (config.provider == LlmProvider.LOCAL) {
                "Loading model…"
            } else {
                "Testing connection…"
            }
            if (config.provider == LlmProvider.LOCAL) {
                setLocalModelBusy(true, testingMessage)
            }
            _uiState.update {
                it.copy(
                    llmConnectionTest = LlmConnectionTestState(
                        isTesting = true,
                        message = testingMessage,
                        isSuccess = null,
                    ),
                )
            }
            try {
                val message = llmApi.testConnection(config)
                _uiState.update {
                    it.copy(
                        llmConnectionTest = LlmConnectionTestState(
                            isTesting = false,
                            message = message,
                            isSuccess = true,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        llmConnectionTest = LlmConnectionTestState(
                            isTesting = false,
                            message = e.message ?: "Connection test failed.",
                            isSuccess = false,
                        ),
                    )
                }
            } finally {
                if (config.provider == LlmProvider.LOCAL) {
                    setLocalModelBusy(false)
                }
            }
        }
    }

    private fun setLocalModelBusy(isBusy: Boolean, message: String? = null) {
        _uiState.update {
            it.copy(
                localModelBusy = LocalModelBusyState(
                    isBusy = isBusy,
                    message = if (isBusy) message else null,
                ),
            )
        }
    }

    fun navigateActiveTab(url: String) {
        val resolved = AddressBarNavigation.resolve(url) ?: return
        val tabId = _uiState.value.activeTabId ?: return
        if (_uiState.value.tabs.none { it.id == tabId }) return
        updateTabMetadata(tabId, url = resolved, status = BrowserTabStatus.LOADING)
        browserController.loadUrl(resolved, tabId)
    }

    fun toggleSkill(skillType: SkillType, enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.enabledSkills.toMutableSet().apply {
                if (enabled) add(skillType) else remove(skillType)
            }
            repository.saveEnabledSkills(updated)
            _uiState.update { state ->
                state.copy(
                    enabledSkills = updated,
                    skillConfigs = state.skillConfigs.map {
                        if (it.type == skillType) it.copy(enabled = enabled) else it
                    },
                )
            }
        }
    }
}