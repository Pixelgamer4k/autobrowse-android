package com.autobrowse.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autobrowse.android.browser.BrowserController
import com.autobrowse.android.browser.ContentColorSampler
import com.autobrowse.android.browser.WindowGeometry
import com.autobrowse.android.domain.model.BrowserTab
import com.autobrowse.android.domain.model.BrowserWindowFrame
import com.autobrowse.android.domain.model.BrowserWindowLayout
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

private val WindowCornerRadius = 12.dp
private val CornerGripOutset = 8.dp
private val CornerGripStroke = 2.8.dp
private val CornerGripTouchSize = 48.dp
private val MenuPopupOffset = 22.dp
private val DefaultDotColor = Color(0xFFF2F2F2)

private data class GestureSnapshot(
    val xPx: Float,
    val yPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val offsetX: Float,
    val offsetY: Float,
)

private data class DisplayGeometry(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val layoutW: Float,
    val layoutH: Float,
    val resizeScale: Float,
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
    onClose: () -> Unit,
    onGoBack: () -> Unit = {},
    onGoForward: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val titleBarHeightPx = with(density) { BrowserWindowLayout.TITLE_BAR_HEIGHT_DP.dp.toPx() }
    val cornerRadiusPx = with(density) { WindowCornerRadius.toPx() }
    val canvas = remember(canvasWidthPx, canvasHeightPx, titleBarHeightPx) {
        WindowGeometry.Canvas(canvasWidthPx, canvasHeightPx, titleBarHeightPx)
    }

    val resolved = remember(
        frame.layout.offsetX,
        frame.layout.offsetY,
        frame.layout.widthFraction,
        frame.windowState,
        canvas,
    ) {
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
    var dotColor by remember(tab.id) { mutableStateOf(DefaultDotColor) }
    var settleJob by remember(tab.id) { mutableStateOf<Job?>(null) }

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
        gestureActive,
    ) {
        if (gestureActive) {
            settleJob?.cancel()
            return@LaunchedEffect
        }
        settleJob?.cancel()
        settleJob = coroutineScope {
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
        settleJob?.cancel()
        coroutineScope {
            launch { animX.snapTo(xPx) }
            launch { animY.snapTo(yPx) }
            launch { animW.snapTo(widthPx) }
            launch { animH.snapTo(heightPx) }
        }
    }

    val snap = gestureAnchor
    val dragging = gestureActive && gestureMode == "move" && snap != null
    val resizing = gestureActive && gestureMode == "resize" && snap != null

    val displayW = when {
        resizing -> {
            val delta = max(dragDx, dragDy * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
            (snap!!.widthPx + delta).coerceIn(
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
        dragging -> (snap!!.xPx + dragDx)
            .coerceIn(0f, (canvas.widthPx - displayW).coerceAtLeast(0f))
        else -> animX.value
    }
    val displayY = when {
        dragging -> (snap!!.yPx + dragDy)
            .coerceIn(0f, (canvas.heightPx - displayH).coerceAtLeast(0f))
        else -> animY.value
    }
    val layoutW = if (resizing && snap != null) snap.widthPx else displayW
    val layoutH = if (resizing && snap != null) snap.heightPx else displayH
    val resizeScale = if (resizing && snap != null && snap.widthPx > 0f) displayW / snap.widthPx else 1f

    val windowWidthDp = with(density) { layoutW.toDp() }
    val useCompactChrome = windowWidthDp < 200.dp

    val canResize = frame.windowState == BrowserWindowState.NORMAL && canvas.widthPx > 0f
    val canDrag = frame.windowState == BrowserWindowState.NORMAL
    val webInteractionEnabled = !gestureActive && !menuExpanded

    val elevation by animateFloatAsState(
        targetValue = if (isActive) 14f else 5f,
        animationSpec = Motion.springSmooth,
        label = "windowElevation",
    )
    val windowScale by animateFloatAsState(
        targetValue = when {
            gestureActive -> 1.02f
            isActive -> 1f
            else -> 0.985f
        },
        animationSpec = Motion.springSnappy,
        label = "windowScale",
    )

    fun beginMoveGesture() {
        if (!canDrag) return
        menuExpanded = false
        settleJob?.cancel()
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

    fun endMoveGesture() {
        val anchor = gestureAnchor
        val mode = gestureMode
        val dx = dragDx
        val dy = dragDy
        scope.launch {
            if (mode == "move" && anchor != null && (dx != 0f || dy != 0f)) {
                val finalW = anchor.widthPx
                val finalH = anchor.heightPx
                val finalX = (anchor.xPx + dx)
                    .coerceIn(0f, (canvas.widthPx - finalW).coerceAtLeast(0f))
                val finalY = (anchor.yPx + dy)
                    .coerceIn(0f, (canvas.heightPx - finalH).coerceAtLeast(0f))
                snapAnimToDisplay(finalX, finalY, finalW, finalH)
                onCommitGeometry(
                    WindowGeometry.moved(
                        base = resolved.layout,
                        widthPx = finalW,
                        offsetX = if (canvas.widthPx > 0f) finalX / canvas.widthPx else anchor.offsetX,
                        offsetY = if (canvas.heightPx > 0f) finalY / canvas.heightPx else anchor.offsetY,
                        canvas = canvas,
                    ),
                )
            }
            resetGesture()
        }
    }

    Box(
        modifier = modifier
            .zIndex(if (isActive) 200f + tab.zIndex else tab.zIndex.toFloat())
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
        WindowBody(
            tab = tab,
            frame = frame,
            controller = controller,
            onTabUpdate = onTabUpdate,
            interactionEnabled = webInteractionEnabled,
            sampleContentColor = frame.windowState != BrowserWindowState.MINIMIZED,
            onContentColorSampled = { sampled ->
                dotColor = ContentColorSampler.smoothToward(dotColor, sampled)
            },
            elevation = elevation,
            isActive = isActive,
        )

        WindowDragHandle(
            tabId = tab.id,
            canDrag = canDrag,
            isGesturing = gestureActive && gestureMode == "move",
            dotColor = dotColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(min = 48.dp)
                .zIndex(12f),
            onDragStart = ::beginMoveGesture,
            onDrag = { dx, dy ->
                if (gestureMode == "move") {
                    dragDx += dx
                    dragDy += dy
                }
            },
            onDragEnd = ::endMoveGesture,
            onTap = {
                menuExpanded = !menuExpanded
                onSelect()
            },
        )

        if (menuExpanded) {
            WindowMenuScrim(
                visible = true,
                onDismiss = { menuExpanded = false },
                modifier = Modifier.matchParentSize().zIndex(14f),
            )
            if (useCompactChrome) {
                WindowCompactControls(
                    windowState = frame.windowState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 30.dp)
                        .zIndex(20f),
                    onGoBack = {
                        menuExpanded = false
                        onSelect()
                        onGoBack()
                    },
                    onGoForward = {
                        menuExpanded = false
                        onSelect()
                        onGoForward()
                    },
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
                    onClose = {
                        menuExpanded = false
                        onClose()
                    },
                )
            } else {
                WindowOptionsPopup(
                    windowState = frame.windowState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = MenuPopupOffset)
                        .zIndex(20f),
                    onGoBack = {
                        menuExpanded = false
                        onSelect()
                        onGoBack()
                    },
                    onGoForward = {
                        menuExpanded = false
                        onSelect()
                        onGoForward()
                    },
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
                    onClose = {
                        menuExpanded = false
                        onClose()
                    },
                )
            }
        }

        if (canResize) {
            FrameCornerResizeGrip(
                tabId = tab.id,
                cornerRadiusPx = cornerRadiusPx,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = CornerGripOutset, y = CornerGripOutset)
                    .size(CornerGripTouchSize)
                    .zIndex(8f),
                onResizeStart = {
                    menuExpanded = false
                    settleJob?.cancel()
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
                    val anchor = gestureAnchor
                    val mode = gestureMode
                    val dx = dragDx
                    val dy = dragDy
                    scope.launch {
                        if (mode == "resize" && anchor != null && (dx != 0f || dy != 0f)) {
                            val delta = max(dx, dy * BrowserWindowLayout.CONTENT_ASPECT_RATIO)
                            val finalW = (anchor.widthPx + delta).coerceIn(
                                BrowserWindowLayout.MIN_FRACTION * canvas.widthPx,
                                canvas.widthPx,
                            )
                            val finalH = BrowserWindowLayout.totalHeightPx(finalW, canvas.titleBarPx)
                            snapAnimToDisplay(anchor.xPx, anchor.yPx, finalW, finalH)
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
private fun WindowBody(
    tab: BrowserTab,
    frame: BrowserWindowFrame,
    controller: BrowserController,
    onTabUpdate: (BrowserTab) -> Unit,
    interactionEnabled: Boolean,
    sampleContentColor: Boolean,
    onContentColorSampled: (Color) -> Unit,
    elevation: Float,
    isActive: Boolean,
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
        if (frame.windowState != BrowserWindowState.MINIMIZED) {
            BrowserWebView(
                tab = tab,
                controller = controller,
                onTabUpdate = onTabUpdate,
                interactionEnabled = interactionEnabled,
                sampleContentColor = sampleContentColor,
                onContentColorSampled = onContentColorSampled,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    val gripOutsetPx = with(density) { CornerGripOutset.toPx() }
    val strokePx = with(density) { CornerGripStroke.toPx() }
    val gripColor = Color.White.copy(alpha = 0.92f)

    Box(
        modifier = modifier.pointerInput(tabId) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onResizeStart()
                val pointerId = down.id

                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break

                    val delta = change.position - change.previousPosition
                    if (delta.x != 0f || delta.y != 0f) {
                        change.consume()
                        onResize(delta.x, delta.y)
                    }
                }
                onResizeEnd()
            }
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val corner = Offset(width - gripOutsetPx, height - gripOutsetPx)
            val radius = cornerRadiusPx.coerceAtMost(minOf(width, height) * 0.45f)

            drawArc(
                color = gripColor,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(corner.x - radius * 2f, corner.y - radius * 2f),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}