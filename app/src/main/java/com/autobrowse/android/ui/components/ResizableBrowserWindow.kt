package com.autobrowse.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion
import kotlin.math.roundToInt

@Composable
fun ResizableBrowserWindow(
    tab: BrowserTab,
    isActive: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    controller: BrowserController,
    onSelect: () -> Unit,
    onTabUpdate: (BrowserTab) -> Unit,
    onLayoutChange: (BrowserWindowLayout) -> Unit,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val titleBarHeightPx = with(density) { BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp.toPx() }
    val minimizedHeightPx = titleBarHeightPx.coerceAtLeast(1f)

    var dragOffset by remember(tab.id) { mutableStateOf(IntOffset.Zero) }
    var resizeDelta by remember(tab.id) { mutableStateOf(0f to 0f) }

    val baseLayout = when (tab.windowState) {
        BrowserWindowState.MAXIMIZED -> BrowserWindowLayout.maximized()
        BrowserWindowState.MINIMIZED -> tab.layout
        BrowserWindowState.NORMAL -> tab.layout
    }

    val widthPx = (baseLayout.widthFraction * canvasWidthPx + resizeDelta.first).coerceIn(
        canvasWidthPx * BrowserWindowLayout.MIN_FRACTION,
        canvasWidthPx,
    )
    val heightPx = when (tab.windowState) {
        BrowserWindowState.MINIMIZED -> minimizedHeightPx
        else -> (baseLayout.heightFraction * canvasHeightPx + resizeDelta.second).coerceIn(
            canvasHeightPx * BrowserWindowLayout.MIN_FRACTION,
            canvasHeightPx,
        )
    }
    val xPx = (baseLayout.offsetX * canvasWidthPx + dragOffset.x).coerceIn(
        0f,
        (canvasWidthPx - widthPx).coerceAtLeast(0f),
    )
    val yPx = (baseLayout.offsetY * canvasHeightPx + dragOffset.y).coerceIn(
        0f,
        (canvasHeightPx - heightPx).coerceAtLeast(0f),
    )

    val canResize = tab.windowState == BrowserWindowState.NORMAL

    fun commitLayout() {
        val newLayout = BrowserWindowLayout(
            offsetX = (xPx / canvasWidthPx).coerceIn(0f, 1f),
            offsetY = (yPx / canvasHeightPx).coerceIn(0f, 1f),
            widthFraction = (widthPx / canvasWidthPx).coerceIn(
                BrowserWindowLayout.MIN_FRACTION,
                BrowserWindowLayout.MAX_FRACTION,
            ),
            heightFraction = (heightPx / canvasHeightPx).coerceIn(
                BrowserWindowLayout.MIN_FRACTION,
                BrowserWindowLayout.MAX_FRACTION,
            ),
        ).clamped()
        dragOffset = IntOffset.Zero
        resizeDelta = 0f to 0f
        onLayoutChange(newLayout)
        onSelect()
    }

    val elevation by animateFloatAsState(
        targetValue = if (isActive) 14f else 5f,
        animationSpec = Motion.springSmooth,
        label = "windowElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.985f,
        animationSpec = Motion.springSnappy,
        label = "windowScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
            .width(with(density) { widthPx.toDp() })
            .height(with(density) { heightPx.toDp() })
            .shadow(elevation.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
            )
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WindowTitleBar(
                tab = tab,
                isActive = isActive,
                onRefresh = {
                    onSelect()
                    onRefresh()
                },
                onToggleMaximize = {
                    onSelect()
                    onToggleMaximize()
                },
                onMinimize = {
                    onSelect()
                    onMinimize()
                },
                onClose = onClose,
                onDrag = { delta ->
                    dragOffset = IntOffset(
                        dragOffset.x + delta.x.roundToInt(),
                        dragOffset.y + delta.y.roundToInt(),
                    )
                },
                onDragEnd = { commitLayout() },
            )

            if (tab.windowState != BrowserWindowState.MINIMIZED) {
                BrowserWebView(
                    tab = tab,
                    controller = controller,
                    onTabUpdate = onTabUpdate,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }

        if (canResize) {
            ResizeEdge(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(20.dp),
                onResize = { dx, _ -> resizeDelta = resizeDelta.first + dx to resizeDelta.second },
                onResizeEnd = { commitLayout() },
            )
            ResizeEdge(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(20.dp),
                onResize = { _, dy -> resizeDelta = resizeDelta.first to resizeDelta.second + dy },
                onResizeEnd = { commitLayout() },
            )
            ResizeHandle(
                modifier = Modifier.align(Alignment.BottomEnd),
                onResize = { dx, dy ->
                    resizeDelta = resizeDelta.first + dx to resizeDelta.second + dy
                },
                onResizeEnd = { commitLayout() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowTitleBar(
    tab: BrowserTab,
    isActive: Boolean,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val maxMinIcon = when (tab.windowState) {
        BrowserWindowState.MAXIMIZED -> Icons.Default.FullscreenExit
        BrowserWindowState.MINIMIZED -> Icons.Default.UnfoldMore
        BrowserWindowState.NORMAL -> Icons.Default.Fullscreen
    }

    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            WindowControlButton(
                onClick = onRefresh,
                contentDescription = "Refresh",
                tint = Color(0xFF4A90D9),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            WindowControlButton(
                onClick = onToggleMaximize,
                onLongClick = onMinimize,
                contentDescription = "Maximize or restore. Long press to minimize.",
                tint = Color(0xFF34A853),
            ) {
                Icon(maxMinIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            WindowControlButton(
                onClick = onClose,
                contentDescription = "Close",
                tint = Color(0xFFE8453C),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(tab.id) {
                        detectDragGestures(
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                        )
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = tab.title.ifBlank { tab.url },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    tint: Color,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = tint.copy(alpha = 0.18f),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides tint,
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onResizeEnd() },
                    onDragCancel = { onResizeEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x, dragAmount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun ResizeEdge(
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = { onResizeEnd() },
                onDragCancel = { onResizeEnd() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onResize(dragAmount.x, dragAmount.y)
                },
            )
        },
    )
}