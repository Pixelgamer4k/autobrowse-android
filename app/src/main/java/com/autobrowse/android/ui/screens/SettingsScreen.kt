package com.autobrowse.android.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.AppUiConfig
import com.autobrowse.android.domain.model.CaptchaConfig
import com.autobrowse.android.domain.model.CaptchaSolverProvider
import com.autobrowse.android.domain.model.FeedbackEntry
import com.autobrowse.android.domain.model.LearnedStrategy
import com.autobrowse.android.domain.model.MemoryEntry
import com.autobrowse.android.domain.model.SkillConfig
import com.autobrowse.android.domain.model.SkillType
import com.autobrowse.android.skills.SkillMetadata
import com.autobrowse.android.ui.FeedbackTransferState
import com.autobrowse.android.ui.SkillTransferState
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    skillConfigs: List<SkillConfig>,
    enabledSkills: Set<SkillType>,
    agentSkills: List<SkillMetadata>,
    memory: List<MemoryEntry>,
    strategies: List<LearnedStrategy>,
    skillTransfer: SkillTransferState,
    feedbackEntries: List<FeedbackEntry>,
    feedbackTransfer: FeedbackTransferState,
    onOpenLlmSetup: () -> Unit,
    onToggleSkill: (SkillType, Boolean) -> Unit,
    onBuildLearnedSkillsExport: suspend () -> Pair<String, Int>,
    onCreateLearnedSkillsShareUri: suspend (String) -> android.net.Uri,
    onBuildLearnedSkillsShareIntent: (android.net.Uri) -> Intent,
    onSaveLearnedSkillsExport: (android.net.Uri) -> Unit,
    onImportLearnedSkills: (android.net.Uri) -> Unit,
    onClearSkillTransferMessage: () -> Unit,
    onShowSkillTransfer: (String, Boolean) -> Unit,
    onBuildFeedbackExport: suspend () -> Pair<String, Int>,
    onCreateFeedbackShareUri: suspend (String) -> android.net.Uri,
    onBuildFeedbackShareIntent: (android.net.Uri) -> Intent,
    onSaveFeedbackExport: (android.net.Uri) -> Unit,
    onImportFeedback: (android.net.Uri) -> Unit,
    onClearFeedbackTransferMessage: () -> Unit,
    onShowFeedbackTransfer: (String, Boolean) -> Unit,
    onUpvoteFeedback: (String) -> Unit,
    onDownvoteFeedback: (String) -> Unit,
    onDeleteFeedback: (String) -> Unit,
    captchaConfig: CaptchaConfig,
    onCaptchaConfigChange: (CaptchaConfig) -> Unit,
    onSaveCaptchaConfig: () -> Unit,
    exportFileName: String,
    feedbackExportFileName: String,
    appUiConfig: AppUiConfig = AppUiConfig(),
    onResolutionScaleChange: (Float) -> Unit = {},
    onMaxAgentIterationsChange: (Float) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val saveExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            onSaveLearnedSkillsExport(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportLearnedSkills(uri)
        }
    }

    val saveFeedbackExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            onSaveFeedbackExport(uri)
        }
    }

    val importFeedbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportFeedback(uri)
        }
    }
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
            Text("Display", style = MaterialTheme.typography.titleMedium)
            Text(
                "Internal browser window resolution. Lower values use less memory; higher values look sharper. Open a new tab to apply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Resolution scale", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${"%.0f".format(appUiConfig.coercedScale() * 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = appUiConfig.coercedScale(),
                onValueChange = onResolutionScaleChange,
                valueRange = 0.75f..1.5f,
                steps = 14,
            )

            Text("Agent", style = MaterialTheme.typography.titleMedium)
            Text(
                "Maximum reasoning turns per message. Higher values allow longer tasks but use more time and tokens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Max turns", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${appUiConfig.coercedMaxIterations()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = appUiConfig.coercedMaxIterations().toFloat(),
                onValueChange = onMaxAgentIterationsChange,
                valueRange = 5f..50f,
                steps = 44,
            )

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
            Text(
                "Train skills by running tasks, then export and share the JSON file to bundle them into a future app release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            onClearSkillTransferMessage()
                            val (json, count) = onBuildLearnedSkillsExport()
                            if (count == 0) {
                                onShowSkillTransfer(
                                    "No learned skills to export yet. Run browsing tasks first.",
                                    false,
                                )
                                return@launch
                            }
                            val uri = onCreateLearnedSkillsShareUri(json)
                            val intent = onBuildLearnedSkillsShareIntent(uri)
                            context.startActivity(Intent.createChooser(intent, "Share learned skills"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share export")
                }
                OutlinedButton(
                    onClick = {
                        onClearSkillTransferMessage()
                        saveExportLauncher.launch(exportFileName)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save export")
                }
            }
            OutlinedButton(
                onClick = {
                    onClearSkillTransferMessage()
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import learned skills")
            }
            skillTransfer.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (skillTransfer.isSuccess == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
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

            Text("CAPTCHA Solver (authorized sites)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Auto-solve on domains you authorize. Uses CapSolver or 2Captcha. Android fingerprint stealth is enabled by default. " +
                    "Only add sites you own or have explicit permission to automate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enable solver")
                Switch(
                    checked = captchaConfig.enabled,
                    onCheckedChange = { onCaptchaConfigChange(captchaConfig.copy(enabled = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Android fingerprint")
                Switch(
                    checked = captchaConfig.useAndroidFingerprint,
                    onCheckedChange = { onCaptchaConfigChange(captchaConfig.copy(useAndroidFingerprint = it)) },
                )
            }
            OutlinedTextField(
                value = captchaConfig.apiKey,
                onValueChange = { onCaptchaConfigChange(captchaConfig.copy(apiKey = it)) },
                label = { Text("API key (CapSolver / 2Captcha)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = captchaConfig.authorizedDomains,
                onValueChange = { onCaptchaConfigChange(captchaConfig.copy(authorizedDomains = it)) },
                label = { Text("Authorized domains (comma-separated)") },
                placeholder = { Text("staging.myapp.com, amazon.com") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = captchaConfig.proxyUrl,
                onValueChange = { onCaptchaConfigChange(captchaConfig.copy(proxyUrl = it)) },
                label = { Text("Residential proxy URL (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onCaptchaConfigChange(captchaConfig.copy(provider = CaptchaSolverProvider.CAPSOLVER))
                }) {
                    Text(if (captchaConfig.provider == CaptchaSolverProvider.CAPSOLVER) "✓ CapSolver" else "CapSolver")
                }
                TextButton(onClick = {
                    onCaptchaConfigChange(captchaConfig.copy(provider = CaptchaSolverProvider.TWOCAPTCHA))
                }) {
                    Text(if (captchaConfig.provider == CaptchaSolverProvider.TWOCAPTCHA) "✓ 2Captcha" else "2Captcha")
                }
            }
            OutlinedButton(onClick = onSaveCaptchaConfig, modifier = Modifier.fillMaxWidth()) {
                Text("Save CAPTCHA settings")
            }

            Text("Training Feedback Mechanism", style = MaterialTheme.typography.titleMedium)
            Text(
                "Coach the agent in chat — purpose, sources, preferences. Mandatory entries (sources/purpose) inject into EVERY session. " +
                    "Upvote important entries. Say \"feedback:\" or coach naturally; also synced to long-term memory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            onClearFeedbackTransferMessage()
                            val (json, count) = onBuildFeedbackExport()
                            if (count == 0) {
                                onShowFeedbackTransfer(
                                    "No feedback to export yet. Coach the agent in chat first.",
                                    false,
                                )
                                return@launch
                            }
                            val uri = onCreateFeedbackShareUri(json)
                            val intent = onBuildFeedbackShareIntent(uri)
                            context.startActivity(Intent.createChooser(intent, "Share training feedback"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share export")
                }
                OutlinedButton(
                    onClick = {
                        onClearFeedbackTransferMessage()
                        saveFeedbackExportLauncher.launch(feedbackExportFileName)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save export")
                }
            }
            OutlinedButton(
                onClick = {
                    onClearFeedbackTransferMessage()
                    importFeedbackLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import feedback")
            }
            feedbackTransfer.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feedbackTransfer.isSuccess == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (feedbackEntries.isEmpty()) {
                Text(
                    "No feedback yet. Example: \"Feedback: your purpose is multi-window research. Use browser_search on Amazon, open 4 windows, compare ratings faster.\"",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Catalog (${feedbackEntries.size}) — sorted by priority",
                    style = MaterialTheme.typography.labelLarge,
                )
                feedbackEntries.forEach { entry ->
                    FeedbackRow(
                        entry = entry,
                        onUpvote = { onUpvoteFeedback(entry.id) },
                        onDownvote = { onDownvoteFeedback(entry.id) },
                        onDelete = { onDeleteFeedback(entry.id) },
                    )
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
private fun FeedbackRow(
    entry: FeedbackEntry,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "[${entry.category}] priority ${entry.priorityScore}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onUpvote) { Text("▲ ${entry.upvotes}") }
                TextButton(onClick = onDownvote) { Text("▼ ${entry.downvotes}") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
        Text(
            entry.content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (entry.source.isNotBlank() || entry.tags.isNotBlank()) {
            Text(
                "${entry.source}${if (entry.tags.isNotBlank()) " · ${entry.tags}" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
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