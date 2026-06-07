package com.autobrowse.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.Session
import com.autobrowse.android.ui.theme.Motion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = "Sessions",
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SessionsPanelOverlay(
    visible: Boolean,
    sessions: List<Session>,
    activeSessionId: String?,
    onDismiss: () -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it },
                ) + fadeIn(Motion.tweenQuick),
                exit = slideOutHorizontally(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    targetOffsetX = { -it },
                ) + fadeOut(Motion.tweenQuick),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onNewSession),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = "New session",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sessions, key = { it.id }) { session ->
                                SessionRow(
                                    session = session,
                                    selected = session.id == activeSessionId,
                                    onClick = { onSelectSession(session.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dateLabel = remember(session.lastActiveAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(session.lastActiveAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}