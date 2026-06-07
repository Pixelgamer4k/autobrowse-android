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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.autobrowse.android.browser.WindowGeometry
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion
import kotlin.math.max

private val WindowCornerRadius = 12.dp
private val CornerGripExtent = 26.dp
private val CornerGripTouchSize = 56.dp

private data class GestureSnapshot(
    val offsetX: Float,
    val offsetY: Float,
    val widthPx: Float,
)

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
    onCommitGeometry: (BrowserWindowLayout) -> Unit,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val titleBarHeightPx = with(density) { BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp.toPx() }
    val cornerRadiusPx = with(density) { WindowCornerRadius.toPx() }
    val canvas = remember(canvasWidthPx, canvasHeightPx, titleBarHeightPx) {
        WindowGeometry.Canvas(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
    }

    val resolved = remember(frame, canvas) { WindowGeometry.resolve(frame, canvas) }

    var menuExpanded by remember(tab.id) { mutableStateOf(false) }
    var gestureActive by remember(tab.id) { mutableStateOf(false) }
    var gestureMode by remember(tab.id) { mutableStateOf<String?>(null) }
    var dragDx by remember(tab.id) { mutableFloatStateOf(0f) }
    var dragDy by remember(tab.id) { mutableFloatStateOf(0f) }
    var gestureAnchor by remember(tab.id) { mutableStateOf<GestureSnapshot?>(null) }

    LaunchedEffect(isActive) {
        if (!isActive) menuExpanded = false
    }

    fun resetGesture() {
        gestureActive = false
        gestureMode = null
        dragDx = 0f
        dragDy = 0f
        gestureAnchor = null
    }

    val anchor = gestureAnchor
    val isDragging = gestureActive && gestureMode == "move" && anchor != null
    val isResizing = gestureActive && gestureMode == "resize" && anchor != null

    val widthPx = when {
        isResizing && anchor != null -> {
            val delta = max(dragDx, dragDy * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
            (anchor.widthPx + delta).coerceIn(0f, canvas.widthPx)
        }
        else -> resolved.widthPx
    }
    val heightPx = when (frame.windowState) {
        BrowserWindowState.MINIMIZED -> canvas.titleBarPx.coerceAtLeast(1f)
        BrowserWindowState.MAXIMIZED -> resolved.heightPx
        BrowserWindowState.NORMAL -> BrowserWindowLayout.totalHeightPx(widthPx, canvas.titleBarPx)
    }

    val xPx = when {
        isDragging && anchor != null -> {
            (anchor.offsetX * canvas.widthPx + dragDx)
                .coerceIn(0f, (canvas.widthPx - widthPx).coerceAtLeast(0f))
        }
        else -> resolved.xPx
    }
    val yPx = when {
        isDragging && anchor != null -> {
            (anchor.offsetY * canvas.heightPx + dragDy)
                .coerceIn(0f, (canvas.heightPx - heightPx).coerceAtLeast(0f))
        }
        else -> resolved.yPx
    }

    val canResize = frame.windowState == BrowserWindowState.NORMAL && canvas.widthPx > 0f
    val webInteractionEnabled = !gestureActive && !menuExpanded

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
                    windowState = frame.windowState,
                    isGesturing = gestureActive,
                    onDragStart = {
                        if (frame.windowState != BrowserWindowState.MAXIMIZED) {
                            menuExpanded = false
                            gestureActive = true
                            gestureMode = "move"
                            gestureAnchor = GestureSnapshot(
                                offsetX = resolved.layout.offsetX,
                                offsetY = resolved.layout.offsetY,
                                widthPx = resolved.widthPx,
                            )
                            dragDx = 0f
                            dragDy = 0f
                            onSelect()
                        }
                    },
                    onDrag = { dx, dy ->
                        if (gestureMode == "move") {
                            dragDx += dx
                            dragDy += dy
                        }
                    },
                    onDragEnd = {
                        val snap = gestureAnchor
                        if (gestureMode == "move" && snap != null && (dragDx != 0f || dragDy != 0f)) {
                            onCommitGeometry(
                                WindowGeometry.moved(
                                    base = resolved.layout,
                                    widthPx = widthPx,
                                    offsetX = snap.offsetX + dragDx / canvas.widthPx,
                                    offsetY = snap.offsetY + dragDy / canvas.heightPx,
                                    canvas = canvas,
                                ),
                            )
                        }
                        resetGesture()
                    },
                    onToggleMenu = {
                        menuExpanded = !menuExpanded
                        onSelect()
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
                            interactionEnabled = webInteractionEnabled,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (menuExpanded) {
            WindowMenuScrim(
                visible = true,
                onDismiss = { menuExpanded = false },
                modifier = Modifier.matchParentSize().zIndex(14f),
            )
            WindowOptionsPopup(
                windowState = frame.windowState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 34.dp)
                    .zIndex(20f),
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
            )
        }

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
                    gestureActive = true
                    gestureMode = "resize"
                    gestureAnchor = GestureSnapshot(
                        offsetX = resolved.layout.offsetX,
                        offsetY = resolved.layout.offsetY,
                        widthPx = resolved.widthPx,
                    )
                    dragDx = 0f
                    dragDy = 0f
                    onSelect()
                },
                onResize = { dx, dy ->
                    if (gestureMode == "resize") {
                        dragDx += dx
                        dragDy += dy
                    }
                },
                onResizeEnd = {
                    val snap = gestureAnchor
                    if (gestureMode == "resize" && snap != null && (dragDx != 0f || dragDy != 0f)) {
                        onCommitGeometry(
                            WindowGeometry.resized(
                                base = resolved.layout,
                                widthPx = widthPx,
                                canvas = canvas,
                            ),
                        )
                    }
                    resetGesture()
                },
            )
        }
    }
}

@Composable
private fun WindowTitleBar(
    tabId: String,
    windowState: BrowserWindowState,
    isGesturing: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleMenu: () -> Unit,
) {
    fun dragHandle(side: String): Modifier {
        if (windowState == BrowserWindowState.MAXIMIZED) return Modifier
        return Modifier.pointerInput(tabId, side) {
            detectDragGestures(
                onDragStart = { onDragStart() },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() },
                onDrag = { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                },
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().then(dragHandle("left")))
        ThreeDotMenuButton(
            onClick = onToggleMenu,
            isGesturing = isGesturing,
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight().then(dragHandle("right")))
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