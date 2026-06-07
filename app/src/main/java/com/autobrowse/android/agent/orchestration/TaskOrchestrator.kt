package com.autobrowse.android.agent.orchestration

import com.autobrowse.android.agent.core.AgentLoop
import com.autobrowse.android.domain.model.AgentConversationResult
import com.autobrowse.android.domain.model.AutoBrowseRequest
import com.autobrowse.android.domain.model.BrowserContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TaskOrchestrator(
    private val agentLoop: AgentLoop,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    suspend fun runSingle(
        request: AutoBrowseRequest,
        browserContext: BrowserContext,
    ): AgentConversationResult = mutex.withLock {
        agentLoop.runConversation(request, browserContext)
    }

    fun launch(
        request: AutoBrowseRequest,
        browserContext: BrowserContext,
    ): String {
        val taskId = UUID.randomUUID().toString()
        val job = scope.launchTask(request, browserContext, taskId)
        activeJobs[taskId] = job
        job.invokeOnCompletion { activeJobs.remove(taskId) }
        return taskId
    }

    suspend fun runParallel(
        sessionId: String,
        prompts: List<String>,
        browserContext: BrowserContext,
    ): List<AgentConversationResult> = coroutineScope {
        prompts.map { prompt ->
            async {
                mutex.withLock {
                    agentLoop.runConversation(
                        request = AutoBrowseRequest(prompt = prompt, sessionId = sessionId),
                        browserContext = browserContext,
                    )
                }
            }
        }.map { it.await() }
    }

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        agentLoop.requestCancel()
    }

    private fun CoroutineScope.launchTask(
        request: AutoBrowseRequest,
        browserContext: BrowserContext,
        taskId: String,
    ): Job = launch {
        mutex.withLock {
            agentLoop.runConversation(request, browserContext)
        }
    }
}