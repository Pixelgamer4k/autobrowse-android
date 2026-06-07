package com.autobrowse.android.skills

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.autobrowse.android.domain.model.AgentAction
import com.autobrowse.android.domain.model.SkillType
import com.autobrowse.android.worker.AutoBrowseWorker

class BackgroundTaskSkill(
    private val context: Context,
) : Skill {
    override val type = SkillType.BACKGROUND_TASK
    override val displayName = "Background Task"
    override val description = "Queue long-running browse and research tasks to run in the background."

    override suspend fun execute(context: SkillContext): SkillResult {
        val workRequest = OneTimeWorkRequestBuilder<AutoBrowseWorker>()
            .setInputData(
                workDataOf(
                    AutoBrowseWorker.KEY_SESSION_ID to context.sessionId,
                    AutoBrowseWorker.KEY_PROMPT to context.userPrompt,
                    AutoBrowseWorker.KEY_PAGE_URL to (context.pageUrl ?: ""),
                ),
            )
            .build()

        WorkManager.getInstance(this.context).enqueue(workRequest)

        return SkillResult(
            success = true,
            output = "Background task queued. You'll be notified when it completes.",
            actions = listOf(
                AgentAction(
                    type = "background_enqueue",
                    value = workRequest.id.toString(),
                    reasoning = "Long-running task delegated to WorkManager.",
                ),
            ),
        )
    }
}