package com.autobrowse.android.ui.screens

import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.autobrowse.android.domain.model.DeviceContextDefaults
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LocalLlmModelInfo

import com.autobrowse.android.domain.model.LlmBackend
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.LlmProvider
import com.autobrowse.android.domain.model.LocalLlmModel
import com.autobrowse.android.domain.model.ToolCallingLevel
import com.autobrowse.android.domain.model.VisionLevel
import com.autobrowse.android.ui.LlmConnectionTestState
import com.autobrowse.android.ui.ModelDownloadState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LlmSetupScreen(
    llmConfig: LlmConfig,
    connectionTest: LlmConnectionTestState,
    onTestConnection: (LlmConfig) -> Unit,
    onSave: (LlmConfig) -> Unit,
    onImportModel: (Uri, LocalLlmModel) -> Unit,
    onDownloadModel: (LocalLlmModel) -> Unit,
    onCancelModelDownload: () -> Unit,
    modelDownload: ModelDownloadState,
    onOpenUrl: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var provider by remember(llmConfig) { mutableStateOf(llmConfig.provider) }
    var apiKey by remember(llmConfig) { mutableStateOf(llmConfig.apiKey) }
    var apiUrl by remember(llmConfig) { mutableStateOf(llmConfig.apiUrl) }
    var modelId by remember(llmConfig) { mutableStateOf(llmConfig.modelId) }
    var localModel by remember(llmConfig) { mutableStateOf(llmConfig.localModel) }
    var backend by remember(llmConfig) { mutableStateOf(llmConfig.backend) }
    var localModelPath by remember(llmConfig) { mutableStateOf(llmConfig.localModelPath) }
    var contextTokens by remember(llmConfig) {
        mutableStateOf(
            LocalLlmCatalog.coerceContextTokens(llmConfig.localModel, llmConfig.maxTokens),
        )
    }
    val appContext = LocalContext.current
    val deviceRamGb = remember { DeviceContextDefaults.totalRamGb(appContext) }

    LaunchedEffect(llmConfig.localModelPath) {
        if (llmConfig.localModelPath.isNotBlank()) {
            localModelPath = llmConfig.localModelPath
        }
    }

    LaunchedEffect(llmConfig.localModel, llmConfig.maxTokens) {
        contextTokens = LocalLlmCatalog.coerceContextTokens(llmConfig.localModel, llmConfig.maxTokens)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportModel(uri, localModel)
        }
    }

    fun currentConfig() = llmConfig.copy(
        provider = provider,
        apiKey = apiKey.trim(),
        apiUrl = apiUrl.trim(),
        modelId = modelId.trim(),
        localModel = localModel,
        backend = backend,
        localModelPath = localModelPath,
        maxTokens = LocalLlmCatalog.coerceContextTokens(localModel, contextTokens),
    )

    val canSubmit = when (provider) {
        LlmProvider.REMOTE ->
            apiKey.isNotBlank() && apiUrl.isNotBlank() && modelId.isNotBlank()
        LlmProvider.LOCAL ->
            localModelPath.isNotBlank()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Setup") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val apiKeyBringIntoView = remember { BringIntoViewRequester() }
        val apiUrlBringIntoView = remember { BringIntoViewRequester() }
        val modelIdBringIntoView = remember { BringIntoViewRequester() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Cloud API is recommended for fast, reliable agent runs. Local on-device models are experimental.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = provider == LlmProvider.REMOTE,
                    onClick = { provider = LlmProvider.REMOTE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Cloud API (recommended)")
                }
                SegmentedButton(
                    selected = provider == LlmProvider.LOCAL,
                    onClick = { provider = LlmProvider.LOCAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Local (experimental)")
                }
            }

            when (provider) {
                LlmProvider.REMOTE -> RemoteLlmSection(
                    apiKey = apiKey,
                    apiUrl = apiUrl,
                    modelId = modelId,
                    connectionTest = connectionTest,
                    canSubmit = canSubmit,
                    onApiKeyChange = { apiKey = it },
                    onApiUrlChange = { apiUrl = it },
                    onModelIdChange = { modelId = it },
                    onTest = { onTestConnection(currentConfig()) },
                    apiKeyBringIntoView = apiKeyBringIntoView,
                    apiUrlBringIntoView = apiUrlBringIntoView,
                    modelIdBringIntoView = modelIdBringIntoView,
                    onFocused = { requester ->
                        scope.launch { requester.bringIntoView() }
                    },
                )
                LlmProvider.LOCAL -> LocalLlmSection(
                    localModel = localModel,
                    backend = backend,
                    localModelPath = localModelPath,
                    contextTokens = contextTokens,
                    deviceRamGb = deviceRamGb,
                    modelDownload = modelDownload,
                    onLocalModelChange = {
                        localModel = it
                        localModelPath = ""
                        contextTokens = DeviceContextDefaults.defaultContextTokens(appContext, it)
                    },
                    onBackendChange = { backend = it },
                    onContextTokensChange = { contextTokens = it },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onDownload = onDownloadModel,
                    onCancelDownload = onCancelModelDownload,
                    onOpenUrl = onOpenUrl,
                    onPathReady = { localModelPath = "" },
                )
            }

            connectionTest.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (connectionTest.isSuccess) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onTestConnection(currentConfig()) },
                    enabled = canSubmit && !connectionTest.isTesting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (connectionTest.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text("Test", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = { onSave(currentConfig()) },
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (onBack == null) "Continue" else "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RemoteLlmSection(
    apiKey: String,
    apiUrl: String,
    modelId: String,
    connectionTest: LlmConnectionTestState,
    canSubmit: Boolean,
    onApiKeyChange: (String) -> Unit,
    onApiUrlChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onTest: () -> Unit,
    apiKeyBringIntoView: BringIntoViewRequester,
    apiUrlBringIntoView: BringIntoViewRequester,
    modelIdBringIntoView: BringIntoViewRequester,
    onFocused: (BringIntoViewRequester) -> Unit,
) {
    Text(
        "OpenAI-compatible endpoint — best experience on mobile. Credentials are stored encrypted on device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("gpt-4o-mini", "gpt-4o", "claude-sonnet-4").forEach { preset ->
            FilterChip(
                selected = modelId == preset,
                onClick = { onModelIdChange(preset) },
                label = { Text(preset) },
            )
        }
    }
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("API Token") },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(apiKeyBringIntoView)
            .onFocusEvent { if (it.isFocused) onFocused(apiKeyBringIntoView) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
    OutlinedTextField(
        value = apiUrl,
        onValueChange = onApiUrlChange,
        label = { Text("API URL") },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(apiUrlBringIntoView)
            .onFocusEvent { if (it.isFocused) onFocused(apiUrlBringIntoView) },
        singleLine = true,
        placeholder = { Text("https://api.openai.com/v1/") },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = modelId,
            onValueChange = onModelIdChange,
            label = { Text("Model ID") },
            modifier = Modifier
                .weight(1f)
                .bringIntoViewRequester(modelIdBringIntoView)
                .onFocusEvent { if (it.isFocused) onFocused(modelIdBringIntoView) },
            singleLine = true,
            placeholder = { Text("gpt-4o-mini") },
        )
        IconButton(
            onClick = onTest,
            enabled = canSubmit && !connectionTest.isTesting,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Test connection")
        }
    }
}

private enum class ModelCatalogTab { OFFICIAL, COMMUNITY }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalLlmSection(
    localModel: LocalLlmModel,
    backend: LlmBackend,
    localModelPath: String,
    contextTokens: Int,
    deviceRamGb: Int,
    modelDownload: ModelDownloadState,
    onLocalModelChange: (LocalLlmModel) -> Unit,
    onBackendChange: (LlmBackend) -> Unit,
    onContextTokensChange: (Int) -> Unit,
    onImport: () -> Unit,
    onDownload: (LocalLlmModel) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onPathReady: (String) -> Unit,
) {
    val selectedInfo = LocalLlmCatalog.infoFor(localModel)
    var catalogTab by remember(localModel) {
        mutableStateOf(
            if (selectedInfo.isCommunity) ModelCatalogTab.COMMUNITY else ModelCatalogTab.OFFICIAL,
        )
    }
    val visibleModels = when (catalogTab) {
        ModelCatalogTab.OFFICIAL -> LocalLlmCatalog.officialModels
        ModelCatalogTab.COMMUNITY -> LocalLlmCatalog.communityModels
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CatalogHintLine("Experimental · ~6–10 min/response · cloud API recommended")
        CatalogHintLine("Needs tool calling · vision recommended · single .litertlm file")

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = catalogTab == ModelCatalogTab.OFFICIAL,
                onClick = { catalogTab = ModelCatalogTab.OFFICIAL },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Official", style = MaterialTheme.typography.labelLarge)
            }
            SegmentedButton(
                selected = catalogTab == ModelCatalogTab.COMMUNITY,
                onClick = { catalogTab = ModelCatalogTab.COMMUNITY },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Community", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (catalogTab == ModelCatalogTab.COMMUNITY) {
            CatalogHintLine(
                text = "Third-party builds — unverified weights, safety, and tool behavior.",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (modelDownload.isDownloading) {
            DownloadProgressBlock(
                modelDownload = modelDownload,
                onCancelDownload = onCancelDownload,
            )
        }

        visibleModels.forEach { info ->
            LocalModelCard(
                info = info,
                selected = localModel == info.model,
                isDownloading = modelDownload.isDownloading && modelDownload.model == info.model,
                onSelect = { onLocalModelChange(info.model) },
                onDownload = {
                    onDownload(info.model)
                    onPathReady("")
                },
                onOpenPage = { onOpenUrl(info.pageUrl) },
            )
        }

        SelectedModelNote(info = selectedInfo)

        ContextWindowControl(
            model = localModel,
            contextTokens = contextTokens,
            deviceRamGb = deviceRamGb,
            onContextTokensChange = onContextTokensChange,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Backend", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LlmBackend.entries.forEach { option ->
                    FilterChip(
                        selected = backend == option,
                        onClick = { onBackendChange(option) },
                        label = { Text(option.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
        CatalogHintLine(
            when (backend) {
                LlmBackend.CPU -> "CPU — widest compatibility"
                LlmBackend.GPU -> "GPU — fastest (recommended)"
            },
        )

        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Import .litertlm", modifier = Modifier.padding(start = 8.dp))
        }

        val statusColor = if (localModelPath.isNotBlank()) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        val statusText = when {
            localModelPath.isNotBlank() -> {
                val ctx = contextTokens / 1024
                "Ready · ${localModelPath.substringAfterLast('/')} · ${ctx}K context"
            }
            else -> "No model downloaded for ${selectedInfo.displayName}"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContextWindowControl(
    model: LocalLlmModel,
    contextTokens: Int,
    deviceRamGb: Int,
    onContextTokensChange: (Int) -> Unit,
) {
    val bounds = LocalLlmCatalog.contextBounds(model)
    val recommended = DeviceContextDefaults.defaultContextTokens(deviceRamGb, model)
    val minK = bounds.first / 1024f
    val maxK = bounds.last / 1024f
    val steps = ((bounds.last - bounds.first) / LocalLlmCatalog.CONTEXT_STEP_TOKENS) - 1

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Context window", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "${contextTokens / 1024}K",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = (contextTokens / 1024f).coerceIn(minK, maxK),
            onValueChange = { valueK ->
                onContextTokensChange(
                    LocalLlmCatalog.coerceContextTokens(
                        model,
                        (valueK.roundToInt()) * 1024,
                    ),
                )
            },
            valueRange = minK..maxK,
            steps = steps.coerceAtLeast(0),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${bounds.first / 1024}K",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${bounds.last / 1024}K",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CatalogHintLine(
            text = "Recommended ${recommended / 1024}K for ${deviceRamGb}GB RAM · up to ${bounds.last / 1024}K",
        )
        TextButton(
            onClick = { onContextTokensChange(recommended) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Use recommended", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CatalogHintLine(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        lineHeight = 14.sp,
    )
}

@Composable
private fun DownloadProgressBlock(
    modelDownload: ModelDownloadState,
    onCancelDownload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Downloading ${modelDownload.model?.name ?: "model"}…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (modelDownload.progress.percent > 0f) {
            LinearProgressIndicator(
                progress = { modelDownload.progress.percent },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modelDownload.progress.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancelDownload) {
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SelectedModelNote(info: LocalLlmModelInfo) {
    val notes = buildList {
        if (info.isCommunity) add("Community")
        if (info.vision == VisionLevel.NONE) add("No vision")
        when (info.toolCalling) {
            ToolCallingLevel.UNVERIFIED -> add("Tools unverified")
            ToolCallingLevel.NONE -> add("No tools")
            ToolCallingLevel.SPECIALIST -> add("Text-only specialist")
            ToolCallingLevel.NATIVE -> Unit
        }
    }
    if (notes.isEmpty()) return

    Text(
        text = "${info.displayName} · ${notes.joinToString(" · ")}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LocalModelCard(
    info: LocalLlmModelInfo,
    selected: Boolean,
    isDownloading: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = info.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = info.sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CapabilityBadges(info = info)
            }
            IconButton(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier.size(36.dp),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download ${info.displayName}",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(
                onClick = onOpenPage,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = "Open on Hugging Face",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadges(info: LocalLlmModelInfo) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CapabilityBadge(
            label = when (info.toolCalling) {
                ToolCallingLevel.NATIVE -> "Tools"
                ToolCallingLevel.SPECIALIST -> "Tools+"
                ToolCallingLevel.UNVERIFIED -> "Tools?"
                ToolCallingLevel.NONE -> "No tools"
            },
            tint = when (info.toolCalling) {
                ToolCallingLevel.NATIVE, ToolCallingLevel.SPECIALIST -> MaterialTheme.colorScheme.primary
                ToolCallingLevel.UNVERIFIED -> MaterialTheme.colorScheme.tertiary
                ToolCallingLevel.NONE -> MaterialTheme.colorScheme.error
            },
        )
        CapabilityBadge(
            label = if (info.vision == VisionLevel.MULTIMODAL) "Vision" else "Text only",
            tint = if (info.vision == VisionLevel.MULTIMODAL) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun CapabilityBadge(
    label: String,
    tint: Color,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}