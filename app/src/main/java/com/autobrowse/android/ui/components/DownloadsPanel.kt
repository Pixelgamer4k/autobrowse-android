package com.autobrowse.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobrowse.android.downloads.DownloadItem
import com.autobrowse.android.downloads.DownloadStatus
import com.autobrowse.android.ui.theme.Motion
import com.autobrowse.android.ui.theme.SectionSeparator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadsPanelOverlay(
    visible: Boolean,
    downloads: List<DownloadItem>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            initialOffsetX = { it },
        ) + fadeIn(Motion.tweenQuick),
        exit = slideOutHorizontally(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            targetOffsetX = { it },
        ) + fadeOut(Motion.tweenQuick),
        modifier = modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Downloads", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${downloads.size} item(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
                SectionSeparator()
                if (downloads.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                            Text(
                                "No downloads yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(downloads, key = { it.id }) { item ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(Motion.tweenQuick) + slideInVertically(Motion.springSmoothOffset) { it / 4 },
                            ) {
                                DownloadRow(
                                    item = item,
                                    onOpen = { onOpen(item) },
                                    onCancel = { onCancel(item) },
                                    onDelete = { onDelete(item) },
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
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val canOpen = item.status == DownloadStatus.COMPLETED && !item.path.isNullOrBlank()
    val isActive = item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING
    val dateLabel = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.modifiedAt))
    val sizeLabel = formatSize(item.sizeBytes)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(enabled = canOpen, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(item.category)
                        append(" · ")
                        append(statusLabel(item.status))
                        append(" · ")
                        append(sizeLabel)
                        append(" · ")
                        append(dateLabel)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2,
                )
                item.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    )
                }
            }
            if (isActive) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel download",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        }
        if (isActive) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "queued"
    DownloadStatus.DOWNLOADING -> "downloading"
    DownloadStatus.COMPLETED -> "completed"
    DownloadStatus.FAILED -> "failed"
    DownloadStatus.CANCELLED -> "cancelled"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}