package com.autobrowse.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.autobrowse.android.ui.theme.Motion
import com.autobrowse.android.ui.theme.SectionSeparator

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
                    .weight(0.56f)
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
                scrollOnInput = chatInputFocused,
                composerBottomPadding = composerBottomPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.44f)
                    .background(MaterialTheme.colorScheme.background),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { composerHeightPx = it.height },
        ) {
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            ChatComposer(
                value = state.chatInput,
                onValueChange = viewModel::updateChatInput,
                attachments = state.pendingAttachments,
                onAddAttachment = viewModel::addAttachment,
                onRemoveAttachment = viewModel::removeAttachment,
                onSend = viewModel::sendMessage,
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