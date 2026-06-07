package com.autobrowse.android.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

private val WindowCornerRadius = 12.dp
private val CornerGripExtent = 26.dp
private val CornerGripTouchSize = 56.dp

private data class GestureSnapshot(
    val xPx: Float,
    val yPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val offsetX: Float,
    val offsetY: Float,
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
    val scope = rememberCoroutineScope()
    val titleBarHeightPx = with(density) { BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp.toPx() }
    val cornerRadiusPx = with(density) { WindowCornerRadius.toPx() }
    val canvas = remember(canvasWidthPx, canvasHeightPx, titleBarHeightPx) {
        WindowGeometry.Canvas(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
    }

    val resolved = remember(frame.layout, frame.windowState, canvas) {
        WindowGeometry.resolve(frame, canvas)
    }

    val animX = remember(tab.id) { Animatable(resolved.xPx) }
    val animY = remember(tab.id) { Animatable(resolved.yPx) }
    val animW = remember(tab.id) { Animatable(resolved.widthPx) }
    val animH = remember(tab.id) { Animatable(resolved.heightPx) }

    var menuExpanded by remember(tab.id) { mutableStateOf(false) }
    var gestureActive by remember(tab.id) { mutableStateOf(false) }
    var gestureMode by remember(tab.id) { mutableStateOf<String?>(null) }
    var dragDx by remember(tab.id) { mutableFloatStateOf(0f) }
    var dragDy by remember(tab.id) { mutableFloatStateOf(0f) }
    var gestureAnchor by remember(tab.id) { mutableStateOf<GestureSnapshot?>(null) }

    LaunchedEffect(isActive) {
        if (!isActive) menuExpanded = false
    }

    LaunchedEffect(
        frame.layout.offsetX,
        frame.layout.offsetY,
        frame.layout.widthFraction,
        frame.windowState,
        canvasWidthPx,
        canvasHeightPx,
    ) {
        if (gestureActive) return@LaunchedEffect
        coroutineScope {
            launch { animX.animateTo(resolved.xPx, Motion.springWindow) }
            launch { animY.animateTo(resolved.yPx, Motion.springWindow) }
            launch { animW.animateTo(resolved.widthPx, Motion.springWindowSettle) }
            launch { animH.animateTo(resolved.heightPx, Motion.springWindowSettle) }
        }
    }

    fun resetGesture() {
        gestureActive = false
        gestureMode = null
        dragDx = 0f
        dragDy = 0f
        gestureAnchor = null
    }

    suspend fun snapAnimToDisplay(xPx: Float, yPx: Float, widthPx: Float, heightPx: Float) {
        coroutineScope {
            launch { animX.snapTo(xPx) }
            launch { animY.snapTo(yPx) }
            launch { animW.snapTo(widthPx) }
            launch { animH.snapTo(heightPx) }
        }
    }

    val anchor = gestureAnchor
    val isDragging = gestureActive && gestureMode == "move" && anchor != null
    val isResizing = gestureActive && gestureMode == "resize" && anchor != null

    val displayW = when {
        isResizing -> {
            val delta = max(dragDx, dragDy * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
            (anchor!!.widthPx + delta).coerceIn(
                BrowserWindowLayout.MIN_FRACTION * canvas.widthPx,
                canvas.widthPx,
            )
        }
        else -> animW.value
    }
    val displayH = when (frame.windowState) {
        BrowserWindowState.MINIMIZED -> canvas.titleBarPx.coerceAtLeast(1f)
        BrowserWindowState.MAXIMIZED -> animH.value
        BrowserWindowState.NORMAL -> BrowserWindowLayout.totalHeightPx(displayW, canvas.titleBarPx)
    }
    val displayX = when {
        isDragging -> {
            (anchor!!.xPx + dragDx)
                .coerceIn(0f, (canvas.widthPx - displayW).coerceAtLeast(0f))
        }
        else -> animX.value
    }
    val displayY = when {
        isDragging -> {
            (anchor!!.yPx + dragDy)
                .coerceIn(0f, (canvas.heightPx - displayH).coerceAtLeast(0f))
        }
        else -> animY.value
    }

    val layoutW = if (isResizing && anchor != null) anchor.widthPx else displayW
    val layoutH = if (isResizing && anchor != null) anchor.heightPx else displayH
    val resizeScale = if (isResizing && anchor != null && anchor.widthPx > 0f) {
        displayW / anchor.widthPx
    } else {
        1f
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
                translationX = displayX
                translationY = displayY
                scaleX = resizeScale * windowScale
                scaleY = resizeScale * windowScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .width(with(density) { layoutW.toDp() })
            .height(with(density) { layoutH.toDp() }),
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
                                xPx = animX.value,
                                yPx = animY.value,
                                widthPx = animW.value,
                                heightPx = animH.value,
                                offsetX = resolved.layout.offsetX,
                                offsetY = resolved.layout.offsetY,
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
                        val mode = gestureMode
                        val dx = dragDx
                        val dy = dragDy
                        scope.launch {
                            if (mode == "move" && snap != null && (dx != 0f || dy != 0f)) {
                                val finalW = snap.widthPx
                                val finalH = snap.heightPx
                                val finalX = (snap.xPx + dx)
                                    .coerceIn(0f, (canvas.widthPx - finalW).coerceAtLeast(0f))
                                val finalY = (snap.yPx + dy)
                                    .coerceIn(0f, (canvas.heightPx - finalH).coerceAtLeast(0f))
                                snapAnimToDisplay(finalX, finalY, finalW, finalH)
                                onCommitGeometry(
                                    WindowGeometry.moved(
                                        base = resolved.layout,
                                        widthPx = finalW,
                                        offsetX = if (canvas.widthPx > 0f) finalX / canvas.widthPx else snap.offsetX,
                                        offsetY = if (canvas.heightPx > 0f) finalY / canvas.heightPx else snap.offsetY,
                                        canvas = canvas,
                                    ),
                                )
                            }
                            resetGesture()
                        }
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
                        xPx = animX.value,
                        yPx = animY.value,
                        widthPx = animW.value,
                        heightPx = animH.value,
                        offsetX = resolved.layout.offsetX,
                        offsetY = resolved.layout.offsetY,
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
                    val mode = gestureMode
                    val dx = dragDx
                    val dy = dragDy
                    scope.launch {
                        if (mode == "resize" && snap != null && (dx != 0f || dy != 0f)) {
                            val delta = max(dx, dy * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
                            val finalW = (snap.widthPx + delta).coerceIn(
                                BrowserWindowLayout.MIN_FRACTION * canvas.widthPx,
                                canvas.widthPx,
                            )
                            val finalH = BrowserWindowLayout.totalHeightPx(finalW, canvas.titleBarPx)
                            snapAnimToDisplay(snap.xPx, snap.yPx, finalW, finalH)
                            onCommitGeometry(
                                WindowGeometry.resized(
                                    base = resolved.layout,
                                    widthPx = finalW,
                                    canvas = canvas,
                                ),
                            )
                        }
                        resetGesture()
                    }
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