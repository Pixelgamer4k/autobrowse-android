package com.autobrowse.android.ui.miniapps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
)

@Composable
fun NotesDrawingSheet(
    onDismiss: () -> Unit,
    onSave: (List<DrawStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var strokeColor by remember { mutableStateOf(Color(0xFF1C1C1E)) }
    var strokeWidth by remember { mutableStateOf(4f) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel drawing")
                }
                Text(
                    text = "Drawing",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { strokes.clear(); currentPoints = emptyList() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear drawing")
                }
                IconButton(onClick = {
                    val all = strokes.toList() + if (currentPoints.size > 1) {
                        listOf(DrawStroke(currentPoints, strokeColor, strokeWidth))
                    } else {
                        emptyList()
                    }
                    onSave(all)
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Save drawing")
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(top = 56.dp, start = 12.dp, end = 12.dp)
                    .background(Color.White, MaterialTheme.shapes.large)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> currentPoints = listOf(offset) },
                            onDrag = { change, _ ->
                                currentPoints = currentPoints + change.position
                                change.consume()
                            },
                            onDragEnd = {
                                if (currentPoints.size > 1) {
                                    strokes.add(DrawStroke(currentPoints, strokeColor, strokeWidth))
                                }
                                currentPoints = emptyList()
                            },
                        )
                    },
            ) {
                fun drawStroke(stroke: DrawStroke) {
                    val path = Path()
                    stroke.points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                strokes.forEach(::drawStroke)
                if (currentPoints.size > 1) {
                    drawStroke(DrawStroke(currentPoints, strokeColor, strokeWidth))
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Color(0xFF1C1C1E),
                            Color(0xFFFF3B30),
                            Color(0xFFFF9500),
                            Color(0xFF34C759),
                            Color(0xFF007AFF),
                            Color(0xFF5856D6),
                        ).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(color, CircleShape)
                                    .then(
                                        if (color == strokeColor) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { strokeColor = color },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Stroke", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 2f..16f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}