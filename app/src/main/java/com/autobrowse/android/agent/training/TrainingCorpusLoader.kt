package com.autobrowse.android.agent.training

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BundledStrategy(
    val id: String = "",
    val domain: String = "",
    val heuristic: String = "",
    val confidence: Float = 0.8f,
    @Json(name = "successCount") val successCount: Int = 5,
    @Json(name = "failureCount") val failureCount: Int = 0,
)

data class BundledFailure(
    val id: String = "",
    val title: String = "",
    val symptom: String = "",
    val fix: String = "",
    val domain: String = "",
    @Json(name = "example_prompt") val examplePrompt: String? = null,
)

data class TrajectoryStep(
    val iteration: Int = 0,
    val tool: String = "",
    val args: Map<String, Any?> = emptyMap(),
    val success: Boolean = true,
)

data class BundledTrajectory(
    val id: String = "",
    val domain: String = "",
    val prompt: String = "",
    val success: Boolean = true,
    @Json(name = "toolCount") val toolCount: Int = 0,
    val steps: List<TrajectoryStep> = emptyList(),
    val lesson: String = "",
)

class TrainingCorpusLoader(private val context: Context) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val mutex = Mutex()
    private var strategies: List<BundledStrategy> = emptyList()
    private var failures: List<BundledFailure> = emptyList()
    private var trajectoryIndex: List<Pair<String, String>> = emptyList()
    private var siteTemplates: Map<String, String> = emptyMap()
    private var loaded = false

    suspend fun ensureLoaded() = mutex.withLock {
        if (loaded) return@withLock
        withContext(Dispatchers.IO) {
            strategies = loadStrategies()
            failures = loadFailures()
            trajectoryIndex = loadTrajectoryIndex()
            siteTemplates = loadSiteTemplates()
            loaded = true
        }
    }

    suspend fun allStrategies(): List<BundledStrategy> {
        ensureLoaded()
        return strategies
    }

    suspend fun siteTemplates(): Map<String, String> {
        ensureLoaded()
        return siteTemplates
    }

    suspend fun buildTrainingContext(userPrompt: String, limitChars: Int = 6000): String = withContext(Dispatchers.IO) {
        ensureLoaded()
        val domain = inferDomain(userPrompt)
        buildString {
            val traj = findMatchingTrajectory(userPrompt, domain)
            if (traj != null) {
                appendLine("## Gold Trajectory (proven sequence — replicate this)")
                appendLine("Task: ${traj.prompt}")
                appendLine("Lesson: ${traj.lesson}")
                traj.steps.forEach { step ->
                    val args = step.args.entries.joinToString { "${it.key}=${it.value}" }
                    appendLine("- ${step.tool}(${args.ifBlank { "…" }})")
                }
                appendLine()
            }
            val domainFailures = failures
                .filter { it.domain == domain || domain == "general" }
                .take(3)
            if (domainFailures.isNotEmpty()) {
                appendLine("## Anti-patterns (AVOID these)")
                domainFailures.forEach { f ->
                    appendLine("- **${f.title}**: ${f.symptom} → Fix: ${f.fix}")
                }
                appendLine()
            }
            val domainStrategies = strategies
                .filter { it.domain == domain }
                .sortedByDescending { it.confidence }
                .take(5)
            if (domainStrategies.isNotEmpty()) {
                appendLine("## Bundled strategies for $domain")
                domainStrategies.forEach { s ->
                    appendLine("- ${s.heuristic} (${(s.confidence * 100).toInt()}%)")
                }
            }
        }.take(limitChars)
    }

    private fun findMatchingTrajectory(prompt: String, domain: String): BundledTrajectory? {
        val lower = prompt.lowercase()
        val candidate = trajectoryIndex
            .asSequence()
            .map { (id, file) -> id to file }
            .filter { (_, file) ->
                val trajDomain = file.substringAfter("traj-").substringBefore("-")
                trajDomain.replace("_", ".") == domain.replace("_", ".") ||
                    lower.contains(trajDomain.replace("_", " "))
            }
            .firstOrNull()
            ?: trajectoryIndex.firstOrNull()
            ?: return null
        return runCatching {
            val json = context.assets.open("training/trajectories/${candidate.second}.json")
                .bufferedReader().use { it.readText() }
            moshi.adapter(BundledTrajectory::class.java).fromJson(json)
        }.getOrNull()
    }

    private fun loadStrategies(): List<BundledStrategy> = runCatching {
        val json = context.assets.open("training/strategies.json").bufferedReader().use { it.readText() }
        val root = moshi.adapter(StrategiesRoot::class.java).fromJson(json)
        root?.strategies.orEmpty()
    }.getOrDefault(emptyList())

    private fun loadFailures(): List<BundledFailure> = runCatching {
        val json = context.assets.open("training/failures.json").bufferedReader().use { it.readText() }
        val root = moshi.adapter(FailuresRoot::class.java).fromJson(json)
        root?.failures.orEmpty()
    }.getOrDefault(emptyList())

    private fun loadTrajectoryIndex(): List<Pair<String, String>> = runCatching {
        val json = context.assets.open("training/trajectories/index.json").bufferedReader().use { it.readText() }
        val root = moshi.adapter(TrajectoryIndexRoot::class.java).fromJson(json)
        root?.items.orEmpty().map { it.id to it.id }
    }.getOrDefault(emptyList())

    private fun loadSiteTemplates(): Map<String, String> = runCatching {
        val json = context.assets.open("training/site-templates.json").bufferedReader().use { it.readText() }
        val root = moshi.adapter(SiteTemplatesRoot::class.java).fromJson(json)
        root?.templates.orEmpty()
    }.getOrDefault(emptyMap())

    private fun inferDomain(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("youtube") -> "youtube.com"
            lower.contains("google") && lower.contains("search") -> "google.com"
            lower.contains("amazon") -> "amazon.com"
            lower.contains("reddit") -> "reddit.com"
            lower.contains("github") -> "github.com"
            lower.contains("wikipedia") -> "wikipedia.org"
            lower.contains("stackoverflow") -> "stackoverflow.com"
            lower.contains("linkedin") -> "linkedin.com"
            lower.contains("search") || lower.contains("find") -> "search"
            lower.contains("research") -> "research"
            lower.contains("form") || lower.contains("fill") -> "form_fill"
            lower.contains("login") -> "auth"
            else -> "general"
        }
    }

    private data class StrategiesRoot(val strategies: List<BundledStrategy> = emptyList())
    private data class FailuresRoot(val failures: List<BundledFailure> = emptyList())
    private data class TrajectoryIndexRoot(val items: List<TrajectoryIndexItem> = emptyList())
    private data class TrajectoryIndexItem(val id: String = "", val domain: String = "", val prompt: String = "")
    private data class SiteTemplatesRoot(val templates: Map<String, String> = emptyMap())
}