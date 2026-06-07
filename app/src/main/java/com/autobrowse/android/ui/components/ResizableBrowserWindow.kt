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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion
import kotlin.math.roundToInt

private val ResizeGutter = 32.dp

@Composable
fun ResizableBrowserWindow(
    tab: BrowserTab,
    isActive: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    controller: BrowserController,
    onSelect: () -> Unit,
    onTabUpdate: (BrowserTab) -> Unit,
    onPreviewLayout: (BrowserWindowLayout) -> Unit,
    onCommitLayout: (BrowserWindowLayout) -> Unit,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val titleBarHeightPx = with(density) { BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp.toPx() }
    val minimizedHeightPx = titleBarHeightPx.coerceAtLeast(1f)

    var isManipulating by remember(tab.id) { mutableStateOf(false) }
    var dragStartLayout by remember(tab.id) { mutableStateOf<BrowserWindowLayout?>(null) }
    var dragAccum by remember(tab.id) { mutableStateOf(Offset.Zero) }
    var resizeStartLayout by remember(tab.id) { mutableStateOf<BrowserWindowLayout?>(null) }
    var resizeAccum by remember(tab.id) { mutableStateOf(Offset.Zero) }

    val baseLayout = when (tab.windowState) {
        BrowserWindowState.MAXIMIZED -> BrowserWindowLayout.maximized()
        BrowserWindowState.MINIMIZED,
        BrowserWindowState.NORMAL,
        -> tab.layout
    }

    val widthPx = baseLayout.widthFraction * canvasWidthPx
    val heightPx = when (tab.windowState) {
        BrowserWindowState.MINIMIZED -> minimizedHeightPx
        else -> baseLayout.heightFraction * canvasHeightPx
    }
    val xPx = (baseLayout.offsetX * canvasWidthPx).coerceIn(0f, (canvasWidthPx - widthPx).coerceAtLeast(0f))
    val yPx = (baseLayout.offsetY * canvasHeightPx).coerceIn(0f, (canvasHeightPx - heightPx).coerceAtLeast(0f))

    val canResize = tab.windowState == BrowserWindowState.NORMAL && canvasWidthPx > 0f && canvasHeightPx > 0f

    fun layoutFromDrag(start: BrowserWindowLayout, accum: Offset): BrowserWindowLayout {
        return start.copy(
            offsetX = start.offsetX + accum.x / canvasWidthPx,
            offsetY = start.offsetY + accum.y / canvasHeightPx,
        ).clamped()
    }

    fun layoutFromResize(start: BrowserWindowLayout, accum: Offset): BrowserWindowLayout {
        return start.copy(
            widthFraction = start.widthFraction + accum.x / canvasWidthPx,
            heightFraction = start.heightFraction + accum.y / canvasHeightPx,
        ).clamped()
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
            .zIndex(if (isActive) 200f else tab.zIndex.toFloat())
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
                onRefresh = onRefresh,
                onToggleMaximize = onToggleMaximize,
                onMinimize = onMinimize,
                onClose = onClose,
                onSelect = onSelect,
                onDragStart = {
                    if (tab.windowState != BrowserWindowState.MAXIMIZED) {
                        isManipulating = true
                        dragStartLayout = tab.layout
                        dragAccum = Offset.Zero
                        onSelect()
                    }
                },
                onDrag = { delta ->
                    if (tab.windowState == BrowserWindowState.MAXIMIZED) return@WindowTitleBar
                    val start = dragStartLayout ?: tab.layout
                    dragAccum += delta
                    onPreviewLayout(layoutFromDrag(start, dragAccum))
                },
                onDragEnd = {
                    isManipulating = false
                    if (dragStartLayout != null) {
                        onCommitLayout(tab.layout)
                    }
                    dragStartLayout = null
                    dragAccum = Offset.Zero
                },
            )

            if (tab.windowState != BrowserWindowState.MINIMIZED) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    BrowserWebView(
                        tab = tab,
                        controller = controller,
                        onTabUpdate = onTabUpdate,
                        interactionEnabled = !isManipulating,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = ResizeGutter, bottom = ResizeGutter),
                    )

                    if (canResize) {
                        ResizeEdge(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(ResizeGutter)
                                .zIndex(2f),
                            onResizeStart = {
                                isManipulating = true
                                resizeStartLayout = tab.layout
                                resizeAccum = Offset.Zero
                                onSelect()
                            },
                            onResize = { dx, dy ->
                                val start = resizeStartLayout ?: tab.layout
                                resizeAccum += Offset(dx, dy)
                                onPreviewLayout(layoutFromResize(start, resizeAccum))
                            },
                            onResizeEnd = {
                                isManipulating = false
                                if (resizeStartLayout != null) {
                                    onCommitLayout(tab.layout)
                                }
                                resizeStartLayout = null
                                resizeAccum = Offset.Zero
                            },
                        )
                        ResizeEdge(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(ResizeGutter)
                                .zIndex(2f),
                            onResizeStart = {
                                isManipulating = true
                                resizeStartLayout = tab.layout
                                resizeAccum = Offset.Zero
                                onSelect()
                            },
                            onResize = { dx, dy ->
                                val start = resizeStartLayout ?: tab.layout
                                resizeAccum += Offset(dx, dy)
                                onPreviewLayout(layoutFromResize(start, resizeAccum))
                            },
                            onResizeEnd = {
                                isManipulating = false
                                if (resizeStartLayout != null) {
                                    onCommitLayout(tab.layout)
                                }
                                resizeStartLayout = null
                                resizeAccum = Offset.Zero
                            },
                        )
                        ResizeHandle(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .zIndex(3f),
                            onResizeStart = {
                                isManipulating = true
                                resizeStartLayout = tab.layout
                                resizeAccum = Offset.Zero
                                onSelect()
                            },
                            onResize = { dx, dy ->
                                val start = resizeStartLayout ?: tab.layout
                                resizeAccum += Offset(dx, dy)
                                onPreviewLayout(layoutFromResize(start, resizeAccum))
                            },
                            onResizeEnd = {
                                isManipulating = false
                                if (resizeStartLayout != null) {
                                    onCommitLayout(tab.layout)
                                }
                                resizeStartLayout = null
                                resizeAccum = Offset.Zero
                            },
                        )
                    }
                }
            }
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
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
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
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(tab.id, tab.windowState, tab.layout) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                )
            },
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
                onClick = {
                    onSelect()
                    onRefresh()
                },
                tint = Color(0xFF4A90D9),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(22.dp))
            }
            WindowControlButton(
                onClick = {
                    onSelect()
                    onToggleMaximize()
                },
                onLongClick = {
                    onSelect()
                    onMinimize()
                },
                tint = Color(0xFF34A853),
            ) {
                Icon(maxMinIcon, contentDescription = "Maximize or restore", modifier = Modifier.size(22.dp))
            }
            WindowControlButton(
                onClick = onClose,
                tint = Color(0xFFE8453C),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(22.dp))
            }

            Text(
                text = tab.title.ifBlank { tab.url },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowControlButton(
    onClick: () -> Unit,
    tint: Color,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
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
            color = tint.copy(alpha = 0.2f),
            modifier = Modifier.size(38.dp),
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
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onResizeStart() },
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
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun ResizeEdge(
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { onResizeStart() },
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