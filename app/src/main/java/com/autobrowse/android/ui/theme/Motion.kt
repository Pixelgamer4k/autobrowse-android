package com.autobrowse.android.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object Motion {
    val springSnappy = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 420f,
    )

    val springSmooth = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = 280f,
    )

    val springBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 340f,
    )

    /** iOS-like window move/resize settle — slight overshoot, responsive. */
    val springWindow = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 380f,
    )

    val springWindowSettle = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 420f,
    )

    val springSmoothOffset = spring<IntOffset>(
        dampingRatio = 0.88f,
        stiffness = StiffnessMediumLow,
    )

    val tweenQuick = tween<Float>(durationMillis = 220)
    val tweenMedium = tween<Float>(durationMillis = 380)
    val tweenSlow = tween<Float>(durationMillis = 520)
}