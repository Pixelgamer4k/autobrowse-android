package com.autobrowse.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autobrowse.android.ui.theme.Motion
import com.autobrowse.android.ui.theme.SectionSeparator
import com.autobrowse.android.domain.model.AgentPhase
import com.autobrowse.android.domain.model.AgentProgress
import com.autobrowse.android.domain.model.AgentRole
import com.autobrowse.android.domain.model.ChatMessage

private val UserBubbleBg = Color(0xFF2C2C2E)

@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    isAgentThinking: Boolean,
    agentProgress: AgentProgress?,
    scrollOnInput: Boolean,
    composerBottomPadding: Dp,
    bannerMessage: String? = null,
    onQuickAction: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        bannerMessage?.let { message ->
            ChatBanner(message = message, isError = true)
        }
        if (agentProgress?.captchaPending == true) {
            ChatBanner(
                message = agentProgress.captchaBannerMessage
                    ?: "CAPTCHA detected — complete the challenge in the browser window.",
                isError = false,
            )
        }

        ChatConversation(
            messages = messages,
            isAgentThinking = isAgentThinking,
            agentProgress = agentProgress,
            scrollOnInput = scrollOnInput,
            contentBottomPadding = composerBottomPadding,
            onQuickAction = onQuickAction,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ChatBanner(message: String, isError: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            },
        ) {
            Text(
                text = message,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        SectionSeparator()
    }
}

@Composable
private fun ChatConversation(
    messages: List<ChatMessage>,
    isAgentThinking: Boolean,
    agentProgress: AgentProgress?,
    scrollOnInput: Boolean,
    contentBottomPadding: Dp,
    onQuickAction: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isAgentThinking, agentProgress?.streamPreview) {
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = contentBottomPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(0.25f),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Ask me to browse, research, or automate anything.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                onQuickAction?.let { onAction ->
                    Spacer(modifier = Modifier.height(8.dp))
                    QuickActionChips(onAction = onAction)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        state = listState,
        contentPadding = PaddingValues(bottom = contentBottomPadding + 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            AnimatedMessageBubble(message = message)
        }
        if (isAgentThinking) {
            item(key = "thinking") {
                AgentThinkingBubble(agentProgress = agentProgress)
            }
        }
    }
}

@Composable
private fun QuickActionChips(onAction: (String) -> Unit) {
    val actions = listOf(
        "Search Google" to "Search Google for the latest AI news",
        "Compare products" to "Find 4 affordable highly-rated men's sneakers on Amazon and open each in its own window",
        "Summarize page" to "Summarize the current page",
        "Research topic" to "Research how climate change affects penguin breeding cycles. Open sources side by side and summarize species-specific impacts.",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, prompt) ->
                    Surface(
                        onClick = { onAction(prompt) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedMessageBubble(message: ChatMessage) {
    val isUser = message.role == AgentRole.USER
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(Motion.tweenMedium) + slideInVertically(Motion.springSmoothOffset) { it / 4 } + scaleIn(Motion.springSnappy, initialScale = 0.96f),
    ) {
        if (isUser) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                    color = UserBubbleBg,
                    tonalElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                        if (shouldRenderMarkdown(message.role, message.content)) {
                            MarkdownMessageText(content = message.content)
                        } else {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
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
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    tonalElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "${message.attachments.size} attachment(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (shouldRenderMarkdown(message.role, message.content)) {
                            MarkdownMessageText(content = message.content)
                        } else {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentThinkingBubble(agentProgress: AgentProgress?) {
    val preview = agentProgress?.streamPreview.orEmpty()
    val statusMessage = agentProgress?.message?.takeIf { it.isNotBlank() } ?: "Thinking…"
    val previewScroll = rememberScrollState()

    LaunchedEffect(preview) {
        if (preview.isNotBlank()) {
            previewScroll.animateScrollTo(previewScroll.maxValue)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (agentProgress?.phase != null && agentProgress.phase != AgentPhase.IDLE) {
                            Text(
                                text = agentProgress.phase.name.lowercase().replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }

            if (preview.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TypingDots()
                            Text(
                                text = "Thinking",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (shouldRenderMarkdown(AgentRole.AGENT, preview)) {
                            MarkdownMessageText(
                                content = preview,
                                stabilizeIncomplete = true,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(previewScroll),
                            )
                        } else {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(previewScroll),
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TypingDots()
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val dotCount = 3
    val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
    val delays = remember { List(dotCount) { it * 120 } }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        delays.forEachIndexed { index, delayMs ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delayMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
