package com.autobrowse.android.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

@Composable
fun OverlayBackHandlers(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
    PredictiveBackHandler(enabled = enabled) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { }
            onBack()
        } catch (_: CancellationException) {
            // Gesture cancelled
        }
    }
}