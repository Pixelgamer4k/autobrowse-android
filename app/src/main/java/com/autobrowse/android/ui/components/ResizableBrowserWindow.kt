package com.autobrowse.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion
import kotlin.math.max

private val ResizeHandleTouchSize = 48.dp
private val ResizeHandleVisualSize = 22.dp

@Composable
fun ResizableBrowserWindow(
    tab: BrowserTab,
    frame: BrowserWindowFrame,
    isActive: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    controller: BrowserController,
    onSelect: () -> Unit,
    onTabUpdate: (BrowserTab) -> Unit,
    onMoveWindow: (BrowserWindowLayout) -> Unit,
    onResizeWindow: (BrowserWindowLayout) -> Unit,
    onEndManipulation: () -> Unit,
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
    var dragAnchor by remember(tab.id) { mutableStateOf<BrowserWindowLayout?>(null) }
    var dragAccum by remember(tab.id) { mutableStateOf(Offset.Zero) }
    var resizeAnchor by remember(tab.id) { mutableStateOf<BrowserWindowLayout?>(null) }
    var resizeAccum by remember(tab.id) { mutableStateOf(Offset.Zero) }

    fun widthPxFromFraction(fraction: Float): Float = fraction * canvasWidthPx

    fun heightPxForWidth(widthPx: Float, minimized: Boolean): Float = when {
        minimized -> minimizedHeightPx
        frame.windowState == BrowserWindowState.MAXIMIZED -> frame.effectiveLayout().heightFraction * canvasHeightPx
        else -> BrowserWindowLayout.totalHeightPx(widthPx, titleBarHeightPx)
    }

    val effectiveLayout = frame.effectiveLayout()
    val baseWidthPx = widthPxFromFraction(effectiveLayout.widthFraction)
    val baseHeightPx = heightPxForWidth(baseWidthPx, frame.windowState == BrowserWindowState.MINIMIZED)

    val isResizing = isManipulating && resizeAnchor != null && frame.windowState == BrowserWindowState.NORMAL
    val resizedWidthPx = if (isResizing) {
        val startWidthPx = widthPxFromFraction(resizeAnchor!!.widthFraction)
        val delta = max(resizeAccum.x, resizeAccum.y * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
        (startWidthPx + delta).coerceIn(
            BrowserWindowLayout.MIN_FRACTION * canvasWidthPx,
            canvasWidthPx,
        )
    } else {
        baseWidthPx
    }
    val widthPx = if (isResizing) resizedWidthPx else baseWidthPx
    val heightPx = if (isResizing) {
        heightPxForWidth(resizedWidthPx, minimized = false)
    } else {
        baseHeightPx
    }

    val isDragging = isManipulating && dragAnchor != null
    val baseXPx = (effectiveLayout.offsetX * canvasWidthPx).coerceIn(0f, (canvasWidthPx - baseWidthPx).coerceAtLeast(0f))
    val baseYPx = (effectiveLayout.offsetY * canvasHeightPx).coerceIn(0f, (canvasHeightPx - baseHeightPx).coerceAtLeast(0f))
    val xPx = if (isDragging) baseXPx + dragAccum.x else baseXPx
    val yPx = if (isDragging) baseYPx + dragAccum.y else baseYPx

    val canResize = frame.windowState == BrowserWindowState.NORMAL && canvasWidthPx > 0f

    fun finishManipulation() {
        isManipulating = false
        dragAnchor = null
        dragAccum = Offset.Zero
        resizeAnchor = null
        resizeAccum = Offset.Zero
        onEndManipulation()
    }

    val elevation by animateFloatAsState(
        targetValue = if (isActive) 14f else 5f,
        animationSpec = Motion.springSmooth,
        label = "windowElevation",
    )
    val windowScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.985f,
        animationSpec = Motion.springSnappy,
        label = "windowScale",
    )

    Box(
        modifier = modifier
            .zIndex(if (isActive) 200f else tab.zIndex.toFloat())
            .graphicsLayer {
                scaleX = windowScale
                scaleY = windowScale
                translationX = xPx
                translationY = yPx
            }
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
                frame = frame,
                onRefresh = onRefresh,
                onToggleMaximize = onToggleMaximize,
                onMinimize = onMinimize,
                onClose = onClose,
                onSelect = onSelect,
                onDragStart = {
                    if (frame.windowState != BrowserWindowState.MAXIMIZED) {
                        isManipulating = true
                        dragAnchor = frame.layout
                        dragAccum = Offset.Zero
                        onSelect()
                    }
                },
                onDrag = { delta ->
                    if (frame.windowState == BrowserWindowState.MAXIMIZED) return@WindowTitleBar
                    dragAccum += delta
                },
                onDragEnd = {
                    val start = dragAnchor
                    if (start != null) {
                        onMoveWindow(
                            start.copy(
                                offsetX = start.offsetX + dragAccum.x / canvasWidthPx,
                                offsetY = start.offsetY + dragAccum.y / canvasHeightPx,
                            ).clamped(canvasWidthPx, canvasHeightPx, titleBarHeightPx),
                        )
                    }
                    finishManipulation()
                },
                isManipulating = isManipulating,
            )

            if (frame.windowState != BrowserWindowState.MINIMIZED) {
                val contentModifier = if (frame.windowState == BrowserWindowState.MAXIMIZED) {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(BrowserWindowLayout.CONTENT_ASPECT_RATIO)
                }
                Box(modifier = contentModifier) {
                    BrowserWebView(
                        tab = tab,
                        controller = controller,
                        onTabUpdate = onTabUpdate,
                        interactionEnabled = !isManipulating,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (canResize) {
            CornerResizeHandle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .zIndex(6f),
                onResizeStart = {
                    isManipulating = true
                    resizeAnchor = frame.layout
                    resizeAccum = Offset.Zero
                    onSelect()
                },
                onResize = { dx, dy ->
                    resizeAccum += Offset(dx, dy)
                },
                onResizeEnd = {
                    val start = resizeAnchor
                    if (start != null) {
                        val startWidthPx = widthPxFromFraction(start.widthFraction)
                        val delta = max(resizeAccum.x, resizeAccum.y * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
                        val newWidthFraction = (startWidthPx + delta) / canvasWidthPx
                        onResizeWindow(
                            start.copy(widthFraction = newWidthFraction)
                                .withAspectRatio(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
                                .clamped(canvasWidthPx, canvasHeightPx, titleBarHeightPx),
                        )
                    }
                    finishManipulation()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowTitleBar(
    tab: BrowserTab,
    frame: BrowserWindowFrame,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    isManipulating: Boolean,
) {
    val maxMinIcon = when (frame.windowState) {
        BrowserWindowState.MAXIMIZED -> Icons.Default.FullscreenExit
        BrowserWindowState.MINIMIZED -> Icons.Default.UnfoldMore
        BrowserWindowState.NORMAL -> Icons.Default.Fullscreen
    }

    Surface(
        color = if (isManipulating) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(tab.id, frame.windowState) {
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
            WindowControlButton(onClick = { onSelect(); onRefresh() }, tint = Color(0xFF4A90D9)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(22.dp))
            }
            WindowControlButton(
                onClick = { onSelect(); onToggleMaximize() },
                onLongClick = { onSelect(); onMinimize() },
                tint = Color(0xFF34A853),
            ) {
                Icon(maxMinIcon, contentDescription = "Maximize or restore", modifier = Modifier.size(22.dp))
            }
            WindowControlButton(onClick = onClose, tint = Color(0xFFE8453C)) {
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
    Box(
        modifier = Modifier
            .size(44.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
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
private fun CornerResizeHandle(
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gripColor = Color.White.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .size(ResizeHandleTouchSize)
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
        contentAlignment = Alignment.BottomEnd,
    ) {
        Canvas(modifier = Modifier.size(ResizeHandleVisualSize)) {
            val lineCount = 3
            val spacing = size.minDimension / 5f
            val strokeWidth = size.minDimension / 10f
            for (i in 0 until lineCount) {
                val offset = spacing * (i + 1.2f)
                drawLine(
                    color = gripColor,
                    start = Offset(size.width - offset, size.height),
                    end = Offset(size.width, size.height - offset),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}