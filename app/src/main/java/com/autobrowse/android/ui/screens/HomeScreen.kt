package com.autobrowse.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.autobrowse.android.ui.components.OverlayBackHandlers
import com.autobrowse.android.ui.components.ChatComposer
import com.autobrowse.android.ui.components.ChatPanel
import com.autobrowse.android.ui.components.DownloadsPanelOverlay
import com.autobrowse.android.ui.components.SessionsLauncherButton
import com.autobrowse.android.ui.components.SessionsPanelOverlay


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
                onDownloadModel = viewModel::downloadLocalModel,
                onDeleteModel = viewModel::deleteLocalModel,
                onCancelModelDownload = viewModel::cancelModelDownload,
                modelDownload = state.modelDownload,
                localModelBusy = state.localModelBusy,
                downloadedModels = state.downloadedLocalModels,
                onOpenUrl = viewModel::openUrl,
                onBack = if (state.llmSetupFromSettings) viewModel::closeLlmSetup else null,
            )
        }
        state.showSettings -> {
            SettingsScreen(
                skillConfigs = state.skillConfigs,
                enabledSkills = state.enabledSkills,
                agentSkills = state.agentSkills,
                memory = state.memory,
                strategies = state.strategies,
                skillTransfer = state.skillTransfer,
                feedbackEntries = state.feedbackEntries,
                feedbackTransfer = state.feedbackTransfer,
                onOpenLlmSetup = { viewModel.openLlmSetup(fromSettings = true) },
                onToggleSkill = viewModel::toggleSkill,
                onBuildLearnedSkillsExport = viewModel::buildLearnedSkillsExport,
                onCreateLearnedSkillsShareUri = viewModel::createLearnedSkillsShareUri,
                onBuildLearnedSkillsShareIntent = viewModel::buildLearnedSkillsShareIntent,
                onSaveLearnedSkillsExport = viewModel::saveLearnedSkillsExport,
                onImportLearnedSkills = viewModel::importLearnedSkills,
                onClearSkillTransferMessage = viewModel::clearSkillTransferMessage,
                onShowSkillTransfer = viewModel::showSkillTransfer,
                onBuildFeedbackExport = viewModel::buildFeedbackExport,
                onCreateFeedbackShareUri = viewModel::createFeedbackShareUri,
                onBuildFeedbackShareIntent = viewModel::buildFeedbackShareIntent,
                onSaveFeedbackExport = viewModel::saveFeedbackExport,
                onImportFeedback = viewModel::importFeedback,
                onClearFeedbackTransferMessage = viewModel::clearFeedbackTransferMessage,
                onShowFeedbackTransfer = viewModel::showFeedbackTransfer,
                onUpvoteFeedback = viewModel::upvoteFeedback,
                onDownvoteFeedback = viewModel::downvoteFeedback,
                onDeleteFeedback = viewModel::deleteFeedback,
                captchaConfig = state.captchaConfig,
                onCaptchaConfigChange = viewModel::updateCaptchaConfig,
                onSaveCaptchaConfig = viewModel::saveCaptchaConfig,
                exportFileName = viewModel.learnedSkillsExportFileName(),
                feedbackExportFileName = viewModel.feedbackExportFileName(),
                appUiConfig = state.appUiConfig,
                onResolutionScaleChange = viewModel::updateResolutionScale,
                onMaxAgentIterationsChange = viewModel::updateMaxAgentIterations,
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
    val composerBottomPadding = with(density) { composerHeightPx.toDp() + 4.dp }

    OverlayBackHandlers(
        enabled = state.showSessionsPanel,
        onBack = viewModel::closeSessionsPanel,
    )
    OverlayBackHandlers(
        enabled = state.showDownloadsPanel,
        onBack = viewModel::closeDownloadsPanel,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.46f)
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
                    onCaptureScreenshot = viewModel::captureTabScreenshot,
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

            ChatPanel(
                messages = state.messages,
                isAgentThinking = state.isAgentThinking,
                agentProgress = state.agentProgress,
                scrollOnInput = chatInputFocused,
                composerBottomPadding = composerBottomPadding,
                bannerMessage = state.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.54f)
                    .then(
                        if (chatInputFocused) {
                            Modifier.windowInsetsPadding(
                                WindowInsets.ime.union(WindowInsets.navigationBars)
                                    .only(WindowInsetsSides.Bottom),
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        ChatComposer(
            value = state.chatInput,
            onValueChange = viewModel::updateChatInput,
            attachments = state.pendingAttachments,
            onAddAttachment = viewModel::addAttachment,
            onRemoveAttachment = viewModel::removeAttachment,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stopAgent,
            isSending = state.isAgentThinking,
            onFocusChange = { chatInputFocused = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .onSizeChanged { composerHeightPx = it.height }
                .zIndex(20f),
        )

        SessionsPanelOverlay(
            visible = state.showSessionsPanel,
            sessions = state.sessions,
            activeSessionId = state.session?.id,
            onDismiss = viewModel::closeSessionsPanel,
            onSelectSession = viewModel::switchSession,
            onNewSession = viewModel::createNewSession,
            onPinSession = viewModel::pinSession,
            onDeleteSession = viewModel::deleteSession,
            onSearchSessions = viewModel::searchSessions,
            onOpenSettings = { viewModel.toggleSettings(true) },
            onOpenDownloads = viewModel::toggleDownloadsPanel,
            modifier = Modifier.zIndex(50f),
        )

        DownloadsPanelOverlay(
            visible = state.showDownloadsPanel,
            downloads = state.downloads,
            onDismiss = viewModel::closeDownloadsPanel,
            onRefresh = viewModel::refreshDownloads,
            onOpen = viewModel::openDownload,
            onCancel = viewModel::cancelDownload,
            onDelete = viewModel::deleteDownload,
            modifier = Modifier.zIndex(55f),
        )
    }
}