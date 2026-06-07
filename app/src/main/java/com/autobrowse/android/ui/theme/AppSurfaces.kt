package com.autobrowse.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppGradients {
    val browserSection = Brush.verticalGradient(
        0f to Color(0xFF1A1A26),
        0.35f to Color(0xFF101018),
        0.72f to Color(0xFF0A0A12),
        1f to Color(0xFF06060C),
    )

    val chatSection = Brush.verticalGradient(
        0f to Color(0xFF14141C),
        0.4f to Color(0xFF0C0C12),
        0.75f to Color(0xFF060608),
        1f to Color(0xFF000000),
    )

    val composerDock = Brush.verticalGradient(
        0f to Color(0xFF2A2A36),
        0.25f to Color(0xFF1E1E28),
        0.65f to Color(0xFF12121A),
        1f to Color(0xFF08080E),
    )

    val composerInput = Brush.linearGradient(
        0f to Color(0xFF3A3A48),
        0.45f to Color(0xFF2A2A36),
        1f to Color(0xFF1C1C26),
    )

    val chatHeader = Brush.horizontalGradient(
        0f to Color(0xFF1A1A24).copy(alpha = 0.6f),
        0.35f to Color(0xFF323244).copy(alpha = 0.95f),
        0.65f to Color(0xFF2A2A38).copy(alpha = 0.9f),
        1f to Color(0xFF1A1A24).copy(alpha = 0.6f),
    )

    val userBubble = Brush.linearGradient(
        0f to Color(0xFF48485A),
        0.55f to Color(0xFF363646),
        1f to Color(0xFF282834),
    )

    val agentBubble = Brush.linearGradient(
        0f to Color(0xFF1E1E2A).copy(alpha = 0.85f),
        1f to Color(0xFF121218).copy(alpha = 0.55f),
    )

    val browserCanvas = Brush.radialGradient(
        colors = listOf(Color(0xFF242430), Color(0xFF101018), Color(0xFF08080E)),
        radius = 1000f,
    )

    val tabStrip = Brush.verticalGradient(
        0f to Color(0xFF242430).copy(alpha = 0.85f),
        1f to Color(0xFF14141C).copy(alpha = 0.5f),
    )

    val tabSelected = Brush.linearGradient(
        0f to Color(0xFF3A3A4A),
        1f to Color(0xFF282834),
    )

    val tabIdle = Brush.linearGradient(
        0f to Color(0xFF22222C).copy(alpha = 0.7f),
        1f to Color(0xFF181820).copy(alpha = 0.45f),
    )

    val toolbar = Brush.verticalGradient(
        0f to Color(0xFF1C1C26),
        1f to Color(0xFF0E0E16),
    )

    val urlField = Brush.linearGradient(
        0f to Color(0xFF2A2A36),
        1f to Color(0xFF1A1A22),
    )

    val separatorLine = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.12f to Color(0xFF6B7FD7).copy(alpha = 0.22f),
        0.35f to Color(0xFF9A9AAA).copy(alpha = 0.32f),
        0.5f to Color(0xFFE8E8F0).copy(alpha = 0.22f),
        0.65f to Color(0xFF9A9AAA).copy(alpha = 0.32f),
        0.88f to Color(0xFF6B7FD7).copy(alpha = 0.22f),
        1f to Color.Transparent,
    )

    val composerTopFade = Brush.verticalGradient(
        0f to Color.Transparent,
        0.4f to Color(0xFF0C0C12).copy(alpha = 0.65f),
        0.75f to Color(0xFF16161E).copy(alpha = 0.88f),
        1f to Color(0xFF1E1E28),
    )

    val accentGlow = Brush.radialGradient(
        colors = listOf(Color(0xFF7B8CFF).copy(alpha = 0.45f), Color.Transparent),
        radius = 32f,
    )

    val speakPill = Brush.horizontalGradient(
        0f to Color(0xFF5A6AD0),
        0.5f to Color(0xFF6B7FD7),
        1f to Color(0xFF7A8AE8),
    )

    val sendButton = Brush.radialGradient(
        colors = listOf(Color(0xFFF2F2F7), Color(0xFFD8D8E0)),
        radius = 28f,
    )

    val actionChip = Brush.linearGradient(
        0f to Color(0xFF32323E),
        1f to Color(0xFF24242E),
    )

    val attachmentChip = Brush.linearGradient(
        0f to Color(0xFF2E2E3A),
        1f to Color(0xFF22222C),
    )

    val thinkingBubble = Brush.linearGradient(
        0f to Color(0xFF1E1E2A).copy(alpha = 0.7f),
        1f to Color(0xFF14141C).copy(alpha = 0.4f),
    )
}

@Composable
fun SectionSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppGradients.separatorLine),
    )
}

@Composable
fun ComposerTopFade(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(AppGradients.composerTopFade),
    )
}