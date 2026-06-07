package com.autobrowse.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autobrowse.android.ui.MainViewModel
import com.autobrowse.android.ui.components.BrowserPanel
import com.autobrowse.android.ui.components.ChatPanel
import com.autobrowse.android.ui.components.SessionsLauncherButton
import com.autobrowse.android.ui.components.SessionsPanelOverlay
import com.autobrowse.android.ui.theme.Motion

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state.showSettings,
        transitionSpec = {
            if (targetState) {
                slideInHorizontally(Motion.springSmoothOffset) { it } + fadeIn(Motion.tweenMedium) togetherWith
                    slideOutHorizontally(Motion.springSmoothOffset) { -it / 3 } + fadeOut(Motion.tweenQuick)
            } else {
                slideInHorizontally(Motion.springSmoothOffset) { -it } + fadeIn(Motion.tweenMedium) togetherWith
                    slideOutHorizontally(Motion.springSmoothOffset) { it / 3 } + fadeOut(Motion.tweenQuick)
            }
        },
        label = "settingsTransition",
    ) { showSettings ->
        if (showSettings) {
            SettingsScreen(
                llmConfig = state.llmConfig,
                skillConfigs = state.skillConfigs,
                enabledSkills = state.enabledSkills,
                memory = state.memory,
                strategies = state.strategies,
                onSaveLlmConfig = viewModel::saveLlmConfig,
                onToggleSkill = viewModel::toggleSkill,
                onBack = { viewModel.toggleSettings(false) },
            )
        } else {
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
    val browserWeight by animateFloatAsState(
        targetValue = if (chatInputFocused) 0f else 0.56f,
        animationSpec = Motion.springSmooth,
        label = "browserWeight",
    )
    val chatWeight by animateFloatAsState(
        targetValue = if (chatInputFocused) 1f else 0.44f,
        animationSpec = Motion.springSmooth,
        label = "chatWeight",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (browserWeight > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(browserWeight),
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

                SessionsPanelOverlay(
                    visible = state.showSessionsPanel,
                    sessions = state.sessions,
                    activeSessionId = state.session?.id,
                    onDismiss = viewModel::closeSessionsPanel,
                    onSelectSession = viewModel::switchSession,
                    onNewSession = viewModel::createNewSession,
                    modifier = Modifier.zIndex(40f),
                )
            }
        }

        ChatPanel(
            messages = state.messages,
            isAgentThinking = state.isAgentThinking,
            agentProgress = state.agentProgress,
            value = state.chatInput,
            onValueChange = viewModel::updateChatInput,
            attachments = state.pendingAttachments,
            onAddAttachment = viewModel::addAttachment,
            onRemoveAttachment = viewModel::removeAttachment,
            onSend = viewModel::sendMessage,
            onSettings = { viewModel.toggleSettings(true) },
            onChatInputFocusChange = { chatInputFocused = it },
            error = state.error,
            modifier = Modifier
                .fillMaxWidth()
                .weight(chatWeight),
        )
    }
}