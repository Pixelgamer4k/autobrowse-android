package com.autobrowse.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var dragOffset by remember(tab.id) { mutableStateOf(IntOffset.Zero) }
    var resizeDelta by remember(tab.id) { mutableStateOf(Pair(0f, 0f)) }

    val layout = tab.layout
    val widthPx = (layout.widthFraction * canvasWidthPx + resizeDelta.first).coerceAtLeast(
        canvasWidthPx * BrowserWindowLayout.MIN_FRACTION,
    )
    val heightPx = (layout.heightFraction * canvasHeightPx + resizeDelta.second).coerceAtLeast(
        canvasHeightPx * BrowserWindowLayout.MIN_FRACTION,
    )
    val xPx = (layout.offsetX * canvasWidthPx + dragOffset.x).coerceIn(
        0f,
        (canvasWidthPx - widthPx).coerceAtLeast(0f),
    )
    val yPx = (layout.offsetY * canvasHeightPx + dragOffset.y).coerceIn(
        0f,
        (canvasHeightPx - heightPx).coerceAtLeast(0f),
    )

    val elevation by animateFloatAsState(
        targetValue = if (isActive) 12f else 4f,
        animationSpec = Motion.springSmooth,
        label = "windowElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.98f,
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
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WindowTitleBar(
                tab = tab,
                isActive = isActive,
                onDrag = { delta ->
                    dragOffset = IntOffset(
                        dragOffset.x + delta.x.roundToInt(),
                        dragOffset.y + delta.y.roundToInt(),
                    )
                },
                onDragEnd = {
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
                },
                onTap = onSelect,
            )

            BrowserWebView(
                tab = tab,
                controller = controller,
                onTabUpdate = onTabUpdate,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
        }

        ResizeHandle(
            modifier = Modifier.align(Alignment.BottomEnd),
            onResize = { dx, dy ->
                resizeDelta = resizeDelta.first + dx to resizeDelta.second + dy
            },
            onResizeEnd = {
                val newLayout = BrowserWindowLayout(
                    offsetX = layout.offsetX,
                    offsetY = layout.offsetY,
                    widthFraction = (widthPx / canvasWidthPx).coerceIn(
                        BrowserWindowLayout.MIN_FRACTION,
                        BrowserWindowLayout.MAX_FRACTION,
                    ),
                    heightFraction = (heightPx / canvasHeightPx).coerceIn(
                        BrowserWindowLayout.MIN_FRACTION,
                        BrowserWindowLayout.MAX_FRACTION,
                    ),
                ).clamped()
                resizeDelta = 0f to 0f
                onLayoutChange(newLayout)
            },
        )
    }
}

@Composable
private fun WindowTitleBar(
    tab: BrowserTab,
    isActive: Boolean,
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(tab.id) {
                detectDragGestures(
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
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                TrafficDot(Color(0xFFFF5F57))
                TrafficDot(Color(0xFFFEBC2E))
                TrafficDot(Color(0xFF28C840))
            }
            Icon(
                Icons.Default.DesktopWindows,
                contentDescription = "Desktop mode",
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Desktop",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun TrafficDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ResizeHandle(
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
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
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        )
    }
}