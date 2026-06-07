package com.autobrowse.android

import android.app.Application
import androidx.work.Configuration
import com.autobrowse.android.agent.NavigationAgent
import com.autobrowse.android.attachments.AttachmentProcessor
import com.autobrowse.android.attachments.AttachmentStore
import com.autobrowse.android.agent.core.AgentLoop
import com.autobrowse.android.agent.core.ContextCompressor
import com.autobrowse.android.agent.core.PromptBuilder
import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.agent.tools.BrowserClickTool
import com.autobrowse.android.agent.tools.BrowserFillTool
import com.autobrowse.android.agent.tools.BrowserNavigateTool
import com.autobrowse.android.agent.tools.BrowserSnapshotTool
import com.autobrowse.android.agent.tools.ExtractDataTool
import com.autobrowse.android.agent.tools.MemoryRecallTool
import com.autobrowse.android.agent.tools.MemoryRememberTool
import com.autobrowse.android.agent.tools.ReflectTool
import com.autobrowse.android.agent.tools.SessionSearchTool
import com.autobrowse.android.agent.tools.SummarizeTool
import com.autobrowse.android.agent.tools.ToolRegistry
import com.autobrowse.android.agent.tools.WebFetchTool
import com.autobrowse.android.agent.trajectory.SelfImprovementEngine
import com.autobrowse.android.agent.trajectory.TrajectoryStore
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.data.local.AutobrowseDatabase
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.data.settings.SecureSettingsStore
import com.autobrowse.android.skills.SkillRegistry

class AutobrowseApplication : Application(), Configuration.Provider {
    lateinit var repository: AutobrowseRepository
        private set

    lateinit var navigationAgent: NavigationAgent
        private set

    lateinit var agentLoop: AgentLoop
        private set

    lateinit var browserController: BrowserController
        private set

    lateinit var skillRegistry: SkillRegistry
        private set

    lateinit var memoryManager: MemoryManager
        private set

    lateinit var attachmentStore: AttachmentStore
        private set

    lateinit var attachmentProcessor: AttachmentProcessor
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AutobrowseDatabase.get(this)
        val settingsStore = SecureSettingsStore(this)
        val llmApi = LlmApiService()

        repository = AutobrowseRepository(database, settingsStore)
        skillRegistry = SkillRegistry(this, repository, llmApi)
        browserController = BrowserController()
        attachmentStore = AttachmentStore(this)
        attachmentProcessor = AttachmentProcessor(this)

        memoryManager = MemoryManager(
            memoryDao = database.memoryDao(),
            trajectoryDao = database.trajectoryDao(),
            repository = repository,
            llmApi = llmApi,
        )

        val toolRegistry = ToolRegistry(
            tools = listOf(
                BrowserNavigateTool(browserController),
                BrowserSnapshotTool(browserController),
                BrowserFillTool(browserController),
                BrowserClickTool(browserController),
                WebFetchTool(skillRegistry, repository),
                ExtractDataTool(skillRegistry, repository),
                SummarizeTool(skillRegistry, repository),
                MemoryRememberTool(memoryManager),
                MemoryRecallTool(memoryManager),
                SessionSearchTool(memoryManager),
                ReflectTool(llmApi, repository),
            ),
        )

        val selfImprovementEngine = SelfImprovementEngine(
            strategyDao = database.strategyDao(),
            repository = repository,
            llmApi = llmApi,
            memoryManager = memoryManager,
        )

        agentLoop = AgentLoop(
            repository = repository,
            llmApi = llmApi,
            toolRegistry = toolRegistry,
            promptBuilder = PromptBuilder(repository, memoryManager),
            memoryManager = memoryManager,
            contextCompressor = ContextCompressor(llmApi),
            trajectoryStore = TrajectoryStore(database.trajectoryDao()),
            selfImprovementEngine = selfImprovementEngine,
        )

        navigationAgent = NavigationAgent(agentLoop)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}