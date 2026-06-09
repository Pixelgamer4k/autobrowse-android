package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.agent.training.TrainingCorpusLoader
import com.autobrowse.android.agent.trajectory.TrajectoryStore
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.feedback.FeedbackManager
import com.autobrowse.android.skills.SkillStore

/**
 * Hermes-style prompt assembly: stable prefix + volatile context + progressive skill disclosure.
 */
class PromptBuilder(
    repository: AutobrowseRepository,
    private val memoryManager: MemoryManager,
    skillStore: SkillStore? = null,
    feedbackManager: FeedbackManager? = null,
    @Suppress("UNUSED_PARAMETER") trainingCorpus: TrainingCorpusLoader? = null,
    @Suppress("UNUSED_PARAMETER") trajectoryStore: TrajectoryStore? = null,
) {
    private val hermes = HermesPromptAssembler(
        memoryManager = memoryManager,
        skillStore = skillStore,
        feedbackManager = feedbackManager,
    )

    suspend fun buildBundle(
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        toolCount: Int,
        userPrompt: String = "",
    ): PromptBundle = hermes.assemble(
        userPrompt = userPrompt,
        strategies = strategies,
        pageUrl = pageUrl,
        toolCount = toolCount,
    )

    suspend fun buildForLocal(
        userPrompt: String,
        pageUrl: String?,
    ): PromptBundle = hermes.assembleLocal(userPrompt, pageUrl)

    suspend fun rebuildVolatile(
        userPrompt: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
    ): String = hermes.rebuildVolatile(userPrompt, strategies, pageUrl)

    /** @deprecated Use [buildBundle] for split prefix-cache messages. */
    suspend fun build(
        prefetchedMemory: String,
        strategies: List<LearnedStrategy>,
        pageUrl: String?,
        enabledToolNames: List<String>,
        userPrompt: String = "",
    ): String = buildBundle(
        strategies = strategies,
        pageUrl = pageUrl,
        toolCount = enabledToolNames.size,
        userPrompt = userPrompt,
    ).fullSystem
}