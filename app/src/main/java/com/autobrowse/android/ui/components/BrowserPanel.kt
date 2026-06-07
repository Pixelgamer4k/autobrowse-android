package com.autobrowse.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobrowse.android.ui.theme.SectionSeparator
import androidx.compose.ui.zIndex
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.FloatingWindowEngine
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserTabStatus
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.ui.theme.Motion

@Composable
fun BrowserPanel(
    tabs: List<BrowserTab>,
    windowFrames: Map<String, BrowserWindowFrame>,
    activeTabId: String?,
    controller: BrowserController,
    onSelectTab: (String) -> Unit,
    onAddTab: () -> Unit,
    onTabMetadataUpdate: (String, String?, String?, BrowserTabStatus?) -> Unit,
    onCommitGeometry: (String, BrowserWindowLayout) -> Unit,
    onNavigate: (String) -> Unit,
    onRefreshTab: (String) -> Unit,
    onToggleMaximizeTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
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
            onCloseTab = onCloseTab,
            modifier = Modifier.padding(start = 52.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f)),
        ) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()

            if (tabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No browser windows — tap + to open one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                sortedTabs.forEach { tab ->
                    val frame = FloatingWindowEngine.frameForTab(tab.id, tabs, windowFrames)
                    val isActive = tab.id == activeTabId
                    ResizableBrowserWindow(
                        tab = tab,
                        frame = frame,
                        isActive = isActive,
                        canvasWidthPx = canvasWidth,
                        canvasHeightPx = canvasHeight,
                        controller = controller,
                        onSelect = { onSelectTab(tab.id) },
                        onTabUpdate = { updated ->
                            onTabMetadataUpdate(
                                tab.id,
                                updated.url,
                                updated.title,
                                updated.status,
                            )
                        },
                        onCommitGeometry = { layout -> onCommitGeometry(tab.id, layout) },
                        onRefresh = { onRefreshTab(tab.id) },
                        onToggleMaximize = { onToggleMaximizeTab(tab.id) },
                        onClose = { onCloseTab(tab.id) },
                        modifier = Modifier,
                    )
                }
                }
            }
        }

        BrowserToolbar(
            url = activeTab?.url.orEmpty(),
            onNavigate = onNavigate,
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
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val activeIndex = tabs.indexOfFirst { it.id == activeTabId }

    LaunchedEffect(activeIndex, tabs.size) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        items(tabs, key = { it.id }) { tab ->
            val selected = tab.id == activeTabId
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.02f else 1f,
                animationSpec = Motion.springSnappy,
                label = "tabScale",
            )
            val tabBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.55f else 0.35f)
            val tabBorder = if (selected) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            }
            Surface(
                modifier = Modifier
                    .widthIn(min = 108.dp, max = 196.dp)
                    .height(30.dp)
                    .scale(scale)
                    .border(1.dp, tabBorder, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = tabBg,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectTab(tab.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (selected) 0.9f else 0.55f,
                            ),
                        )
                        Text(
                            text = tab.title.ifBlank { "New Tab" },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (selected) 0.92f else 0.55f,
                            ),
                        )
                    }
                    IconButton(
                        onClick = { onCloseTab(tab.id) },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (selected) 0.75f else 0.45f,
                            ),
                        )
                    }
                }
            }
        }
        item {
            IconButton(onClick = onAddTab, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New tab",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun BrowserToolbar(
    url: String,
    onNavigate: (String) -> Unit,
    onAddTab: () -> Unit,
) {
    var address by remember { mutableStateOf(url) }
    LaunchedEffect(url) { address = url }

    fun submitAddress() {
        if (address.isNotBlank()) {
            onNavigate(address)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionSeparator()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        "Search or enter URL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submitAddress() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { submitAddress() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Go",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                    }
                },
            )
            IconButton(onClick = onAddTab, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New tab",
                    modifier = Modifier.size(18.dp),
                    tint = Color.White,
                )
            }
        }
    }
}