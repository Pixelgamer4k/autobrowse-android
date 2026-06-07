package com.autobrowse.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.ui.LlmConnectionTestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSetupScreen(
    llmConfig: LlmConfig,
    connectionTest: LlmConnectionTestState,
    onTestConnection: (apiKey: String, apiUrl: String, modelId: String) -> Unit,
    onSave: (LlmConfig) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var apiKey by remember(llmConfig) { mutableStateOf(llmConfig.apiKey) }
    var apiUrl by remember(llmConfig) { mutableStateOf(llmConfig.apiUrl) }
    var modelId by remember(llmConfig) { mutableStateOf(llmConfig.modelId) }

    val canSubmit = apiKey.isNotBlank() && apiUrl.isNotBlank() && modelId.isNotBlank()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Connect an OpenAI-compatible API to power the browsing agent. Credentials are stored encrypted on device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Token") },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("gpt-4o-mini") },
                )
                IconButton(
                    onClick = {
                        if (canSubmit) {
                            onTestConnection(apiKey.trim(), apiUrl.trim(), modelId.trim())
                        }
                    },
                    enabled = canSubmit && !connectionTest.isTesting,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (connectionTest.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Test connection",
                        )
                    }
                }
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

            Button(
                onClick = {
                    onSave(
                        llmConfig.copy(
                            apiKey = apiKey.trim(),
                            apiUrl = apiUrl.trim(),
                            modelId = modelId.trim(),
                        ),
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (onBack == null) "Continue" else "Save")
            }
        }
    }
}