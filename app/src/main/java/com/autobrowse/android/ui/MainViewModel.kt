package com.autobrowse.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autobrowse.android.AutobrowseApplication
import com.autobrowse.android.browser.AddressBarNavigation
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.FloatingWindowEngine
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
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.PendingAttachment
import com.autobrowse.android.domain.model.Session
import com.autobrowse.android.domain.model.SkillConfig
import com.autobrowse.android.domain.model.SkillType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    val showSettings: Boolean = false,
    val showLlmSetup: Boolean = false,
    val llmSetupFromSettings: Boolean = false,
    val llmConnectionTest: LlmConnectionTestState = LlmConnectionTestState(),
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutobrowseApplication
    private val repository = app.repository
    private val agentLoop = app.agentLoop
    private val attachmentStore = app.attachmentStore
    private val attachmentProcessor = app.attachmentProcessor
    private val llmApi = LlmApiService()
    val browserController: BrowserController = app.browserController

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var sessionId: String? = null
    private val activeSessionId = MutableStateFlow<String?>(null)
    private val persistJobs = mutableMapOf<String, Job>()
    private var agentJob: Job? = null

    init {
        viewModelScope.launch {
            val session = repository.getOrCreateActiveSession()
            val llmConfig = repository.getLlmConfig()
            _uiState.update {
                it.copy(
                    session = session,
                    llmConfig = llmConfig,
                    skillConfigs = app.skillRegistry.allSkillConfigs(),
                    enabledSkills = repository.getEnabledSkills(),
                    showLlmSetup = llmConfig.apiKey.isBlank(),
                    error = if (llmConfig.apiKey.isBlank()) {
                        "Add your API token on the setup screen to use the agent."
                    } else {
                        null
                    },
                )
            }
            activeSessionId.value = session.id
        }

        viewModelScope.launch {
            repository.observeSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
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
            agentLoop.progress.collect { progress ->
                _uiState.update {
                    it.copy(
                        agentProgress = progress,
                        isAgentThinking = progress.phase != AgentPhase.IDLE,
                    )
                }
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
        _uiState.update { it.copy(showSessionsPanel = false) }
    }

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
        _uiState.update { it.copy(showSettings = show) }
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
        agentJob?.cancel()
        agentLoop.requestCancel()
    }

    fun sendMessage() {
        val prompt = _uiState.value.chatInput.trim()
        val attachments = _uiState.value.pendingAttachments
        val id = sessionId ?: return
        if ((prompt.isBlank() && attachments.isEmpty()) || _uiState.value.isAgentThinking) return

        agentJob?.cancel()
        agentJob = viewModelScope.launch {
            val llmConfig = repository.getLlmConfig()
            if (llmConfig.apiKey.isBlank()) {
                _uiState.update {
                    it.copy(
                        error = "Add your API token on the setup screen before sending messages.",
                        showLlmSetup = true,
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(chatInput = "", pendingAttachments = emptyList(), isAgentThinking = true, error = null)
            }
            try {
                val stored = attachments.map { pending ->
                    attachmentStore.persist(getApplication(), pending)
                }
                val payload = attachmentProcessor.processAll(stored)

                val pageHtml = browserController.getPageHtml()
                val pageText = browserController.getPageText()
                val pageUrl = browserController.getCurrentUrl()

                val result = agentLoop.runConversation(
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
            }
        }
    }

    fun saveLlmConfig(config: LlmConfig) {
        viewModelScope.launch {
            repository.saveLlmConfig(config)
            val fromSettings = _uiState.value.llmSetupFromSettings
            _uiState.update {
                it.copy(
                    llmConfig = config,
                    showLlmSetup = config.apiKey.isBlank(),
                    showSettings = fromSettings && config.apiKey.isNotBlank(),
                    llmSetupFromSettings = false,
                    llmConnectionTest = LlmConnectionTestState(),
                    error = if (config.apiKey.isBlank()) {
                        "Add your API token on the setup screen to use the agent."
                    } else {
                        null
                    },
                )
            }
        }
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

    fun testLlmConnection(apiKey: String, apiUrl: String, modelId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    llmConnectionTest = LlmConnectionTestState(
                        isTesting = true,
                        message = "Testing connection…",
                        isSuccess = null,
                    ),
                )
            }
            try {
                val message = llmApi.testConnection(
                    _uiState.value.llmConfig.copy(
                        apiKey = apiKey,
                        apiUrl = apiUrl,
                        modelId = modelId,
                    ),
                )
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
            }
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