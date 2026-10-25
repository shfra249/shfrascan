package app.openscan.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun SignatureDialog(
    onSave: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signature") },
        text = {
            Column {
                Text(
                    "Draw with your finger. The signature is stamped on the bottom-right corner of the current page.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .onSizeChanged { canvasSize = it }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { position -> activeStroke = listOf(position) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    activeStroke = activeStroke + change.position
                                },
                                onDragEnd = {
                                    if (activeStroke.isNotEmpty()) {
                                        strokes = strokes + listOf(activeStroke)
                                        activeStroke = emptyList()
                                    }
                                },
                                onDragCancel = { activeStroke = emptyList() }
                            )
                        }
                ) {
                    val all = if (activeStroke.isEmpty()) strokes else strokes + listOf(activeStroke)
                    all.forEach { drawSignatureStroke(it) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            strokes = emptyList()
                            activeStroke = emptyList()
                        },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Clear") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty() && canvasSize.width > 0,
                onClick = {
                    val file = File(context.cacheDir, "signature_${System.currentTimeMillis()}.png")
                    renderSignature(strokes, canvasSize, file)
                    onSave(file)
                    onDismiss()
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun DrawScope.drawSignatureStroke(points: List<Offset>) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(Color.Black, radius = 1.5.dp.toPx(), center = points[0])
        return
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (index in 1 until points.size) {
            lineTo(points[index].x, points[index].y)
        }
    }
    drawPath(
        path = path,
        color = Color.Black,
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun renderSignature(strokes: List<List<Offset>>, canvas: IntSize, file: File) {
    val width = 1200
    val height = 480
    val scaleX = width.toFloat() / canvas.width.coerceAtLeast(1)
    val scaleY = height.toFloat() / canvas.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val androidCanvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        when {
            stroke.size == 1 -> androidCanvas.drawCircle(
                stroke[0].x * scaleX,
                stroke[0].y * scaleY,
                3f,
                paint
            )
            stroke.size > 1 -> {
                val path = android.graphics.Path()
                path.moveTo(stroke[0].x * scaleX, stroke[0].y * scaleY)
                for (index in 1 until stroke.size) {
                    path.lineTo(stroke[index].x * scaleX, stroke[index].y * scaleY)
                }
                androidCanvas.drawPath(path, paint)
            }
        }
    }
    file.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    bitmap.recycle()
}
