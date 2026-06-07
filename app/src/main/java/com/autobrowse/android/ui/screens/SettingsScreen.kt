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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.SkillConfig
import com.autobrowse.android.domain.model.SkillType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    llmConfig: LlmConfig,
    skillConfigs: List<SkillConfig>,
    enabledSkills: Set<SkillType>,
    memory: List<MemoryEntry>,
    strategies: List<LearnedStrategy>,
    onSaveLlmConfig: (LlmConfig) -> Unit,
    onToggleSkill: (SkillType, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var apiKey by remember(llmConfig) { mutableStateOf(llmConfig.apiKey) }
    var apiUrl by remember(llmConfig) { mutableStateOf(llmConfig.apiUrl) }
    var modelId by remember(llmConfig) { mutableStateOf(llmConfig.modelId) }

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
            Text("LLM Integration", style = MaterialTheme.typography.titleMedium)
            Text(
                "OpenAI-compatible endpoint. API key is stored encrypted on device. Use vision-capable models (e.g. gpt-4o) for image, PDF, and video attachments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.openai.com/v1/") },
            )
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("gpt-4o-mini") },
            )

            Button(
                onClick = {
                    onSaveLlmConfig(
                        llmConfig.copy(
                            apiKey = apiKey,
                            apiUrl = apiUrl,
                            modelId = modelId,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Configuration")
            }

            Text("Skills", style = MaterialTheme.typography.titleMedium)
            skillConfigs.forEach { skill ->
                SkillToggleRow(
                    skill = skill,
                    enabled = skill.type in enabledSkills,
                    onToggle = { onToggleSkill(skill.type, it) },
                )
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