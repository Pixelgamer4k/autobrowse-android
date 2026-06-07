package com.autobrowse.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.Session
import com.autobrowse.android.domain.model.SessionListItem
import com.autobrowse.android.ui.theme.Motion
import com.autobrowse.android.ui.theme.SectionSeparator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            HamburgerMenuIcon(
                tint = Color.White,
                lineCount = 4,
            )
        }
    }
}

@Composable
private fun HamburgerMenuIcon(
    tint: Color,
    lineCount: Int = 4,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(18.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(lineCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint),
            )
        }
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
    onPinSession: (String, Boolean) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSearchSessions: suspend (String) -> List<SessionListItem>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            initialOffsetX = { -it },
        ) + fadeIn(Motion.tweenQuick),
        exit = slideOutHorizontally(
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            targetOffsetX = { -it },
        ) + fadeOut(Motion.tweenQuick),
        modifier = modifier.fillMaxSize(),
    ) {
        SessionsPanelContent(
            sessions = sessions,
            activeSessionId = activeSessionId,
            onDismiss = onDismiss,
            onSelectSession = onSelectSession,
            onNewSession = onNewSession,
            onPinSession = onPinSession,
            onDeleteSession = onDeleteSession,
            onSearchSessions = onSearchSessions,
        )
    }
}

@Composable
private fun SessionsPanelContent(
    sessions: List<Session>,
    activeSessionId: String?,
    onDismiss: () -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onPinSession: (String, Boolean) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSearchSessions: suspend (String) -> List<SessionListItem>,
) {
    var searchQuery by remember { mutableStateOf("") }
    var listItems by remember { mutableStateOf<List<SessionListItem>>(emptyList()) }

    LaunchedEffect(sessions, searchQuery) {
        listItems = onSearchSessions(searchQuery)
    }

    val pinnedCount = sessions.count { it.isPinned }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close sessions",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = buildString {
                            append("${sessions.size} total")
                            if (pinnedCount > 0) append(" · $pinnedCount pinned")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }

            SectionSeparator()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Search titles and messages",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNewSession),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Text(
                            text = "New session",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            SectionSeparator()

            if (listItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No sessions yet"
                        else "No sessions match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listItems, key = { it.session.id }) { item ->
                        SessionRow(
                            item = item,
                            searchQuery = searchQuery,
                            selected = item.session.id == activeSessionId,
                            canDelete = sessions.size > 1,
                            onClick = { onSelectSession(item.session.id) },
                            onPin = { onPinSession(item.session.id, !item.session.isPinned) },
                            onDelete = { onDeleteSession(item.session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    item: SessionListItem,
    searchQuery: String,
    selected: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val session = item.session
    var menuExpanded by remember { mutableStateOf(false) }
    val dateLabel = remember(session.lastActiveAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(session.lastActiveAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    session.isPinned -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (session.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    )
                }
                HighlightedText(
                    text = session.title,
                    query = if (item.matchInTitle) searchQuery else "",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.matchSnippet?.let { snippet ->
                Spacer(modifier = Modifier.height(4.dp))
                HighlightedText(
                    text = snippet,
                    query = searchQuery,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (selected) "$dateLabel · Active" else dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Session options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(if (session.isPinned) "Unpin" else "Pin to top")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (session.isPinned) {
                                Icons.Outlined.PushPin
                            } else {
                                Icons.Default.PushPin
                            },
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onPin()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    enabled = canDelete,
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}