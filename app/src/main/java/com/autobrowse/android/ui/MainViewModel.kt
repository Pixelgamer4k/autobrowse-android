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
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.FloatingWindowEngine
import com.autobrowse.android.browser.TabInfo
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
import com.autobrowse.android.domain.model.LocalLlmModel
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.MiniAppId
import com.autobrowse.android.domain.model.Note
import com.autobrowse.android.domain.model.NoteBlock
import com.autobrowse.android.domain.model.NoteBlockType
import com.autobrowse.android.domain.model.NoteListItem
import com.autobrowse.android.domain.model.PendingAttachment
import com.autobrowse.android.ui.miniapps.NotesEditorState
import android.graphics.Bitmap
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

data class SkillTransferState(
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
    val skillTransfer: SkillTransferState = SkillTransferState(),
    val error: String? = null,
    val showMiniApps: Boolean = false,
    val activeMiniApp: MiniAppId? = null,
    val notes: List<NoteListItem> = emptyList(),
    val notesEditor: NotesEditorState = NotesEditorState(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutobrowseApplication
    private val repository = app.repository
    private val agentLoop = app.agentLoop
    private val attachmentStore = app.attachmentStore
    private val attachmentProcessor = app.attachmentProcessor
    private val notesStore = app.notesStore
    private val noteExporter = app.noteExporter
    private val llmApi = app.llmApi
    private var noteSaveJob: Job? = null
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
        viewModelScope.launch {
            val session = repository.createNewSession()
            val llmConfig = repository.getLlmConfig()
            _uiState.update {
                it.copy(
                    session = session,
                    llmConfig = llmConfig,
                    skillConfigs = app.skillRegistry.allSkillConfigs(),
                    enabledSkills = repository.getEnabledSkills(),
                    showLlmSetup = !llmConfig.isConfigured(),
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
            repository.observeNotes().collect { notes ->
                _uiState.update { state ->
                    if (state.notesEditor.searchQuery.isBlank()) {
                        state.copy(notes = notes)
                    } else {
                        state
                    }
                }
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
        if (show) refreshAgentSkills()
        _uiState.update { it.copy(showSettings = show) }
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
        _uiState.update { state ->
            val frames = FloatingWindowEngine.addFrameForTab(state.windowFrames, tab)
            state.copy(activeTabId = tab.id, windowFrames = frames)
        }
        browserController.setActiveTab(tab.id)
        url?.let { browserController.loadUrl(it, tab.id) }
        val frame = _uiState.value.windowFrames[tab.id] ?: BrowserWindowFrame.fromTab(tab)
        repository.saveTab(tab.withFrame(frame), session)
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
                        maxIterations = 20,
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
            repository.saveLlmConfig(config)
            val fromSettings = _uiState.value.llmSetupFromSettings
            _uiState.update {
                it.copy(
                    llmConfig = config,
                    showLlmSetup = !config.isConfigured(),
                    showSettings = fromSettings && config.isConfigured(),
                    llmSetupFromSettings = false,
                    llmConnectionTest = LlmConnectionTestState(),
                    error = setupBannerMessage(config),
                )
            }
            if (config.isConfigured()) {
                llmApi.warmUpLocalModel(config)
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
                val paths = app.modelFileManager.downloadModel(model) { progress ->
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
                    localModelPath = paths.modelPath,
                    localMmprojPath = paths.mmprojPath,
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
                        llmConnectionTest = LlmConnectionTestState(
                            message = "Downloaded ${paths.modelPath.substringAfterLast('/')}",
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
            putExtra(Intent.EXTRA_SUBJECT, "Autobrowse learned skills")
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
            try {
                val paths = app.modelFileManager.importModel(uri, model)
                _uiState.update {
                    it.copy(
                        llmConfig = it.llmConfig.copy(
                            provider = LlmProvider.LOCAL,
                            localModel = model,
                            localModelPath = paths.modelPath,
                            localMmprojPath = paths.mmprojPath,
                        ),
                        llmConnectionTest = LlmConnectionTestState(
                            message = "Imported ${paths.modelPath.substringAfterLast('/')}",
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
            }
        }
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    private fun setupBannerMessage(config: LlmConfig): String? = when {
        config.isConfigured() -> null
        config.provider == LlmProvider.LOCAL ->
            "Local models are experimental — expect 6–10 min per response. Cloud API is recommended."
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
                "Loading model and testing ${config.backend.name} backend…"
            } else {
                "Testing connection…"
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

    fun openMiniApps() {
        _uiState.update { it.copy(showMiniApps = true) }
    }

    fun closeMiniApps() {
        _uiState.update { it.copy(showMiniApps = false, activeMiniApp = null) }
    }

    fun launchMiniApp(app: MiniAppId) {
        _uiState.update { it.copy(activeMiniApp = app) }
        if (app == MiniAppId.NOTES && _uiState.value.notesEditor.selectedNoteId == null) {
            createNewNote()
        }
    }

    fun backToMiniAppLauncher() {
        _uiState.update { it.copy(activeMiniApp = null) }
    }

    fun updateNotesSearch(query: String) {
        viewModelScope.launch {
            val notes = if (query.isBlank()) {
                repository.getRecentNotes(50).map { it.toListItem() }
            } else {
                repository.searchNotes(query).map { it.toListItem() }
            }
            _uiState.update {
                it.copy(
                    notesEditor = it.notesEditor.copy(searchQuery = query),
                    notes = notes,
                )
            }
        }
    }

    fun selectNote(noteId: String) {
        viewModelScope.launch {
            val note = repository.getNote(noteId) ?: return@launch
            _uiState.update {
                it.copy(
                    notesEditor = NotesEditorState(
                        selectedNoteId = note.id,
                        title = note.title,
                        body = note.markdownBody,
                        isPinned = note.isPinned,
                        searchQuery = it.notesEditor.searchQuery,
                    ),
                )
            }
        }
    }

    fun createNewNote() {
        viewModelScope.launch {
            val note = Note(
                id = UUID.randomUUID().toString(),
                title = "",
                blocks = listOf(NoteBlock(id = UUID.randomUUID().toString(), type = NoteBlockType.TEXT)),
                sessionId = sessionId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repository.saveNote(note)
            _uiState.update {
                it.copy(
                    notesEditor = NotesEditorState(
                        selectedNoteId = note.id,
                        searchQuery = it.notesEditor.searchQuery,
                    ),
                )
            }
        }
    }

    fun updateNoteTitle(title: String) {
        _uiState.update { it.copy(notesEditor = it.notesEditor.copy(title = title)) }
        scheduleNoteSave()
    }

    fun updateNoteBody(body: String) {
        _uiState.update { it.copy(notesEditor = it.notesEditor.copy(body = body)) }
        scheduleNoteSave()
    }

    fun toggleNotePin() {
        _uiState.update {
            it.copy(notesEditor = it.notesEditor.copy(isPinned = !it.notesEditor.isPinned))
        }
        scheduleNoteSave()
    }

    fun toggleNotePreview() {
        _uiState.update {
            it.copy(notesEditor = it.notesEditor.copy(isPreviewMode = !it.notesEditor.isPreviewMode))
        }
    }

    fun wrapNoteSelection(prefix: String, suffix: String) {
        val editor = _uiState.value.notesEditor
        val insertion = "$prefix text $suffix"
        updateNoteBody(if (editor.body.isBlank()) insertion else "${editor.body}\n$insertion")
    }

    fun insertNoteLinePrefix(prefix: String) {
        val editor = _uiState.value.notesEditor
        updateNoteBody(if (editor.body.isBlank()) prefix else "${editor.body}\n$prefix")
    }

    fun insertNoteBlock(block: String) {
        val editor = _uiState.value.notesEditor
        updateNoteBody(editor.body + block)
    }

    fun attachNoteImage(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val path = notesStore.persistImage(context, uri, "image.jpg")
            val markdown = "\n![image]($path)\n"
            insertNoteBlock(markdown)
        }
    }

    fun saveNoteDrawing(bitmap: Bitmap) {
        viewModelScope.launch {
            val path = notesStore.saveDrawing(bitmap)
            bitmap.recycle()
            insertNoteBlock("\n![drawing]($path)\n")
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            val editor = _uiState.value.notesEditor
            if (editor.selectedNoteId == noteId) {
                _uiState.update {
                    it.copy(notesEditor = NotesEditorState(searchQuery = it.notesEditor.searchQuery))
                }
            }
        }
    }

    fun shareExportedNoteText() = shareNote("text/plain") { noteExporter.exportText(it) }

    fun shareExportedNoteMarkdown() = shareNote("text/markdown") { noteExporter.exportMarkdown(it) }

    fun shareExportedNotePdf() = shareNote("application/pdf") { noteExporter.exportPdf(it) }

    fun shareExportedNoteImage() = shareNote("image/png") { noteExporter.exportImage(it) }

    private fun shareNote(mime: String, block: suspend (Note) -> Uri) {
        viewModelScope.launch {
            persistCurrentNote()
            val editor = _uiState.value.notesEditor
            val note = buildNoteFromEditor(editor) ?: return@launch
            runCatching {
                val uri = block(note)
                val intent = noteExporter.buildShareIntent(uri, mime)
                val context = getApplication<Application>()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(Intent.createChooser(intent, "Export note"))
            }
        }
    }

    private fun scheduleNoteSave() {
        noteSaveJob?.cancel()
        noteSaveJob = viewModelScope.launch {
            delay(500)
            persistCurrentNote()
        }
    }

    private suspend fun persistCurrentNote() {
        val editor = _uiState.value.notesEditor
        val note = buildNoteFromEditor(editor) ?: return
        repository.saveNote(note)
    }

    private suspend fun buildNoteFromEditor(editor: NotesEditorState): Note? {
        val id = editor.selectedNoteId ?: return null
        val existing = repository.getNote(id)
        val now = System.currentTimeMillis()
        val blocks = parseBodyToBlocks(editor.body)
        return Note(
            id = id,
            title = editor.title,
            blocks = blocks,
            sessionId = sessionId ?: existing?.sessionId,
            tags = existing?.tags.orEmpty(),
            isPinned = editor.isPinned,
            folder = existing?.folder ?: "Notes",
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private fun parseBodyToBlocks(body: String): List<NoteBlock> {
        if (body.isBlank()) {
            return listOf(NoteBlock(id = UUID.randomUUID().toString(), type = NoteBlockType.TEXT))
        }
        return listOf(
            NoteBlock(
                id = UUID.randomUUID().toString(),
                type = NoteBlockType.TEXT,
                markdown = body,
            ),
        )
    }

    private fun filterNotes(query: String, notes: List<NoteListItem>): List<NoteListItem> {
        if (query.isBlank()) return notes
        val q = query.lowercase()
        return notes.filter {
            it.title.lowercase().contains(q) ||
                it.preview.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
        }
    }

    private fun Note.toListItem() = NoteListItem(
        id = id,
        title = title.ifBlank { "Untitled" },
        preview = markdownBody.lineSequence().firstOrNull()?.take(120) ?: "",
        isPinned = isPinned,
        folder = folder,
        updatedAt = updatedAt,
        tags = tags,
    )

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