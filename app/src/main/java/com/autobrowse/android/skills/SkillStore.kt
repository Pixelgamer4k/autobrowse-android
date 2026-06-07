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
        val category = file.parentFile?.parentFile?.name ?: "user"
        return SkillMetadata(
            name = name,
            description = description,
            path = file.parentFile?.absolutePath.orEmpty(),
            category = category,
            version = version,
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