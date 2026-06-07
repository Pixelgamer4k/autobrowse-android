package com.autobrowse.android.skills

import com.autobrowse.android.domain.model.AgentAction
import com.autobrowse.android.domain.model.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WebRequestSkill : Skill {
    override val type = SkillType.WEB_REQUEST
    override val displayName = "Web Request"
    override val description = "Fetch remote URLs and retrieve response content for agent analysis."

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(context: SkillContext): SkillResult = withContext(Dispatchers.IO) {
        val url = extractUrl(context.userPrompt) ?: context.pageUrl
        if (url.isNullOrBlank()) {
            return@withContext SkillResult(
                success = false,
                output = "",
                error = "No URL found in prompt or active page.",
            )
        }

        runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                SkillResult(
                    success = response.isSuccessful,
                    output = body.take(8000),
                    extractedData = mapOf(
                        "url" to url,
                        "status_code" to response.code.toString(),
                        "content_length" to body.length.toString(),
                    ),
                    actions = listOf(
                        AgentAction(
                            type = "fetch",
                            target = url,
                            reasoning = "Retrieved page content for agent processing.",
                        ),
                    ),
                )
            }
        }.getOrElse { error ->
            SkillResult(success = false, output = "", error = error.message)
        }
    }

    private fun extractUrl(prompt: String): String? {
        val regex = Regex("""https?://[^\s"'<>]+""")
        return regex.find(prompt)?.value
    }
}