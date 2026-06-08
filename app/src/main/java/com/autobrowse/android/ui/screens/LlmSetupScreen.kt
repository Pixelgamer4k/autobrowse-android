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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.autobrowse.android.ui.LlmConnectionTestState
import com.autobrowse.android.ui.LocalModelBusyState
import com.autobrowse.android.ui.ModelDownloadState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LlmSetupScreen(
    llmConfig: LlmConfig,
    connectionTest: LlmConnectionTestState,
    onTestConnection: (LlmConfig) -> Unit,
    onSave: (LlmConfig) -> Unit,
    onImportModel: (Uri, LocalLlmModel) -> Unit,
    onDownloadModel: (LocalLlmModel) -> Unit,
    onDeleteModel: (LocalLlmModel) -> Unit,
    onCancelModelDownload: () -> Unit,
    modelDownload: ModelDownloadState,
    localModelBusy: LocalModelBusyState,
    downloadedModels: Set<LocalLlmModel>,
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

    LaunchedEffect(llmConfig.localModel, llmConfig.localModelPath, llmConfig.maxTokens) {
        localModel = llmConfig.localModel
        localModelPath = llmConfig.localModelPath
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

    val isLocalBusy = localModelBusy.isBusy ||
        modelDownload.isDownloading ||
        (connectionTest.isTesting && provider == LlmProvider.LOCAL)

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
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = provider == LlmProvider.REMOTE,
                    onClick = { provider = LlmProvider.REMOTE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Cloud API", style = MaterialTheme.typography.labelLarge)
                }
                SegmentedButton(
                    selected = provider == LlmProvider.LOCAL,
                    onClick = { provider = LlmProvider.LOCAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Local", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (provider) {
                LlmProvider.REMOTE -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "OpenAI-compatible endpoint — credentials stored encrypted on device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        RemoteLlmSection(
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
                    }
                }
                LlmProvider.LOCAL -> {
                    LocalLlmSection(
                        modifier = Modifier.weight(1f),
                        localModel = localModel,
                        backend = backend,
                        localModelPath = localModelPath,
                        contextTokens = contextTokens,
                        deviceRamGb = deviceRamGb,
                        modelDownload = modelDownload,
                        localModelBusy = localModelBusy,
                        downloadedModels = downloadedModels,
                        onLocalModelChange = { model ->
                            localModel = model
                            localModelPath = if (downloadedModels.contains(model)) {
                                File(appContext.filesDir, "models/${LocalLlmCatalog.infoFor(model).modelFileName}")
                                    .takeIf { it.isFile }
                                    ?.absolutePath
                                    .orEmpty()
                            } else {
                                ""
                            }
                            contextTokens = DeviceContextDefaults.defaultContextTokens(appContext, model)
                        },
                        onBackendChange = { backend = it },
                        onContextTokensChange = { contextTokens = it },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onDownload = { model ->
                            onDownloadModel(model)
                            localModelPath = ""
                        },
                        onDelete = onDeleteModel,
                        onCancelDownload = onCancelModelDownload,
                        onOpenUrl = onOpenUrl,
                        onPathReady = { path -> localModelPath = path },
                    )
                }
            }

            if (provider == LlmProvider.LOCAL && isLocalBusy) {
                LocalBusyBanner(
                    message = when {
                        localModelBusy.isBusy -> localModelBusy.message
                        modelDownload.isDownloading -> "Downloading ${modelDownload.model?.name ?: "model"}…"
                        else -> connectionTest.message
                    } ?: "Loading model…",
                    showProgress = modelDownload.isDownloading,
                    downloadProgress = modelDownload.progress.percent,
                )
            }

            connectionTest.message?.takeIf {
                provider == LlmProvider.REMOTE || !isLocalBusy
            }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (connectionTest.isSuccess) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onTestConnection(currentConfig()) },
                    enabled = canSubmit && !connectionTest.isTesting && !isLocalBusy,
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
                    enabled = canSubmit && !isLocalBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (onBack == null) "Continue" else "Save")
                }
            }
        }
    }
}

@Composable
private fun LocalBusyBanner(
    message: String,
    showProgress: Boolean,
    downloadProgress: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (showProgress && downloadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalLlmSection(
    modifier: Modifier = Modifier,
    localModel: LocalLlmModel,
    backend: LlmBackend,
    localModelPath: String,
    contextTokens: Int,
    deviceRamGb: Int,
    modelDownload: ModelDownloadState,
    localModelBusy: LocalModelBusyState,
    downloadedModels: Set<LocalLlmModel>,
    onLocalModelChange: (LocalLlmModel) -> Unit,
    onBackendChange: (LlmBackend) -> Unit,
    onContextTokensChange: (Int) -> Unit,
    onImport: () -> Unit,
    onDownload: (LocalLlmModel) -> Unit,
    onDelete: (LocalLlmModel) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onPathReady: (String) -> Unit,
) {
    val selectedInfo = LocalLlmCatalog.infoFor(localModel)
    val recommended = DeviceContextDefaults.defaultContextTokens(deviceRamGb, localModel)
    val bounds = LocalLlmCatalog.contextBounds(localModel)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "On-device Gemma 4 · experimental · cloud API recommended",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 14.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LocalLlmCatalog.models.forEach { info ->
                CompactModelCard(
                    info = info,
                    selected = localModel == info.model,
                    downloaded = downloadedModels.contains(info.model),
                    isDownloading = modelDownload.isDownloading && modelDownload.model == info.model,
                    modifier = Modifier.weight(1f),
                    onSelect = { onLocalModelChange(info.model) },
                    onDownload = {
                        onDownload(info.model)
                        onPathReady("")
                    },
                    onDelete = { onDelete(info.model) },
                    onOpenPage = { onOpenUrl(info.pageUrl) },
                )
            }
        }

        if (modelDownload.isDownloading) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Context", style = MaterialTheme.typography.labelMedium)
            Text(
                "${contextTokens / 1024}K",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = (contextTokens / 1024f).coerceIn(
                bounds.first / 1024f,
                bounds.last / 1024f,
            ),
            onValueChange = { valueK ->
                onContextTokensChange(
                    LocalLlmCatalog.coerceContextTokens(
                        localModel,
                        valueK.roundToInt() * 1024,
                    ),
                )
            },
            valueRange = (bounds.first / 1024f)..(bounds.last / 1024f),
            steps = (((bounds.last - bounds.first) / LocalLlmCatalog.CONTEXT_STEP_TOKENS) - 1)
                .coerceAtLeast(0),
        )
        Text(
            "Recommended ${recommended / 1024}K · ${deviceRamGb}GB RAM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Backend", style = MaterialTheme.typography.labelMedium)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onImport,
                enabled = !localModelBusy.isBusy && !modelDownload.isDownloading,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Import", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
            if (downloadedModels.contains(localModel)) {
                OutlinedButton(
                    onClick = { onDelete(localModel) },
                    enabled = !localModelBusy.isBusy && !modelDownload.isDownloading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Delete", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val statusText = when {
            localModelPath.isNotBlank() -> {
                "Ready · ${selectedInfo.displayName} · ${contextTokens / 1024}K"
            }
            downloadedModels.contains(localModel) -> {
                "Downloaded · tap Test to load"
            }
            else -> "No model for ${selectedInfo.displayName}"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = if (localModelPath.isNotBlank()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactModelCard(
    info: LocalLlmModelInfo,
    selected: Boolean,
    downloaded: Boolean,
    isDownloading: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        modifier = modifier.clickable(onClick = onSelect),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = info.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = info.sizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = info.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (downloaded) {
                Text(
                    text = "On device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (downloaded) {
                    IconButton(
                        onClick = onDelete,
                        enabled = !isDownloading,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${info.displayName}",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(
                    onClick = onDownload,
                    enabled = !isDownloading,
                    modifier = Modifier.size(28.dp),
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download ${info.displayName}",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onOpenPage,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "Open on Hugging Face",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}