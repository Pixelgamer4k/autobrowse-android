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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autobrowse.android.domain.model.BrowserWindowState
import com.autobrowse.android.ui.theme.Motion

@Composable
fun WindowChromeWithMenu(
    windowState: BrowserWindowState,
    menuExpanded: Boolean,
    isManipulating: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRefresh: () -> Unit,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
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

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = if (isManipulating) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                ThreeDotMenuButton(onClick = onToggleMenu)
            }
        }

        AnimatedVisibility(
            visible = menuExpanded,
            enter = fadeIn(Motion.tweenQuick) + scaleIn(
                animationSpec = Motion.springSnappy,
                initialScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0f),
            ),
            exit = fadeOut(Motion.tweenQuick) + scaleOut(
                animationSpec = Motion.tweenQuick,
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(0.5f, 0f),
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 30.dp)
                .zIndex(12f),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1B1B1F),
                shadowElevation = 10.dp,
                tonalElevation = 6.dp,
                modifier = Modifier.width(168.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    WindowMenuItem(
                        label = "Refresh",
                        icon = Icons.Default.Refresh,
                        tint = Color(0xFF4A90D9),
                        onClick = {
                            onDismissMenu()
                            onRefresh()
                        },
                    )
                    WindowMenuItem(
                        label = maxMinLabel,
                        icon = maxMinIcon,
                        tint = Color(0xFF34A853),
                        onClick = {
                            onDismissMenu()
                            onToggleMaximize()
                        },
                    )
                    WindowMenuItem(
                        label = "Minimize",
                        icon = Icons.Default.UnfoldMore,
                        tint = Color(0xFFFFB74D),
                        onClick = {
                            onDismissMenu()
                            onMinimize()
                        },
                    )
                    WindowMenuItem(
                        label = "Close",
                        icon = Icons.Default.Close,
                        tint = Color(0xFFE8453C),
                        onClick = {
                            onDismissMenu()
                            onClose()
                        },
                    )
                }
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
        targetValue = if (transition.targetState) 0.18f else 0f,
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
                )
                .zIndex(10f),
        )
    }
}

@Composable
private fun ThreeDotMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Color.White.copy(alpha = 0.92f), CircleShape),
            )
        }
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}