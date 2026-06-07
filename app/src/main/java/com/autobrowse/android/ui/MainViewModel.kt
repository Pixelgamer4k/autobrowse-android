package com.autobrowse.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autobrowse.android.AutobrowseApplication
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.FloatingWindowEngine
import com.autobrowse.android.domain.model.AgentPhase
import com.autobrowse.android.domain.model.AgentProgress
import com.autobrowse.android.domain.model.AutoBrowseRequest
import com.autobrowse.android.domain.model.AutomationTask
import com.autobrowse.android.domain.model.BrowserContext
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.withFrame
import com.autobrowse.android.domain.model.ChatMessage
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.PendingAttachment
import com.autobrowse.android.domain.model.Session
import com.autobrowse.android.domain.model.SkillConfig
import com.autobrowse.android.domain.model.SkillType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val session: Session? = null,
    val tabs: List<BrowserTab> = emptyList(),
    val windowFrames: Map<String, BrowserWindowFrame> = emptyMap(),
    val activeTabId: String? = null,
    val tasks: List<AutomationTask> = emptyList(),
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
    val browserPanelWeight: Float = 0.52f,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutobrowseApplication
    private val repository = app.repository
    private val agentLoop = app.agentLoop
    private val attachmentStore = app.attachmentStore
    private val attachmentProcessor = app.attachmentProcessor
    val browserController: BrowserController = app.browserController

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var sessionId: String? = null
    private var flowsBound = false
    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            val session = repository.getOrCreateActiveSession()
            sessionId = session.id
            val llmConfig = repository.getLlmConfig()
            _uiState.update {
                it.copy(
                    session = session,
                    llmConfig = llmConfig,
                    skillConfigs = app.skillRegistry.allSkillConfigs(),
                    enabledSkills = repository.getEnabledSkills(),
                    error = if (llmConfig.apiKey.isBlank()) {
                        "Add your API key in Settings (gear icon) to use the agent."
                    } else {
                        null
                    },
                )
            }
            bindSessionFlows(session.id)
        }
    }

    val tasks: StateFlow<List<AutomationTask>> = _uiState
        .map { it.tasks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun bindSessionFlows(id: String) {
        if (flowsBound) return
        flowsBound = true
        viewModelScope.launch {
            repository.observeTabs(id).collect { dbTabs ->
                _uiState.update { state ->
                    val tabsById = state.tabs.associateBy { it.id }
                    val frames = state.windowFrames.toMutableMap()
                    val liveIds = dbTabs.map { it.id }.toSet()
                    frames.keys.retainAll(liveIds)

                    val tabs = dbTabs.map { dbTab ->
                        val existing = tabsById[dbTab.id]
                        val frame = FloatingWindowEngine.hydrateFrame(
                            tabId = dbTab.id,
                            dbTab = dbTab,
                            existing = frames[dbTab.id],
                        ).also { frames[dbTab.id] = it }
                        FloatingWindowEngine.syncTabMetadata(dbTab, existing, frame)
                    }
                    state.copy(
                        tabs = tabs,
                        windowFrames = frames,
                        activeTabId = state.activeTabId ?: tabs.firstOrNull()?.id,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeTasks(id).collect { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
            }
        }
        viewModelScope.launch {
            repository.observeChat(id).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
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
            val frames = FloatingWindowEngine.commitLayout(
                frames = state.windowFrames,
                tabId = tabId,
                layout = layout,
                fallbackTab = fallbackTab,
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
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(400)
            persistWindowNow(tabId)
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
            val frames = FloatingWindowEngine.toggleMaximize(state.windowFrames, tabId)
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
            val frames = FloatingWindowEngine.minimize(state.windowFrames, tabId)
            state.copy(
                windowFrames = frames,
                tabs = applyFramesToTabs(state.tabs, frames),
                activeTabId = tabId,
            )
        }
        browserController.setActiveTab(tabId)
        persistWindowNow(tabId)
    }

    fun setBrowserPanelWeight(weight: Float) {
        _uiState.update { it.copy(browserPanelWeight = weight.coerceIn(0.25f, 0.75f)) }
    }

    fun addTab(url: String = "https://www.google.com") {
        val id = sessionId ?: return
        viewModelScope.launch {
            val index = _uiState.value.tabs.size
            val tab = BrowserTab(
                id = UUID.randomUUID().toString(),
                url = url,
                title = "New Tab",
                status = BrowserTabStatus.IDLE,
                zIndex = index,
                layout = BrowserWindowLayout.defaultForIndex(index),
                desktopMode = true,
            )
            repository.saveTab(tab, id)
            browserController.setActiveTab(tab.id)
            _uiState.update { state ->
                val frames = FloatingWindowEngine.addFrameForTab(state.windowFrames, tab)
                state.copy(
                    activeTabId = tab.id,
                    windowFrames = frames,
                )
            }
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

    fun sendMessage() {
        val prompt = _uiState.value.chatInput.trim()
        val attachments = _uiState.value.pendingAttachments
        val id = sessionId ?: return
        if ((prompt.isBlank() && attachments.isEmpty()) || _uiState.value.isAgentThinking) return

        viewModelScope.launch {
            val llmConfig = repository.getLlmConfig()
            if (llmConfig.apiKey.isBlank()) {
                _uiState.update {
                    it.copy(error = "Add your API key in Settings (gear icon) before sending messages.")
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
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isAgentThinking = false) }
            }
        }
    }

    fun saveLlmConfig(config: LlmConfig) {
        viewModelScope.launch {
            repository.saveLlmConfig(config)
            _uiState.update {
                it.copy(
                    llmConfig = config,
                    error = if (config.apiKey.isBlank()) {
                        "Add your API key in Settings (gear icon) to use the agent."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun navigateActiveTab(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val tabId = _uiState.value.activeTabId ?: return
        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return
        val normalized = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
        browserController.loadUrl(normalized, tabId)
        updateTabMetadata(tabId, url = normalized, status = BrowserTabStatus.LOADING)
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