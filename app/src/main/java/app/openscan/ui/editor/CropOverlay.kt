package app.openscan.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Four draggable corner handles over the original page image.
 * [points] is a flat list of normalized x/y pairs: TL, TR, BR, BL.
 */
@Composable
fun CropOverlay(
    points: List<Float>,
    onPointsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPoints by rememberUpdatedState(points)
    val currentOnChange by rememberUpdatedState(onPointsChange)
    var activeCorner by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val touchRadius = 56.dp.toPx()
            detectDragGestures(
                onDragStart = { position ->
                    activeCorner = nearestCorner(currentPoints, size.width, size.height, position, touchRadius)
                },
                onDragEnd = { activeCorner = -1 },
                onDragCancel = { activeCorner = -1 },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val index = activeCorner
                    if (index >= 0) {
                        val updated = currentPoints.toMutableList()
                        updated[index * 2] =
                            (updated[index * 2] + dragAmount.x / size.width).coerceIn(0f, 1f)
                        updated[index * 2 + 1] =
                            (updated[index * 2 + 1] + dragAmount.y / size.height).coerceIn(0f, 1f)
                        currentOnChange(updated)
                    }
                }
            )
        }
    ) {
        if (points.size < 8) return@Canvas
        val offsets = (0 until 4).map { index ->
            Offset(points[index * 2] * size.width, points[index * 2 + 1] * size.height)
        }

        val path = Path().apply {
            moveTo(offsets[0].x, offsets[0].y)
            lineTo(offsets[1].x, offsets[1].y)
            lineTo(offsets[2].x, offsets[2].y)
            lineTo(offsets[3].x, offsets[3].y)
            close()
        }

        drawPath(path, Color.White.copy(alpha = 0.18f))
        drawPath(path, Color.White, style = Stroke(width = 2.dp.toPx()))
        drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 4.dp.toPx()))

        offsets.forEachIndexed { index, offset ->
            drawCircle(Color.White, radius = 12.dp.toPx(), center = offset)
            drawCircle(
                color = if (index == activeCorner) Color(0xFF7FD8FF) else Color(0xFF0B5C8A),
                radius = 7.dp.toPx(),
                center = offset
            )
        }
    }
}

private fun nearestCorner(
    points: List<Float>,
    width: Int,
    height: Int,
    position: Offset,
    radius: Float
): Int {
    if (points.size < 8) return -1
    var bestIndex = -1
    var bestDistance = Float.MAX_VALUE
    for (index in 0 until 4) {
        val x = points[index * 2] * width
        val y = points[index * 2 + 1] * height
        val distance = hypot(x - position.x, y - position.y)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return if (bestDistance <= radius) bestIndex else -1
}
