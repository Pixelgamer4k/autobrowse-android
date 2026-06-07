package com.autobrowse.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autobrowse.android.AutobrowseApplication
import com.autobrowse.android.domain.model.AutoBrowseRequest

class AutoBrowseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val prompt = inputData.getString(KEY_PROMPT) ?: return Result.failure()
        val pageUrl = inputData.getString(KEY_PAGE_URL)

        val app = applicationContext as AutobrowseApplication
        val result = app.navigationAgent.processUserMessage(
            request = AutoBrowseRequest(
                prompt = prompt,
                sessionId = sessionId,
                targetUrl = pageUrl?.ifBlank { null },
            ),
            pageUrl = pageUrl,
            pageHtml = null,
            pageText = null,
        )

        return if (result.success) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_PROMPT = "prompt"
        const val KEY_PAGE_URL = "page_url"
    }
}