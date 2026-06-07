package com.autobrowse.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.SkillConfig
import com.autobrowse.android.domain.model.SkillType
import com.autobrowse.android.skills.SkillMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    skillConfigs: List<SkillConfig>,
    enabledSkills: Set<SkillType>,
    agentSkills: List<SkillMetadata>,
    memory: List<MemoryEntry>,
    strategies: List<LearnedStrategy>,
    onOpenLlmSetup: () -> Unit,
    onToggleSkill: (SkillType, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("API", style = MaterialTheme.typography.titleMedium)
            Text(
                "Cloud API is recommended. Local on-device models are experimental and can take 6–10 minutes per response.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            OutlinedButton(
                onClick = onOpenLlmSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change API Configuration")
            }

            Text("Tool Skills", style = MaterialTheme.typography.titleMedium)
            Text(
                "Built-in agent capabilities (summarize, extract, web fetch, etc.).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            skillConfigs.forEach { skill ->
                SkillToggleRow(
                    skill = skill,
                    enabled = skill.type in enabledSkills,
                    onToggle = { onToggleSkill(skill.type, it) },
                )
            }

            Text("Agent Skills", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bundled playbooks plus skills auto-created after each automation run. Matched tasks load these into the agent prompt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (agentSkills.isEmpty()) {
                Text(
                    "No skills loaded yet. Run a browsing task to seed bundled skills and start learning.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val learned = agentSkills.filter { it.category == "learned" }
                val bundled = agentSkills.filter { it.category != "learned" }
                if (learned.isNotEmpty()) {
                    Text("Learned (${learned.size})", style = MaterialTheme.typography.labelLarge)
                    learned.forEach { skill -> AgentSkillRow(skill) }
                }
                if (bundled.isNotEmpty()) {
                    Text("Bundled (${bundled.size})", style = MaterialTheme.typography.labelLarge)
                    bundled.forEach { skill -> AgentSkillRow(skill) }
                }
            }

            Text("Long-term Memory", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hermes-style persistent memory with FTS search. Extracted automatically after each turn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (memory.isEmpty()) {
                Text(
                    "No memories yet. Say \"remember that I prefer concise summaries\" or let the agent learn from tasks.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                memory.take(12).forEach { entry ->
                    Text(
                        "[${entry.category}] ${entry.key}: ${entry.value.take(80)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            Text("Self-Improved Strategies", style = MaterialTheme.typography.titleMedium)
            Text(
                "Heuristics learned from past trajectories. Injected into the agent prompt automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (strategies.isEmpty()) {
                Text(
                    "No strategies yet. Complete browsing tasks and the agent will reflect and improve.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                strategies.take(8).forEach { strategy ->
                    Text(
                        "[${strategy.domain}] ${strategy.heuristic} (${(strategy.confidence * 100).toInt()}% confidence)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentSkillRow(skill: SkillMetadata) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(skill.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            skill.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (skill.category == "learned" && skill.learnedRuns > 0) {
            Text(
                "Runs learned: ${skill.learnedRuns}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SkillToggleRow(
    skill: SkillConfig,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(skill.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}