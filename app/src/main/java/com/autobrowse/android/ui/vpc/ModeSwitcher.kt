package com.autobrowse.android.ui.vpc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.AppMode

@Composable
fun ModeSwitcher(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeChip(
            label = "Browser",
            icon = Icons.Default.Language,
            selected = mode == AppMode.Browser,
            onClick = { onModeChange(AppMode.Browser) },
        )
        ModeChip(
            label = "Virtual PC",
            icon = Icons.Default.Computer,
            selected = mode == AppMode.VirtualPC,
            accent = true,
            onClick = { onModeChange(AppMode.VirtualPC) },
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        selected && accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        selected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val border = if (selected && accent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.55f),
            modifier = Modifier.padding(0.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.55f),
        )
    }
}