package com.autobrowse.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autobrowse.android.ui.MainViewModel
import com.autobrowse.android.ui.components.BrowserPanel
import com.autobrowse.android.ui.components.ChatComposer
import com.autobrowse.android.ui.components.ChatPanel
import com.autobrowse.android.ui.components.SessionsLauncherButton
import com.autobrowse.android.ui.components.SessionsPanelOverlay
import com.autobrowse.android.ui.theme.SectionSeparator

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.showLlmSetup -> {
            LlmSetupScreen(
                llmConfig = state.llmConfig,
                connectionTest = state.llmConnectionTest,
                onTestConnection = viewModel::testLlmConnection,
                onSave = viewModel::saveLlmConfig,
                onImportModel = viewModel::importLocalModel,
                onOpenUrl = viewModel::openUrl,
                onBack = if (state.llmSetupFromSettings) viewModel::closeLlmSetup else null,
            )
        }
        state.showSettings -> {
            SettingsScreen(
                skillConfigs = state.skillConfigs,
                enabledSkills = state.enabledSkills,
                memory = state.memory,
                strategies = state.strategies,
                onOpenLlmSetup = { viewModel.openLlmSetup(fromSettings = true) },
                onToggleSkill = viewModel::toggleSkill,
                onBack = { viewModel.toggleSettings(false) },
            )
        }
        else -> {
            MainContent(viewModel = viewModel, state = state)
        }
    }
}

@Composable
private fun MainContent(
    viewModel: MainViewModel,
    state: com.autobrowse.android.ui.MainUiState,
) {
    var chatInputFocused by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val composerBottomPadding = with(density) { composerHeightPx.toDp() }
    val keyboardLiftActive = chatInputFocused

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.52f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                BrowserPanel(
                    tabs = state.tabs,
                    windowFrames = state.windowFrames,
                    activeTabId = state.activeTabId,
                    controller = viewModel.browserController,
                    onSelectTab = viewModel::selectTab,
                    onAddTab = { viewModel.addTab() },
                    onTabMetadataUpdate = viewModel::updateTabMetadata,
                    onCommitGeometry = viewModel::commitWindowGeometry,
                    onNavigate = viewModel::navigateActiveTab,
                    onRefreshTab = viewModel::refreshTab,
                    onToggleMaximizeTab = viewModel::toggleMaximizeTab,
                    onCloseTab = viewModel::closeTab,
                    modifier = Modifier.fillMaxSize(),
                )

                SessionsLauncherButton(
                    onClick = viewModel::toggleSessionsPanel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 8.dp)
                        .zIndex(30f),
                )
            }

            SectionSeparator()

            ChatPanel(
                messages = state.messages,
                isAgentThinking = state.isAgentThinking,
                agentProgress = state.agentProgress,
                onSettings = { viewModel.toggleSettings(true) },
                onStop = viewModel::stopAgent,
                scrollOnInput = chatInputFocused,
                composerBottomPadding = composerBottomPadding,
                bannerMessage = state.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.48f)
                    .background(MaterialTheme.colorScheme.background),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { composerHeightPx = it.height },
        ) {
            ChatComposer(
                value = state.chatInput,
                onValueChange = viewModel::updateChatInput,
                attachments = state.pendingAttachments,
                onAddAttachment = viewModel::addAttachment,
                onRemoveAttachment = viewModel::removeAttachment,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopAgent,
                isSending = state.isAgentThinking,
                keyboardLiftActive = keyboardLiftActive,
                onFocusChange = { chatInputFocused = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }

        SessionsPanelOverlay(
            visible = state.showSessionsPanel,
            sessions = state.sessions,
            activeSessionId = state.session?.id,
            onDismiss = viewModel::closeSessionsPanel,
            onSelectSession = viewModel::switchSession,
            onNewSession = viewModel::createNewSession,
            onPinSession = viewModel::pinSession,
            onDeleteSession = viewModel::deleteSession,
            modifier = Modifier.zIndex(50f),
        )
    }
}