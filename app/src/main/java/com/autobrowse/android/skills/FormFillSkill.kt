package com.autobrowse.android.skills

import com.autobrowse.android.domain.model.AgentAction
import com.autobrowse.android.domain.model.SkillType
class FormFillSkill : Skill {
    override val type = SkillType.FORM_FILL
    override val displayName = "Form Fill"
    override val description = "Detect form fields and generate fill actions for automated submission."

    override suspend fun execute(context: SkillContext): SkillResult {
        val html = context.pageHtml.orEmpty()
        if (html.isBlank()) {
            return SkillResult(success = false, output = "", error = "No HTML available for form detection.")
        }

        val fields = detectFormFields(html)
        if (fields.isEmpty()) {
            return SkillResult(
                success = false,
                output = "No form fields detected on the current page.",
                error = "No forms found.",
            )
        }

        val actions = fields.map { field ->
            AgentAction(
                type = "fill",
                target = field.selector,
                value = inferValue(field, context),
                reasoning = "Auto-fill detected ${field.type} field: ${field.name}",
            )
        }

        return SkillResult(
            success = true,
            output = "Detected ${fields.size} form fields. Ready to fill.",
            extractedData = fields.associate { it.name to it.type },
            actions = actions,
        )
    }

    private fun detectFormFields(html: String): List<FormField> {
        val inputRegex = Regex("""<input[^>]*>""", RegexOption.IGNORE_CASE)
        return inputRegex.findAll(html).mapNotNull { match ->
            val tag = match.value
            val type = extractAttr(tag, "type") ?: "text"
            val name = extractAttr(tag, "name") ?: extractAttr(tag, "id") ?: return@mapNotNull null
            val id = extractAttr(tag, "id")
            FormField(
                name = name,
                type = type,
                selector = if (id != null) "#$id" else "[name='$name']",
            )
        }.distinctBy { it.name }.take(20).toList()
    }

    private fun extractAttr(tag: String, attr: String): String? {
        val regex = Regex("""$attr=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return regex.find(tag)?.groupValues?.getOrNull(1)
    }

    private fun inferValue(field: FormField, context: SkillContext): String {
        val memoryValue = context.memoryContext.lines()
            .firstOrNull { it.contains(field.name, ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
        if (!memoryValue.isNullOrBlank()) return memoryValue

        return when (field.type.lowercase()) {
            "email" -> "user@example.com"
            "search" -> context.userPrompt.take(80)
            else -> ""
        }
    }

    private data class FormField(
        val name: String,
        val type: String,
        val selector: String,
    )
}