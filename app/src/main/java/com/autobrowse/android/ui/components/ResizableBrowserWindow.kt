package com.autobrowse.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion
import kotlin.math.max

private val WindowCornerRadius = 12.dp
private val CornerGripExtent = 26.dp
private val CornerGripTouchSize = 56.dp

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
    val cornerRadiusPx = with(density) { WindowCornerRadius.toPx() }

    var isManipulating by remember(tab.id) { mutableStateOf(false) }
    var dragAnchor by remember(tab.id) { mutableStateOf<BrowserWindowLayout?>(null) }
    var dragAccum by remember(tab.id) { mutableStateOf(Offset.Zero) }
    var resizeAnchor by remember(tab.id) { mutableStateOf<BrowserWindowLayout?>(null) }
    var resizeAccum by remember(tab.id) { mutableStateOf(Offset.Zero) }
    var menuExpanded by remember(tab.id) { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (!isActive) menuExpanded = false
    }

    fun normalizedLayout(source: BrowserWindowLayout): BrowserWindowLayout = when (frame.windowState) {
        BrowserWindowState.NORMAL -> source.normalized(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
        else -> source
    }

    fun widthPxFromFraction(fraction: Float): Float = fraction * canvasWidthPx

    fun heightPxForWidth(widthPx: Float, minimized: Boolean): Float = when {
        minimized -> minimizedHeightPx
        frame.windowState == BrowserWindowState.MAXIMIZED -> frame.effectiveLayout().heightFraction * canvasHeightPx
        else -> BrowserWindowLayout.totalHeightPx(widthPx, titleBarHeightPx)
    }

    val storedLayout = frame.effectiveLayout()
    val baseLayout = if (frame.windowState == BrowserWindowState.NORMAL) {
        normalizedLayout(storedLayout)
    } else {
        storedLayout
    }

    val baseWidthPx = widthPxFromFraction(baseLayout.widthFraction)
    val baseHeightPx = heightPxForWidth(baseWidthPx, frame.windowState == BrowserWindowState.MINIMIZED)

    val isResizing = isManipulating && resizeAnchor != null && frame.windowState == BrowserWindowState.NORMAL
    val resizedWidthPx = if (isResizing) {
        val anchorLayout = normalizedLayout(resizeAnchor!!)
        val startWidthPx = widthPxFromFraction(anchorLayout.widthFraction)
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

    val isDragging = isManipulating && dragAnchor != null && resizeAnchor == null
    val baseXPx = baseLayout.offsetX * canvasWidthPx
    val baseYPx = baseLayout.offsetY * canvasHeightPx
    val xPx = if (isDragging) {
        (baseXPx + dragAccum.x).coerceIn(0f, (canvasWidthPx - widthPx).coerceAtLeast(0f))
    } else {
        baseXPx.coerceIn(0f, (canvasWidthPx - widthPx).coerceAtLeast(0f))
    }
    val yPx = if (isDragging) {
        (baseYPx + dragAccum.y).coerceIn(0f, (canvasHeightPx - heightPx).coerceAtLeast(0f))
    } else {
        baseYPx.coerceIn(0f, (canvasHeightPx - heightPx).coerceAtLeast(0f))
    }

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
            .height(with(density) { heightPx.toDp() }),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation.dp, RoundedCornerShape(WindowCornerRadius))
                .clip(RoundedCornerShape(WindowCornerRadius))
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(WindowCornerRadius),
                )
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                WindowTitleBar(
                    tabId = tab.id,
                    frame = frame,
                    menuExpanded = menuExpanded,
                    isManipulating = isManipulating,
                    onToggleMenu = {
                        menuExpanded = !menuExpanded
                        onSelect()
                    },
                    onDismissMenu = { menuExpanded = false },
                    onRefresh = {
                        menuExpanded = false
                        onSelect()
                        onRefresh()
                    },
                    onToggleMaximize = {
                        menuExpanded = false
                        onSelect()
                        onToggleMaximize()
                    },
                    onMinimize = {
                        menuExpanded = false
                        onSelect()
                        onMinimize()
                    },
                    onClose = {
                        menuExpanded = false
                        onClose()
                    },
                    onDragStart = {
                        if (frame.windowState != BrowserWindowState.MAXIMIZED) {
                            menuExpanded = false
                            isManipulating = true
                            resizeAnchor = null
                            resizeAccum = Offset.Zero
                            dragAnchor = baseLayout
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
                        if (start != null && dragAccum != Offset.Zero) {
                            onMoveWindow(
                                BrowserWindowLayout.fromDisplayedSize(
                                    widthPx = widthPx,
                                    offsetX = start.offsetX + dragAccum.x / canvasWidthPx,
                                    offsetY = start.offsetY + dragAccum.y / canvasHeightPx,
                                    canvasWidthPx = canvasWidthPx,
                                    canvasHeightPx = canvasHeightPx,
                                    titleBarHeightPx = titleBarHeightPx,
                                ),
                            )
                        }
                        finishManipulation()
                    },
                )

                if (frame.windowState != BrowserWindowState.MINIMIZED) {
                    val contentModifier = if (frame.windowState == BrowserWindowState.MAXIMIZED) {
                        Modifier.weight(1f).fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(BrowserWindowLayout.CONTENT_ASPECT_RATIO)
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
        }

        WindowMenuScrim(
            visible = menuExpanded,
            onDismiss = { menuExpanded = false },
            modifier = Modifier.matchParentSize(),
        )

        if (canResize) {
            FrameCornerResizeGrip(
                tabId = tab.id,
                cornerRadiusPx = cornerRadiusPx,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(CornerGripTouchSize)
                    .zIndex(8f),
                onResizeStart = {
                    menuExpanded = false
                    isManipulating = true
                    dragAnchor = null
                    dragAccum = Offset.Zero
                    resizeAnchor = baseLayout
                    resizeAccum = Offset.Zero
                    onSelect()
                },
                onResize = { dx, dy ->
                    resizeAccum += Offset(dx, dy)
                },
                onResizeEnd = {
                    val start = resizeAnchor
                    if (start != null && resizeAccum != Offset.Zero) {
                        val startWidthPx = widthPxFromFraction(start.widthFraction)
                        val delta = max(resizeAccum.x, resizeAccum.y * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
                        val newWidthFraction = (startWidthPx + delta) / canvasWidthPx
                        onResizeWindow(
                            BrowserWindowLayout.fromDisplayedSize(
                                widthPx = widthPx,
                                offsetX = start.offsetX,
                                offsetY = start.offsetY,
                                canvasWidthPx = canvasWidthPx,
                                canvasHeightPx = canvasHeightPx,
                                titleBarHeightPx = titleBarHeightPx,
                            ),
                        )
                    }
                    finishManipulation()
                },
            )
        }
    }
}

@Composable
private fun WindowTitleBar(
    tabId: String,
    frame: BrowserWindowFrame,
    menuExpanded: Boolean,
    isManipulating: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val dragModifier = Modifier.pointerInput(tabId, frame.windowState) {
        detectDragGestures(
            onDragStart = { onDragStart() },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).then(dragModifier))
        WindowChromeWithMenu(
            windowState = frame.windowState,
            menuExpanded = menuExpanded,
            isManipulating = isManipulating,
            onToggleMenu = onToggleMenu,
            onDismissMenu = onDismissMenu,
            onRefresh = onRefresh,
            onToggleMaximize = onToggleMaximize,
            onMinimize = onMinimize,
            onClose = onClose,
        )
        Box(modifier = Modifier.weight(1f).then(dragModifier))
    }
}

@Composable
private fun FrameCornerResizeGrip(
    tabId: String,
    cornerRadiusPx: Float,
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gripExtentPx = with(density) { CornerGripExtent.toPx() }
    val gripColor = Color.White.copy(alpha = 0.88f)
    val gripHighlight = Color.White.copy(alpha = 0.45f)

    Box(
        modifier = modifier.pointerInput(tabId) {
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val radius = cornerRadiusPx.coerceAtMost(minOf(width, height) * 0.45f)
            val extent = gripExtentPx.coerceAtMost(minOf(width, height) * 0.9f)

            val cornerPath = Path().apply {
                moveTo(width - extent, height)
                lineTo(width - radius, height)
                arcTo(
                    rect = Rect(
                        offset = Offset(width - radius * 2f, height - radius * 2f),
                        size = Size(radius * 2f, radius * 2f),
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false,
                )
                lineTo(width, height - extent)
                lineTo(width - extent * 0.42f, height - extent * 0.42f)
                close()
            }

            clipPath(cornerPath) {
                drawRoundRect(
                    color = gripHighlight,
                    topLeft = Offset(width - extent, height - extent),
                    size = Size(extent, extent),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }

            drawPath(
                path = cornerPath,
                color = gripColor,
                style = Stroke(width = 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            drawLine(
                color = gripColor,
                start = Offset(width - extent, height - 1.5f),
                end = Offset(width - radius, height - 1.5f),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = gripColor,
                start = Offset(width - 1.5f, height - extent),
                end = Offset(width - 1.5f, height - radius),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}