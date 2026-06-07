package com.autobrowse.android.agent.tools

import com.autobrowse.android.skills.SkillStore

class SkillsListTool(private val skillStore: SkillStore) : AgentTool {
    override val name = "skills_list"
    override val description = "List available Autobrowse skills (metadata only). Use skill_view to load full instructions."
    override val parametersJson = """{"type":"object","properties":{"category":{"type":"string"}}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val category = args["category"]?.toString()
        val skills = skillStore.listSkills().filter { category.isNullOrBlank() || it.category == category }
        val lines = skills.joinToString("\n") { "- ${it.name} [${it.category}]: ${it.description}" }
        return ToolExecutionResult(lines.ifBlank { "No skills found." })
    }
}

class SkillViewTool(private val skillStore: SkillStore) : AgentTool {
    override val name = "skill_view"
    override val description = "Load a skill's SKILL.md or bundled reference/script file."
    override val parametersJson = """
        {"type":"object","properties":{"name":{"type":"string"},"file_path":{"type":"string"}},"required":["name"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val name = args["name"]?.toString().orEmpty()
        val filePath = args["file_path"]?.toString()
        val content = skillStore.readSkill(name, filePath)
        return ToolExecutionResult(content.take(12000))
    }
}

class SkillManageTool(private val skillStore: SkillStore) : AgentTool {
    override val name = "skill_manage"
    override val description = "Create, patch, write files, or delete Autobrowse skills (procedural memory)."
    override val parametersJson = """
        {"type":"object","properties":{
            "action":{"type":"string","enum":["create","patch","write_file","delete"]},
            "name":{"type":"string"},
            "description":{"type":"string"},
            "body":{"type":"string"},
            "find":{"type":"string"},
            "replace":{"type":"string"},
            "file_path":{"type":"string"},
            "content":{"type":"string"},
            "category":{"type":"string"}
        },"required":["action","name"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val action = args["action"]?.toString().orEmpty()
        val name = args["name"]?.toString().orEmpty()
        return when (action) {
            "create" -> {
                val description = args["description"]?.toString().orEmpty()
                val body = args["body"]?.toString().orEmpty()
                val category = args["category"]?.toString() ?: "user"
                val path = skillStore.createSkill(name, description, body, category)
                ToolExecutionResult("Created skill at $path")
            }
            "patch" -> {
                val find = args["find"]?.toString().orEmpty()
                val replace = args["replace"]?.toString().orEmpty()
                ToolExecutionResult(skillStore.patchSkill(name, find, replace))
            }
            "write_file" -> {
                val filePath = args["file_path"]?.toString().orEmpty()
                val content = args["content"]?.toString().orEmpty()
                ToolExecutionResult(skillStore.writeSkillFile(name, filePath, content))
            }
            "delete" -> {
                val deleted = skillStore.deleteSkill(name)
                ToolExecutionResult(if (deleted) "Deleted skill $name" else "Skill not found", success = deleted)
            }
            else -> ToolExecutionResult("Unknown action: $action", success = false)
        }
    }
}

class SkillCreatorTool(
    private val skillStore: SkillStore,
    private val llmApi: com.autobrowse.android.data.remote.LlmApiService,
    private val repository: com.autobrowse.android.data.repository.AutobrowseRepository,
) : AgentTool {
    override val name = "skill_creator"
    override val description = "Analyze a completed workflow and draft an optimized Autobrowse skill (Hermes-style skill creator)."
    override val parametersJson = """
        {"type":"object","properties":{
            "task_summary":{"type":"string"},
            "steps_that_worked":{"type":"string"},
            "pitfalls":{"type":"string"},
            "skill_name":{"type":"string"}
        },"required":["task_summary","skill_name"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val taskSummary = args["task_summary"]?.toString().orEmpty()
        val steps = args["steps_that_worked"]?.toString().orEmpty()
        val pitfalls = args["pitfalls"]?.toString().orEmpty()
        val skillName = args["skill_name"]?.toString()?.lowercase()?.replace(Regex("[^a-z0-9-]"), "-").orEmpty()
        val config = repository.getLlmConfig()
        val draft = llmApi.chat(
            config = config,
            systemPrompt = """
                You write Autobrowse Android agent skills in Markdown.
                Format: ## When to use, ## Workflow (numbered steps with tool names), ## Pitfalls, ## Verification.
                Optimize for browser automation with refs, tabs, vision, and skill_manage.
            """.trimIndent(),
            userPrompt = "Task: $taskSummary\nSteps: $steps\nPitfalls: $pitfalls",
        )
        val path = skillStore.createSkill(
            name = skillName,
            description = "Autobrowse skill for: ${taskSummary.take(120)}",
            body = draft,
            category = "agent-created",
        )
        context.extractedData["created_skill"] = skillName
        return ToolExecutionResult("Created skill '$skillName' at $path\n\n$draft")
    }
}