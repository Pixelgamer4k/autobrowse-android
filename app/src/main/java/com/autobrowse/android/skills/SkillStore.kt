package com.autobrowse.android.skills

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SkillMetadata(
    val name: String,
    val description: String,
    val path: String,
    val category: String = "user",
    val version: String = "1.0.0",
    val triggers: List<String> = emptyList(),
    val learnedRuns: Int = 0,
)

class SkillStore(private val context: Context) {
    private val skillsRoot: File
        get() = File(context.filesDir, "skills").also { it.mkdirs() }

    suspend fun ensureBundledSkills() = withContext(Dispatchers.IO) {
        copyAssetDir("skills", skillsRoot)
    }

    suspend fun listSkills(): List<SkillMetadata> = withContext(Dispatchers.IO) {
        skillsRoot.walkTopDown()
            .filter { it.name == "SKILL.md" }
            .mapNotNull { file -> parseMetadata(file) }
            .sortedBy { it.name }
            .toList()
    }

    suspend fun readSkill(name: String, relativePath: String? = null): String = withContext(Dispatchers.IO) {
        val file = resolveSkillFile(name, relativePath)
            ?: throw IllegalArgumentException("Skill not found: $name")
        file.readText()
    }

    suspend fun createSkill(
        name: String,
        description: String,
        body: String,
        category: String = "user",
    ): String = withContext(Dispatchers.IO) {
        val dir = File(skillsRoot, "$category/$name").also { it.mkdirs() }
        dir.resolve("references").mkdirs()
        dir.resolve("scripts").mkdirs()
        dir.resolve("assets").mkdirs()
        val content = buildSkillMarkdown(name, description, body)
        val file = File(dir, "SKILL.md")
        file.writeText(content)
        file.absolutePath
    }

    suspend fun patchSkill(name: String, find: String, replace: String): String = withContext(Dispatchers.IO) {
        val file = findSkillFile(name) ?: throw IllegalArgumentException("Skill not found: $name")
        val updated = file.readText().replace(find, replace)
        file.writeText(updated)
        "Patched ${file.absolutePath}"
    }

    suspend fun writeSkillFile(name: String, relativePath: String, content: String): String =
        withContext(Dispatchers.IO) {
            val base = findSkillDir(name) ?: throw IllegalArgumentException("Skill not found: $name")
            val target = File(base, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
            target.absolutePath
        }

    suspend fun deleteSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        val dir = findSkillDir(name) ?: return@withContext false
        dir.deleteRecursively()
    }

    suspend fun exportLearnedSkillsBundle(): LearnedSkillsBundle = withContext(Dispatchers.IO) {
        val learned = listSkills().filter { it.category == "learned" }
        val entries = learned.mapNotNull { metadata ->
            val dir = findSkillDir(metadata.name) ?: return@mapNotNull null
            val skillMd = dir.resolve("SKILL.md").readText()
            val extraFiles = collectRelativeTextFiles(dir).filterKeys { it != "SKILL.md" }
            ExportedLearnedSkill(
                name = metadata.name,
                description = metadata.description,
                version = metadata.version,
                triggers = metadata.triggers,
                learnedRuns = metadata.learnedRuns,
                skillMd = skillMd,
                files = extraFiles,
            )
        }
        LearnedSkillsBundle(skills = entries)
    }

    suspend fun exportLearnedSkillsJson(): String = withContext(Dispatchers.IO) {
        LearnedSkillsSerializer.toJson(exportLearnedSkillsBundle())
    }

    suspend fun importLearnedSkillsJson(json: String, merge: Boolean = true): Int = withContext(Dispatchers.IO) {
        val bundle = LearnedSkillsSerializer.fromJson(json)
        var imported = 0
        for (skill in bundle.skills) {
            require(skill.name.isNotBlank()) { "Skill export contains an entry with no name." }
            require(skill.skillMd.isNotBlank()) { "Skill '${skill.name}' is missing SKILL.md content." }
            val targetDir = File(skillsRoot, "learned/${skill.name}")
            if (targetDir.exists() && !merge) continue
            targetDir.mkdirs()
            targetDir.resolve("references").mkdirs()
            targetDir.resolve("scripts").mkdirs()
            targetDir.resolve("assets").mkdirs()
            targetDir.resolve("SKILL.md").writeText(skill.skillMd)
            skill.files.forEach { (relativePath, content) ->
                val file = File(targetDir, relativePath)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            imported++
        }
        imported
    }

    private fun collectRelativeTextFiles(skillDir: File): Map<String, String> {
        val result = linkedMapOf<String, String>()
        skillDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relative = file.relativeTo(skillDir).path.replace('\\', '/')
                result[relative] = file.readText()
            }
        return result
    }

    suspend fun findMatchingSkills(prompt: String, limit: Int = 5): List<SkillMetadata> =
        withContext(Dispatchers.IO) {
            listSkills()
                .map { it to com.autobrowse.android.agent.core.TaskPreprocessor.scoreSkillMatch(it, prompt) }
                .filter { (_, score) -> score >= 2 }
                .sortedByDescending { (_, score) -> score }
                .take(limit)
                .map { (skill, _) -> skill }
        }

    suspend fun upsertLearnedFromTask(
        name: String,
        description: String,
        triggers: List<String>,
        playbookBody: String,
        prompt: String,
        toolSequence: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val existing = findSkillDir(name)
        if (existing != null) {
            val file = existing.resolve("SKILL.md")
            val text = file.readText()
            val runBlock = formatLearnedRun(prompt, toolSequence)
            val updated = if (text.contains("## Learned runs")) {
                text.replace(
                    "## Learned runs",
                    "## Learned runs\n$runBlock",
                )
            } else {
                text.trimEnd() + "\n\n## Learned runs\n$runBlock\n"
            }
            val withCount = bumpLearnedRuns(updated)
            file.writeText(withCount)
            file.absolutePath
        } else {
            val body = buildString {
                appendLine(playbookBody.trim())
                appendLine()
                appendLine("## Triggers")
                triggers.forEach { appendLine("- $it") }
                appendLine()
                appendLine("## Learned runs")
                appendLine(formatLearnedRun(prompt, toolSequence))
            }
            createLearnedSkill(name, description, body, triggers)
        }
    }

    private fun createLearnedSkill(
        name: String,
        description: String,
        body: String,
        triggers: List<String>,
    ): String {
        val dir = File(skillsRoot, "learned/$name").also { it.mkdirs() }
        dir.resolve("references").mkdirs()
        val triggerLine = if (triggers.isEmpty()) "" else "triggers: ${triggers.joinToString(", ")}\n"
        val content = """
            ---
            name: $name
            description: $description
            version: 1.0.0
            category: learned
            learned_runs: 1
            $triggerLine---
            
            $body
        """.trimIndent()
        val file = File(dir, "SKILL.md")
        file.writeText(content)
        return file.absolutePath
    }

    private fun formatLearnedRun(prompt: String, toolSequence: List<String>): String {
        val tools = toolSequence.joinToString(" → ")
        return "- **${prompt.take(72)}** — `$tools`"
    }

    private fun bumpLearnedRuns(text: String): String {
        val current = extractFrontmatter(text, "learned_runs")?.toIntOrNull() ?: 0
        return if (text.startsWith("---")) {
            val end = text.indexOf("---", 3)
            if (end < 0) text
            else {
                val block = text.substring(3, end)
                val updatedBlock = if (block.contains("learned_runs:")) {
                    block.replace(Regex("""learned_runs:\s*\d+"""), "learned_runs: ${current + 1}")
                } else {
                    block.trimEnd() + "\nlearned_runs: ${current + 1}\n"
                }
                "---$updatedBlock---" + text.substring(end + 3)
            }
        } else {
            text
        }
    }

    private fun findSkillDir(name: String): File? =
        skillsRoot.walkTopDown()
            .firstOrNull { it.isDirectory && it.name == name && File(it, "SKILL.md").exists() }

    private fun findSkillFile(name: String): File? =
        findSkillDir(name)?.resolve("SKILL.md")

    private fun resolveSkillFile(name: String, relativePath: String?): File? {
        val dir = findSkillDir(name) ?: return null
        return if (relativePath.isNullOrBlank()) File(dir, "SKILL.md") else File(dir, relativePath)
    }

    private fun parseMetadata(file: File): SkillMetadata? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val name = extractFrontmatter(text, "name") ?: file.parentFile?.name ?: return null
        val description = extractFrontmatter(text, "description") ?: "No description"
        val version = extractFrontmatter(text, "version") ?: "1.0.0"
        val category = extractFrontmatter(text, "category")
            ?: when (file.parentFile?.parentFile?.name) {
                "learned" -> "learned"
                "user" -> "user"
                else -> "bundled"
            }
        val triggers = extractFrontmatter(text, "triggers")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val learnedRuns = extractFrontmatter(text, "learned_runs")?.toIntOrNull() ?: 0
        return SkillMetadata(
            name = name,
            description = description,
            path = file.parentFile?.absolutePath.orEmpty(),
            category = category,
            version = version,
            triggers = triggers,
            learnedRuns = learnedRuns,
        )
    }

    private fun extractFrontmatter(text: String, key: String): String? {
        if (!text.startsWith("---")) return null
        val end = text.indexOf("---", 3)
        if (end < 0) return null
        val block = text.substring(3, end)
        val regex = Regex("""^$key:\s*(.+)$""", RegexOption.MULTILINE)
        return regex.find(block)?.groupValues?.get(1)?.trim()
    }

    private fun buildSkillMarkdown(name: String, description: String, body: String): String = """
        ---
        name: $name
        description: $description
        version: 1.0.0
        ---
        
        $body
    """.trimIndent()

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        for (entry in assets) {
            val childAsset = "$assetPath/$entry"
            val childTarget = File(targetDir, entry)
            val nested = context.assets.list(childAsset)
            if (!nested.isNullOrEmpty()) {
                childTarget.mkdirs()
                copyAssetDir(childAsset, childTarget)
            } else {
                if (!childTarget.exists()) {
                    context.assets.open(childAsset).use { input ->
                        childTarget.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}