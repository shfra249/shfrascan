package app.openscan.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.openscan.ui.components.fileImageModel
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

data class OverlayDraft(
    val id: String,
    val kind: String,
    val file: File,
    val stored: Boolean,
    val x: Float,
    val y: Float,
    val width: Float,
    val aspect: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayEditorDialog(
    title: String,
    baseFile: File,
    baseWidth: Int,
    baseHeight: Int,
    initialDrafts: List<OverlayDraft>,
    onSave: (List<OverlayDraft>) -> Unit,
    onDismiss: () -> Unit
) {
    var drafts by remember { mutableStateOf(initialDrafts) }
    var selectedId by remember { mutableStateOf(initialDrafts.lastOrNull()?.id) }
    val selected = drafts.firstOrNull { it.id == selectedId }
    val density = LocalDensity.current
    val baseAspect = baseWidth.toFloat() / baseHeight.coerceAtLeast(1)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { onSave(drafts) },
                                enabled = drafts.isNotEmpty()
                            ) { Text("Save") }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF202124)),
                        contentAlignment = Alignment.Center
                    ) {
                        val maxWidthPx = with(density) { maxWidth.toPx() }
                        val maxHeightPx = with(density) { maxHeight.toPx() }
                        val displayWidth = min(maxWidthPx, maxHeightPx * baseAspect)
                        val displayHeight = displayWidth / baseAspect.coerceAtLeast(0.01f)

                        Box(
                            modifier = Modifier
                                .size(
                                    with(density) { displayWidth.toDp() },
                                    with(density) { displayHeight.toDp() }
                                )
                                .clip(RoundedCornerShape(2.dp))
                        ) {
                            AsyncImage(
                                model = fileImageModel(baseFile),
                                contentDescription = "Page",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            drafts.forEach { draft ->
                                val widthPx = displayWidth * draft.width
                                val heightPx = widthPx / draft.aspect.coerceAtLeast(0.05f)
                                val heightNorm = heightPx / displayHeight
                                val isSelected = draft.id == selectedId
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (displayWidth * draft.x).roundToInt(),
                                                (displayHeight * draft.y).roundToInt()
                                            )
                                        }
                                        .size(
                                            with(density) { widthPx.toDp() },
                                            with(density) { heightPx.toDp() }
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.White.copy(alpha = 0.7f)
                                            },
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                        .clickable { selectedId = draft.id }
                                        .pointerInput(draft.id, displayWidth, displayHeight) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                drafts = drafts.map { current ->
                                                    if (current.id == draft.id) {
                                                        current.copy(
                                                            x = (current.x + dragAmount.x / displayWidth)
                                                                .coerceIn(0f, 1f - current.width),
                                                            y = (current.y + dragAmount.y / displayHeight)
                                                                .coerceIn(0f, 1f - heightNorm)
                                                        )
                                                    } else {
                                                        current
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = fileImageModel(draft.file),
                                        contentDescription = draft.kind,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (selected == null) {
                                "Tap a layer to select it."
                            } else {
                                "Drag to move. Use the slider to resize."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        selected?.let { draft ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = draft.width,
                                    onValueChange = { value ->
                                        drafts = drafts.map {
                                            if (it.id == draft.id) {
                                                it.copy(
                                                    width = value,
                                                    x = it.x.coerceIn(0f, 1f - value)
                                                )
                                            } else {
                                                it
                                            }
                                        }
                                    },
                                    valueRange = 0.06f..0.9f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = {
                                        drafts = drafts.filterNot { it.id == draft.id }
                                        selectedId = drafts.firstOrNull()?.id
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove layer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
