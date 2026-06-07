package com.autobrowse.android

import android.app.Application
import androidx.work.Configuration
import com.autobrowse.android.agent.NavigationAgent
import com.autobrowse.android.agent.core.AgentLoop
import com.autobrowse.android.agent.core.ContextCompressor
import com.autobrowse.android.agent.core.PromptBuilder
import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.agent.orchestration.TaskOrchestrator
import com.autobrowse.android.agent.tools.BrowserAdvancedTools
import com.autobrowse.android.agent.tools.BrowserBackTool
import com.autobrowse.android.agent.tools.BrowserSearchTool
import com.autobrowse.android.agent.tools.BrowserWaitTool
import com.autobrowse.android.agent.training.PostTaskSkillLearner
import com.autobrowse.android.agent.training.TrainingCorpusLoader
import com.autobrowse.android.agent.training.TrainingDataSeeder
import com.autobrowse.android.agent.tools.BrowserClickTool
import com.autobrowse.android.agent.tools.BrowserClickXYTool
import com.autobrowse.android.agent.tools.BrowserConsoleTool
import com.autobrowse.android.agent.tools.BrowserFillTool
import com.autobrowse.android.agent.tools.BrowserInteractiveSnapshotTool
import com.autobrowse.android.agent.tools.BrowserNavigateTool
import com.autobrowse.android.agent.tools.BrowserPressTool
import com.autobrowse.android.agent.tools.BrowserScrollTool
import com.autobrowse.android.agent.tools.BrowserTabCloseTool
import com.autobrowse.android.agent.tools.BrowserTabListTool
import com.autobrowse.android.agent.tools.BrowserTabOpenTool
import com.autobrowse.android.agent.tools.BrowserTabSwitchTool
import com.autobrowse.android.agent.tools.BrowserTypeTool
import com.autobrowse.android.agent.tools.BrowserVisionTool
import com.autobrowse.android.agent.tools.ChartGenerateTool
import com.autobrowse.android.agent.tools.ClarifyTool
import com.autobrowse.android.agent.tools.DelegateTaskTool
import com.autobrowse.android.agent.tools.DocumentGenerator
import com.autobrowse.android.agent.tools.ExecuteCodeTool
import com.autobrowse.android.agent.tools.ExtractDataTool
import com.autobrowse.android.agent.tools.MemoryRecallTool
import com.autobrowse.android.agent.tools.MemoryRememberTool
import com.autobrowse.android.agent.tools.PdfGenerateTool
import com.autobrowse.android.agent.tools.PythonExecuteTool
import com.autobrowse.android.agent.tools.ReflectTool
import com.autobrowse.android.agent.tools.RunParallelTasksTool
import com.autobrowse.android.agent.tools.SessionSearchTool
import com.autobrowse.android.agent.tools.SkillCreatorTool
import com.autobrowse.android.agent.tools.SkillManageTool
import com.autobrowse.android.agent.tools.SkillViewTool
import com.autobrowse.android.agent.tools.SkillsListTool
import com.autobrowse.android.agent.tools.SummarizeTool
import com.autobrowse.android.agent.tools.TodoWriteTool
import com.autobrowse.android.agent.tools.ToolRegistry
import com.autobrowse.android.agent.tools.WebFetchTool
import com.autobrowse.android.agent.trajectory.SelfImprovementEngine
import com.autobrowse.android.agent.trajectory.TrajectoryStore
import com.autobrowse.android.attachments.AttachmentProcessor
import com.autobrowse.android.attachments.AttachmentStore
import com.autobrowse.android.browser.AndroidTabManager
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.data.local.AutobrowseDatabase
import com.autobrowse.android.data.local.LocalLlmService
import com.autobrowse.android.data.local.ModelFileManager
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.data.settings.SecureSettingsStore
import com.autobrowse.android.skills.SkillRegistry
import com.autobrowse.android.skills.SkillStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutobrowseApplication : Application(), Configuration.Provider {
    lateinit var repository: AutobrowseRepository
        private set

    lateinit var navigationAgent: NavigationAgent
        private set

    lateinit var agentLoop: AgentLoop
        private set

    lateinit var taskOrchestrator: TaskOrchestrator
        private set

    lateinit var browserController: BrowserController
        private set

    lateinit var tabManager: AndroidTabManager
        private set

    lateinit var skillRegistry: SkillRegistry
        private set

    lateinit var skillStore: SkillStore
        private set

    lateinit var memoryManager: MemoryManager
        private set

    lateinit var attachmentStore: AttachmentStore
        private set

    lateinit var attachmentProcessor: AttachmentProcessor
        private set

    lateinit var llmApi: LlmApiService
        private set

    lateinit var modelFileManager: ModelFileManager
        private set

    lateinit var documentGenerator: DocumentGenerator
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var trainingCorpusLoader: TrainingCorpusLoader
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val database = AutobrowseDatabase.get(this)
        val settingsStore = SecureSettingsStore(this)
        modelFileManager = ModelFileManager(this)
        val localLlmService = LocalLlmService(this, modelFileManager)
        llmApi = LlmApiService(localLlmService)

        repository = AutobrowseRepository(database, settingsStore)
        skillStore = SkillStore(this)
        trainingCorpusLoader = TrainingCorpusLoader(this)
        skillRegistry = SkillRegistry(this, repository, llmApi)
        browserController = BrowserController()
        tabManager = AndroidTabManager()
        attachmentStore = AttachmentStore(this)
        attachmentProcessor = AttachmentProcessor(this)
        documentGenerator = DocumentGenerator(this)

        val trajectoryStore = TrajectoryStore(database.trajectoryDao())

        appScope.launch {
            trainingCorpusLoader.ensureLoaded()
            skillStore.ensureBundledSkills()
            TrainingDataSeeder(database.strategyDao(), trainingCorpusLoader).seedIfEmpty()
        }

        memoryManager = MemoryManager(
            memoryDao = database.memoryDao(),
            trajectoryDao = database.trajectoryDao(),
            repository = repository,
            llmApi = llmApi,
        )

        val selfImprovementEngine = SelfImprovementEngine(
            strategyDao = database.strategyDao(),
            repository = repository,
            llmApi = llmApi,
            memoryManager = memoryManager,
        )

        val postTaskSkillLearner = PostTaskSkillLearner(
            skillStore = skillStore,
            repository = repository,
            llmApi = llmApi,
        )

        toolRegistry = ToolRegistry(
            tools = listOf(
                BrowserSearchTool(browserController),
                BrowserWaitTool(browserController),
                BrowserNavigateTool(browserController),
                BrowserInteractiveSnapshotTool(browserController),
                BrowserClickTool(browserController),
                BrowserFillTool(browserController),
                BrowserTypeTool(browserController),
                BrowserScrollTool(browserController),
                BrowserPressTool(browserController),
                BrowserBackTool(browserController),
                BrowserClickXYTool(browserController),
                BrowserConsoleTool(browserController),
                BrowserVisionTool(browserController, llmApi, repository),
                BrowserTabOpenTool(tabManager, browserController),
                BrowserTabCloseTool(tabManager),
                BrowserTabSwitchTool(tabManager),
                BrowserTabListTool(tabManager),
                *BrowserAdvancedTools.createAll(browserController).toTypedArray(),
                WebFetchTool(skillRegistry, repository),
                ExtractDataTool(skillRegistry, repository),
                SummarizeTool(skillRegistry, repository),
                MemoryRememberTool(memoryManager),
                MemoryRecallTool(memoryManager),
                SessionSearchTool(memoryManager),
                ReflectTool(llmApi, repository),
                TodoWriteTool(),
                ClarifyTool(),
                DelegateTaskTool { taskOrchestrator },
                RunParallelTasksTool { taskOrchestrator },
                ExecuteCodeTool({ toolRegistry }, documentGenerator),
                PythonExecuteTool({ toolRegistry }, documentGenerator),
                PdfGenerateTool(documentGenerator),
                ChartGenerateTool(documentGenerator),
                SkillsListTool(skillStore),
                SkillViewTool(skillStore),
                SkillManageTool(skillStore),
                SkillCreatorTool(skillStore, llmApi, repository),
            ),
            browserController = browserController,
        )

        agentLoop = AgentLoop(
            repository = repository,
            llmApi = llmApi,
            toolRegistry = toolRegistry,
            promptBuilder = PromptBuilder(
                repository, memoryManager, skillStore, trainingCorpusLoader, trajectoryStore,
            ),
            memoryManager = memoryManager,
            contextCompressor = ContextCompressor(llmApi),
            trajectoryStore = trajectoryStore,
            selfImprovementEngine = selfImprovementEngine,
            postTaskSkillLearner = postTaskSkillLearner,
            tabManager = tabManager,
        )

        taskOrchestrator = TaskOrchestrator(agentLoop, appScope)
        navigationAgent = NavigationAgent(agentLoop)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}