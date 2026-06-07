package com.autobrowse.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun MiniAppsWindowOverlay(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(40f),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                        Text(
                            text = "Mini Apps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close mini apps",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
                Text(
                    text = "Work in progress",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Text(
                    text = "Mini app windows will launch here in a future update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
fun MiniAppsLauncherIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(34.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = "Mini apps",
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
    }
}