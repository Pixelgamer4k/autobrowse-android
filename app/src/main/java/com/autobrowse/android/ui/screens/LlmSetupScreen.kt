package com.autobrowse.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LlmBackend
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.LlmProvider
import com.autobrowse.android.domain.model.LocalLlmModel
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
    var backend by remember(llmConfig) {
        mutableStateOf(if (llmConfig.backend == LlmBackend.NPU) LlmBackend.CPU else llmConfig.backend)
    }
    var localModelPath by remember(llmConfig) { mutableStateOf(llmConfig.localModelPath) }
    var localMmprojPath by remember(llmConfig) { mutableStateOf(llmConfig.localMmprojPath) }

    LaunchedEffect(llmConfig.localModelPath, llmConfig.localMmprojPath) {
        if (llmConfig.localModelPath.isNotBlank()) {
            localModelPath = llmConfig.localModelPath
        }
        if (llmConfig.localMmprojPath.isNotBlank()) {
            localMmprojPath = llmConfig.localMmprojPath
        }
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
        localMmprojPath = localMmprojPath,
    )

    val canSubmit = when (provider) {
        LlmProvider.REMOTE ->
            apiKey.isNotBlank() && apiUrl.isNotBlank() && modelId.isNotBlank()
        LlmProvider.LOCAL ->
            localModelPath.isNotBlank() && localMmprojPath.isNotBlank()
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
                    localMmprojPath = localMmprojPath,
                    modelDownload = modelDownload,
                    onLocalModelChange = {
                        localModel = it
                        localModelPath = ""
                        localMmprojPath = ""
                    },
                    onBackendChange = { backend = it },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onDownload = onDownloadModel,
                    onCancelDownload = onCancelModelDownload,
                    onOpenUrl = onOpenUrl,
                    onPathsReady = { modelPath, mmprojPath ->
                        localModelPath = modelPath
                        localMmprojPath = mmprojPath
                    },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalLlmSection(
    localModel: LocalLlmModel,
    backend: LlmBackend,
    localModelPath: String,
    localMmprojPath: String,
    modelDownload: ModelDownloadState,
    onLocalModelChange: (LocalLlmModel) -> Unit,
    onBackendChange: (LlmBackend) -> Unit,
    onImport: () -> Unit,
    onDownload: (LocalLlmModel) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onPathsReady: (String, String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Experimental — very slow on phone",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Local models are highly experimental. First response often takes 6–10 minutes on " +
                    "flagship phones (e.g. Snapdragon 8 Gen 2). Cloud API is strongly recommended.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }

    Text(
        "Local Q4 GGUF models (vision + tool calling)",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "Downloads the Q4 language model and vision projector (mmproj). Import is GGUF-only; download mmproj separately.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )

    if (modelDownload.isDownloading) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Downloading ${modelDownload.model?.name ?: "model"}…",
                style = MaterialTheme.typography.bodySmall,
            )
            if (modelDownload.progress.percent > 0f) {
                LinearProgressIndicator(
                    progress = { modelDownload.progress.percent },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                modelDownload.progress.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel download")
            }
        }
    }

    LocalLlmCatalog.models.forEach { info ->
        LocalModelCard(
            info = info,
            selected = localModel == info.model,
            isDownloading = modelDownload.isDownloading && modelDownload.model == info.model,
            onSelect = { onLocalModelChange(info.model) },
            onDownload = {
                onDownload(info.model)
                onPathsReady("", "")
            },
            onOpenPage = { onOpenUrl(info.pageUrl) },
        )
    }

    Text("Inference backend", style = MaterialTheme.typography.titleSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LlmBackend.entries.filter { it != LlmBackend.NPU }.forEach { option ->
            FilterChip(
                selected = backend == option,
                onClick = { onBackendChange(option) },
                label = { Text(option.name) },
            )
        }
    }
    Text(
        when (backend) {
            LlmBackend.CPU -> "CPU: widest compatibility, slowest."
            LlmBackend.GPU -> "GPU: offloads layers via Vulkan/OpenCL when available."
            LlmBackend.NPU -> "NPU is not supported — use CPU or GPU."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
    )

    OutlinedButton(
        onClick = onImport,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null)
        Text("Import .gguf file", modifier = Modifier.padding(start = 8.dp))
    }

    when {
        localModelPath.isNotBlank() && localMmprojPath.isNotBlank() -> {
            Text(
                text = "Ready: ${localModelPath.substringAfterLast('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Vision: ${localMmprojPath.substringAfterLast('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        localModelPath.isNotBlank() -> {
            Text(
                text = "Model: ${localModelPath.substringAfterLast('/')} — download to fetch mmproj",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> {
            Text(
                text = "No model downloaded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun LocalModelCard(
    info: com.autobrowse.android.domain.model.LocalLlmModelInfo,
    selected: Boolean,
    isDownloading: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onOpenPage: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(info.displayName, style = MaterialTheme.typography.titleSmall)
                Text(info.description, style = MaterialTheme.typography.bodySmall)
                Text("Size: ${info.sizeLabel}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "${info.modelFileName} + ${info.mmprojFileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onDownload, enabled = !isDownloading) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        if (isDownloading) "Downloading…" else "Download to phone",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Text(
                    text = "HuggingFace",
                    style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenPage),
                )
            }
        }
    }
}