package com.autobrowse.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun FlexibleSplitPane(
    topWeight: Float,
    onWeightChange: (Float) -> Unit,
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedTop = topWeight.coerceIn(0.25f, 0.75f)

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(clampedTop)) {
            topContent()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .pointerInput(clampedTop) {
                    var weight = clampedTop
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        weight = (weight + dragAmount.y / 1200f).coerceIn(0.25f, 0.75f)
                        onWeightChange(weight)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.2f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f - clampedTop)) {
            bottomContent()
        }
    }
}