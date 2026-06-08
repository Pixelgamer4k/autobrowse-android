package com.autobrowse.android.agent.core

import com.autobrowse.android.agent.memory.MemoryManager
import com.autobrowse.android.agent.training.PostTaskSkillLearner
import com.autobrowse.android.agent.trajectory.SelfImprovementEngine
import com.autobrowse.android.agent.trajectory.TrajectoryStore
import com.autobrowse.android.domain.model.AgentTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class PostTaskLearningResult(
    val memoriesLearned: Int = 0,
    val strategiesUpdated: Int = 0,
    val skillsLearned: Int = 0,
)

/**
 * Runs after the agent response is shown. Uses fast heuristic learning only —
 * no blocking LLM calls on the user-visible path.
 */
class PostTaskLearning(
    private val scope: CoroutineScope,
    private val memoryManager: MemoryManager,
    private val selfImprovementEngine: SelfImprovementEngine,
    private val postTaskSkillLearner: PostTaskSkillLearner?,
    private val trajectoryStore: TrajectoryStore,
) {
    fun schedule(
        sessionId: String,
        taskId: String,
        prompt: String,
        rawPrompt: String,
        finalResponse: String,
        taskSuccess: Boolean,
        turns: List<AgentTurn>,
        pageUrl: String?,
    ) {
        scope.launch(Dispatchers.IO) {
            supervisorScope {
                run(
                    sessionId = sessionId,
                    taskId = taskId,
                    prompt = prompt,
                    rawPrompt = rawPrompt,
                    finalResponse = finalResponse,
                    taskSuccess = taskSuccess,
                    turns = turns,
                    pageUrl = pageUrl,
                )
            }
        }
    }

    suspend fun run(
        sessionId: String,
        taskId: String,
        prompt: String,
        rawPrompt: String,
        finalResponse: String,
        taskSuccess: Boolean,
        turns: List<AgentTurn>,
        pageUrl: String?,
    ): PostTaskLearningResult = coroutineScope {
        val trajectory = async {
            trajectoryStore.save(
                sessionId = sessionId,
                taskId = taskId,
                prompt = prompt,
                success = taskSuccess,
                turns = turns,
            )
        }
        val memories = async {
            memoryManager.syncTurnFast(prompt, finalResponse)
        }
        val strategies = async {
            selfImprovementEngine.reflectFast(
                prompt = prompt,
                success = taskSuccess,
                turns = turns,
                pageUrl = pageUrl,
            )
        }
        val skills = async {
            postTaskSkillLearner?.learnFast(
                prompt = rawPrompt,
                success = taskSuccess,
                turns = turns,
                pageUrl = pageUrl,
            ) ?: 0
        }

        trajectory.await()
        PostTaskLearningResult(
            memoriesLearned = memories.await(),
            strategiesUpdated = strategies.await(),
            skillsLearned = skills.await(),
        )
    }
}