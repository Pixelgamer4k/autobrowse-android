package com.autobrowse.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException

@Composable
fun OverlayBackHandlers(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
    PredictiveBackHandler(enabled = enabled) {
        try {
            awaitGestureCompletion()
        } catch (_: CancellationException) {
            return@PredictiveBackHandler
        }
        onBack()
    }
}