package com.autobrowse.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion
import kotlin.math.abs

@Composable
fun WindowDragHandle(
    tabId: String,
    canDrag: Boolean,
    isGesturing: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val tapSlop = viewConfiguration.touchSlop * 0.65f

    Box(
        modifier = modifier
            .size(width = 72.dp, height = 40.dp)
            .pointerInput(tabId, canDrag) {
                if (!canDrag) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onTap()
                    }
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalMovement = 0f
                    var dragStarted = false
                    val pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break

                        val delta = change.position - change.previousPosition
                        if (delta.x != 0f || delta.y != 0f) {
                            totalMovement += abs(delta.x) + abs(delta.y)
                            if (!dragStarted && totalMovement > 2f) {
                                dragStarted = true
                                onDragStart()
                            }
                            if (dragStarted) {
                                change.consume()
                                onDrag(delta.x, delta.y)
                            }
                        }
                    }

                    when {
                        dragStarted -> onDragEnd()
                        totalMovement <= tapSlop -> onTap()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ThreeDotMenuButton(isGesturing = isGesturing)
    }
}

@Composable
fun ThreeDotMenuButton(
    isGesturing: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(6.dp, CircleShape),
        shape = CircleShape,
        color = Color(0xFF1B1B1F).copy(alpha = 0.88f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotAlpha = if (isGesturing) 0.7f else 1f
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color.White.copy(alpha = dotAlpha), CircleShape),
                )
            }
        }
    }
}

@Composable
fun WindowOptionsPopup(
    windowState: BrowserWindowState,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxMinLabel = when (windowState) {
        BrowserWindowState.MAXIMIZED -> "Restore"
        BrowserWindowState.MINIMIZED -> "Expand"
        BrowserWindowState.NORMAL -> "Maximize"
    }
    val maxMinIcon = when (windowState) {
        BrowserWindowState.MAXIMIZED -> Icons.Default.FullscreenExit
        BrowserWindowState.MINIMIZED -> Icons.Default.UnfoldMore
        BrowserWindowState.NORMAL -> Icons.Default.Fullscreen
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(Motion.tweenQuick) + scaleIn(
            animationSpec = Motion.springSnappy,
            initialScale = 0.86f,
            transformOrigin = TransformOrigin(0.5f, 0f),
        ),
        exit = fadeOut(Motion.tweenQuick) + scaleOut(
            animationSpec = Motion.tweenQuick,
            targetScale = 0.9f,
            transformOrigin = TransformOrigin(0.5f, 0f),
        ),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1B1B1F),
            shadowElevation = 12.dp,
            tonalElevation = 6.dp,
            modifier = Modifier.width(176.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                WindowMenuItem("Refresh", Icons.Default.Refresh, Color(0xFF4A90D9), onRefresh)
                WindowMenuItem(maxMinLabel, maxMinIcon, Color(0xFF34A853), onToggleMaximize)
                WindowMenuItem("Close", Icons.Default.Close, Color(0xFFE8453C), onClose)
            }
        }
    }
}

@Composable
fun WindowMenuScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { transition.targetState = visible }

    val alpha by animateFloatAsState(
        targetValue = if (transition.targetState) 0.22f else 0f,
        animationSpec = Motion.tweenQuick,
        label = "menuScrimAlpha",
    )

    if (transition.currentState || transition.targetState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(alpha)
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}

@Composable
private fun WindowMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.94f),
        )
    }
}