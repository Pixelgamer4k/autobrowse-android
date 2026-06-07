package com.autobrowse.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.ui.theme.Motion

@Composable
fun BrowserPanel(
    tabs: List<BrowserTab>,
    activeTabId: String?,
    controller: BrowserController,
    onSelectTab: (String) -> Unit,
    onAddTab: () -> Unit,
    onTabUpdate: (BrowserTab) -> Unit,
    onLayoutChange: (String, BrowserWindowLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()
    val sortedTabs = tabs.sortedBy { it.zIndex }

    Column(modifier = modifier.fillMaxSize()) {
        TabStrip(
            tabs = tabs,
            activeTabId = activeTabId,
            onSelectTab = onSelectTab,
            onAddTab = onAddTab,
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f)),
        ) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()

            if (tabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No browser windows",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                sortedTabs.forEach { tab ->
                    val isActive = tab.id == activeTabId
                    ResizableBrowserWindow(
                        tab = tab,
                        isActive = isActive,
                        canvasWidthPx = canvasWidth,
                        canvasHeightPx = canvasHeight,
                        controller = controller,
                        onSelect = { onSelectTab(tab.id) },
                        onTabUpdate = onTabUpdate,
                        onLayoutChange = { layout -> onLayoutChange(tab.id, layout) },
                        modifier = Modifier.zIndex(if (isActive) 100f else tab.zIndex.toFloat()),
                    )
                }
            }
        }

        BrowserToolbar(
            desktopMode = true,
            onRefresh = { activeTab?.let { controller.loadUrl(it.url, it.id) } },
            onAddTab = onAddTab,
        )
    }
}

@Composable
private fun TabStrip(
    tabs: List<BrowserTab>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onAddTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.take(6).forEach { tab ->
            val selected = tab.id == activeTabId
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.02f else 1f,
                animationSpec = Motion.springSnappy,
                label = "tabScale",
            )
            Surface(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(32.dp)
                    .scale(scale)
                    .clickable { onSelectTab(tab.id) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                tonalElevation = if (selected) 4.dp else 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = tab.title.take(12),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    StatusDots(status = tab.status)
                }
            }
        }
        IconButton(onClick = onAddTab, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "New tab", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StatusDots(status: BrowserTabStatus, modifier: Modifier = Modifier) {
    val colors = when (status) {
        BrowserTabStatus.ACTIVE -> listOf(Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39))
        BrowserTabStatus.AGENT_CONTROLLED -> listOf(Color(0xFF00BCD4), Color(0xFF4CAF50), Color(0xFFFFC107))
        BrowserTabStatus.LOADING -> listOf(Color(0xFFFFC107), Color(0xFFFFC107), Color(0xFFE0E0E0))
        BrowserTabStatus.ERROR -> listOf(Color(0xFFF44336), Color(0xFFF44336), Color(0xFFE0E0E0))
        BrowserTabStatus.IDLE -> listOf(Color(0xFFE0E0E0), Color(0xFFE0E0E0), Color(0xFFE0E0E0))
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun BrowserToolbar(
    desktopMode: Boolean,
    onRefresh: () -> Unit,
    onAddTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Icons.Default.Refresh to onRefresh,
            Icons.Default.Add to onAddTab,
            Icons.Default.AutoAwesome to {},
            Icons.Default.DesktopWindows to {},
        ).forEach { (icon, action) ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .height(28.dp)
                    .clickable(onClick = action),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        if (desktopMode) {
            Text(
                text = "Desktop",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}