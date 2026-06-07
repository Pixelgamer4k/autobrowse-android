package com.autobrowse.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.autobrowse.android.ui.theme.AppGradients
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.AgentPhase
import com.autobrowse.android.domain.model.AgentProgress
import com.autobrowse.android.domain.model.AgentRole
import com.autobrowse.android.domain.model.ChatMessage

@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    isAgentThinking: Boolean,
    agentProgress: AgentProgress?,
    onSettings: () -> Unit,
    scrollOnInput: Boolean,
    composerBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 6.dp),
    ) {
        ChatPanelHeader(
            onSettings = onSettings,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        ChatConversation(
            messages = messages,
            isAgentThinking = isAgentThinking,
            agentProgress = agentProgress,
            scrollOnInput = scrollOnInput,
            contentBottomPadding = composerBottomPadding,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ChatPanelHeader(
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppGradients.chatHeader)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.1f),
                    1f to Color.Transparent,
                ),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppGradients.accentGlow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "Agent",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ChatConversation(
    messages: List<ChatMessage>,
    isAgentThinking: Boolean,
    agentProgress: AgentProgress?,
    scrollOnInput: Boolean,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isAgentThinking) {
        val lastIndex = messages.size + if (isAgentThinking) 1 else 0
        if (lastIndex > 0) {
            listState.animateScrollToItem(lastIndex - 1)
        }
    }

    LaunchedEffect(scrollOnInput) {
        if (!scrollOnInput) return@LaunchedEffect
        val lastIndex = messages.size + if (isAgentThinking) 1 else 0
        if (lastIndex > 0) {
            listState.animateScrollToItem(lastIndex - 1)
        }
    }

    if (messages.isEmpty() && !isAgentThinking) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Ask me to browse, research, or automate anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        state = listState,
        contentPadding = PaddingValues(bottom = contentBottomPadding + 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            ChatMessageBubble(message = message)
        }
        if (isAgentThinking) {
            item(key = "thinking") {
                AgentThinkingBubble(agentProgress = agentProgress)
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.role == AgentRole.USER

    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(AppGradients.userBubble)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.attachments.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Text(
                                text = "${message.attachments.size} attachment(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                    .background(AppGradients.agentBubble)
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            0f to MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                            0.25f to Color.White.copy(alpha = 0.06f),
                            1f to Color.Transparent,
                        ),
                        shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (message.attachments.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                            )
                            Text(
                                text = "${message.attachments.size} attachment(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentThinkingBubble(agentProgress: AgentProgress?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                .background(AppGradients.thinkingBubble)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = agentProgress?.message?.takeIf { it.isNotBlank() } ?: "Thinking…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (agentProgress?.phase != null && agentProgress.phase != AgentPhase.IDLE) {
                        Text(
                            text = agentProgress.phase.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}