package com.autobrowse.android.skills

import android.content.Context
import com.autobrowse.android.data.remote.LlmApiService
import com.autobrowse.android.data.repository.AutobrowseRepository
import com.autobrowse.android.domain.model.SkillType

class SkillRegistry(
    context: Context,
    repository: AutobrowseRepository,
    llmApi: LlmApiService,
) {
    private val allSkills: Map<SkillType, Skill> = mapOf(
        SkillType.WEB_REQUEST to WebRequestSkill(),
        SkillType.DATA_EXTRACTION to DataExtractionSkill(llmApi, repository),
        SkillType.FORM_FILL to FormFillSkill(),
        SkillType.SUMMARIZE to SummarizeSkill(llmApi, repository),
        SkillType.BACKGROUND_TASK to BackgroundTaskSkill(context.applicationContext),
    )

    fun getEnabledSkills(enabledTypes: Set<SkillType>): List<Skill> =
        enabledTypes.mapNotNull { allSkills[it] }

    fun allSkillConfigs() = allSkills.values.map { skill ->
        com.autobrowse.android.domain.model.SkillConfig(
            type = skill.type,
            enabled = true,
            displayName = skill.displayName,
            description = skill.description,
        )
    }
}