package com.autobrowse.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autobrowse.android.ui.MainViewModel
import com.autobrowse.android.ui.components.BrowserPanel
import com.autobrowse.android.ui.components.ChatBar
import com.autobrowse.android.ui.components.DashboardPanel
import com.autobrowse.android.ui.components.FlexibleSplitPane
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
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Auto Browse · Desktop WebView",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )

        FlexibleSplitPane(
            topWeight = state.browserPanelWeight,
            onWeightChange = viewModel::setBrowserPanelWeight,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            topContent = {
                BrowserPanel(
                    tabs = state.tabs,
                    windowFrames = state.windowFrames,
                    activeTabId = state.activeTabId,
                    controller = viewModel.browserController,
                    onSelectTab = viewModel::selectTab,
                    onAddTab = { viewModel.addTab() },
                    onTabMetadataUpdate = viewModel::updateTabMetadata,
                    onMoveWindow = viewModel::moveWindow,
                    onResizeWindow = viewModel::resizeWindow,
                    onEndWindowManipulation = viewModel::endWindowManipulation,
                    onNavigate = viewModel::navigateActiveTab,
                    onRefreshTab = viewModel::refreshTab,
                    onToggleMaximizeTab = viewModel::toggleMaximizeTab,
                    onMinimizeTab = viewModel::minimizeTab,
                    onCloseTab = viewModel::closeTab,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            bottomContent = {
                Column(modifier = Modifier.fillMaxSize()) {
                    DashboardPanel(
                        tasks = state.tasks,
                        messages = state.messages,
                        isAgentThinking = state.isAgentThinking,
                        agentProgress = state.agentProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )

                    ChatBar(
                        value = state.chatInput,
                        onValueChange = viewModel::updateChatInput,
                        attachments = state.pendingAttachments,
                        onAddAttachment = viewModel::addAttachment,
                        onRemoveAttachment = viewModel::removeAttachment,
                        onSend = viewModel::sendMessage,
                        onSettings = { viewModel.toggleSettings(true) },
                        isSending = state.isAgentThinking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            },
        )

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}