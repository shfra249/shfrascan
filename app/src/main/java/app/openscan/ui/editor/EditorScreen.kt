package app.openscan.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.openscan.data.Filter
import app.openscan.data.PageOverlay
import app.openscan.data.ScanDocument
import app.openscan.data.ScanPage
import app.openscan.domain.ImageProcessor
import app.openscan.ui.MainViewModel
import app.openscan.ui.OverlayBaseImage
import app.openscan.ui.OverlaySaveItem
import app.openscan.ui.PrivacyRiskResult
import app.openscan.util.BitmapUtils
import app.openscan.ui.components.ConfirmDialog
import app.openscan.ui.components.EmptyState
import app.openscan.ui.components.OcrLanguageDialog
import app.openscan.ui.components.PageBadge
import app.openscan.ui.components.TextInputDialog
import app.openscan.ui.components.TextSheetDialog
import app.openscan.ui.components.fileImageModel
import app.openscan.ui.components.shareText
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private data class OverlaySession(
    val pageId: String,
    val title: String,
    val base: OverlayBaseImage,
    val drafts: List<OverlayDraft>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    docId: String,
    onBack: () -> Unit,
    onAddPage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var doc by remember { mutableStateOf<ScanDocument?>(null) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var cropMode by remember { mutableStateOf(false) }
    var cropPoints by remember { mutableStateOf(ImageProcessor.fullCrop()) }

    var renameOpen by remember { mutableStateOf(false) }
    var deletePageOpen by remember { mutableStateOf(false) }
    var ocrOpen by remember { mutableStateOf(false) }
    var textOpen by remember { mutableStateOf(false) }
    var signatureOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var addSheetOpen by remember { mutableStateOf(false) }
    var overlaySession by remember { mutableStateOf<OverlaySession?>(null) }
    var privacyResult by remember { mutableStateOf<PrivacyRiskResult?>(null) }
    var privacyOpen by remember { mutableStateOf(false) }

    suspend fun reload() {
        val loaded = viewModel.loadDocument(docId)
        doc = loaded
        if (loaded != null) {
            pageIndex = pageIndex.coerceIn(0, (loaded.pages.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(docId) { reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { reload() }
    }

    fun startOverlayEditor(
        title: String,
        newFile: File?,
        kind: String,
        target: ScanPage
    ) {
        scope.launch {
            val base = viewModel.overlayBaseFile(docId, target.id) ?: run {
                if (newFile != null) newFile.delete()
                return@launch
            }
            val drafts = target.overlays.mapNotNull { overlay ->
                val file = viewModel.overlayFile(docId, overlay)
                if (!file.exists()) {
                    null
                } else {
                    OverlayDraft(
                        id = overlay.id,
                        kind = overlay.kind,
                        file = file,
                        stored = true,
                        x = overlay.x,
                        y = overlay.y,
                        width = overlay.width,
                        aspect = BitmapUtils.aspectRatio(file)
                    )
                }
            }.toMutableList()
            if (newFile != null) {
                val isSignature = kind == PageOverlay.SIGNATURE
                drafts += OverlayDraft(
                    id = UUID.randomUUID().toString(),
                    kind = kind,
                    file = newFile,
                    stored = false,
                    x = if (isSignature) 0.62f else 0.30f,
                    y = if (isSignature) 0.74f else 0.30f,
                    width = if (isSignature) 0.32f else 0.40f,
                    aspect = BitmapUtils.aspectRatio(newFile)
                )
            }
            if (drafts.isEmpty()) {
                base.file.delete()
                viewModel.showMessage("No layers to arrange")
                return@launch
            }
            overlaySession = OverlaySession(target.id, title, base, drafts)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                viewModel.importImages(docId, uris)
                reload()
                pageIndex = (doc?.pages?.lastIndex ?: pageIndex).coerceAtLeast(0)
            }
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.importPdf(docId, uri)
                reload()
                pageIndex = (doc?.pages?.lastIndex ?: pageIndex).coerceAtLeast(0)
            }
        }
    }

    val currentDoc = doc
    if (currentDoc == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pages = currentDoc.pages
    val page = pages.getOrNull(pageIndex)

    val insertImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val target = page ?: return@rememberLauncherForActivityResult
            scope.launch {
                val temp = File(context.cacheDir, "insert_${UUID.randomUUID()}.img")
                runCatching {
                    BitmapUtils.copyUriToFile(context, uri, temp)
                }.onSuccess {
                    startOverlayEditor("Arrange image", temp, PageOverlay.IMAGE, target)
                }.onFailure {
                    temp.delete()
                    viewModel.showMessage(it.message ?: "Could not read image")
                }
            }
        }
    }


    LaunchedEffect(page?.id) {
        cropMode = false
        cropPoints = page?.crop ?: ImageProcessor.fullCrop()
    }

    fun applyEdits(crop: List<Float>?, filter: Filter, rotation: Int) {
        val target = page ?: return
        scope.launch {
            if (viewModel.applyPageEdits(docId, target.id, crop, filter, rotation)) {
                reload()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = currentDoc.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (pages.isEmpty()) "No pages" else "Page ${pageIndex + 1} of ${pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { addSheetOpen = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add pages")
                    }
                    IconButton(
                        onClick = { ocrOpen = true },
                        enabled = page != null
                    ) {
                        Icon(Icons.Filled.TextFields, contentDescription = "Recognise text")
                    }
                    TextButton(onClick = onBack) { Text("Done") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Page menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename document") },
                                onClick = {
                                    menuOpen = false
                                    renameOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add page") },
                                onClick = {
                                    menuOpen = false
                                    onAddPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move page left") },
                                enabled = page != null && pageIndex > 0,
                                onClick = {
                                    menuOpen = false
                                    val target = page ?: return@DropdownMenuItem
                                    scope.launch {
                                        viewModel.movePage(docId, target.id, -1)
                                        reload()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move page right") },
                                enabled = page != null && pageIndex < pages.lastIndex,
                                onClick = {
                                    menuOpen = false
                                    val target = page ?: return@DropdownMenuItem
                                    scope.launch {
                                        viewModel.movePage(docId, target.id, 1)
                                        reload()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add signature") },
                                enabled = page != null,
                                onClick = {
                                    menuOpen = false
                                    signatureOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Insert image") },
                                enabled = page != null,
                                onClick = {
                                    menuOpen = false
                                    insertImagePicker.launch(arrayOf("image/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Arrange signature / image") },
                                enabled = page != null && page.overlays.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    val target = page ?: return@DropdownMenuItem
                                    startOverlayEditor("Arrange layers", null, PageOverlay.IMAGE, target)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Find private data") },
                                enabled = page != null,
                                onClick = {
                                    menuOpen = false
                                    val target = page ?: return@DropdownMenuItem
                                    scope.launch {
                                        val result = viewModel.findPrivacyRisks(docId, target.id)
                                        if (result != null) {
                                            privacyResult = result
                                            privacyOpen = true
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete page") },
                                enabled = page != null,
                                onClick = {
                                    menuOpen = false
                                    deletePageOpen = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (pages.isEmpty() || page == null) {
                EmptyState(
                    title = "This document has no pages",
                    subtitle = "Capture a page to start building this document.",
                    actionLabel = "Add page",
                    onAction = onAddPage
                )
            } else if (cropMode) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF202124)),
                    contentAlignment = Alignment.Center
                ) {
                    val aspect = if (page.origWidth > 0 && page.origHeight > 0) {
                        page.origWidth.toFloat() / page.origHeight
                    } else {
                        0.707f
                    }
                    val boxRatio = if (maxHeight.value > 0f) {
                        maxWidth.value / maxHeight.value
                    } else {
                        aspect
                    }
                    val imageModifier = if (aspect >= boxRatio) {
                        Modifier.fillMaxWidth().aspectRatio(aspect)
                    } else {
                        Modifier.fillMaxHeight().aspectRatio(aspect)
                    }
                    Box(modifier = imageModifier) {
                        AsyncImage(
                            model = fileImageModel(viewModel.originalFile(docId, page)),
                            contentDescription = "Original page",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        CropOverlay(
                            points = cropPoints,
                            onPointsChange = { cropPoints = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Drag the corners",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val target = page
                        scope.launch {
                            viewModel.detectCrop(docId, target.id)?.let { cropPoints = it }
                        }
                    }) { Text("Auto") }
                    TextButton(onClick = {
                        cropMode = false
                        cropPoints = page.crop ?: ImageProcessor.fullCrop()
                    }) { Text("Cancel") }
                    Button(onClick = {
                        applyEdits(cropPoints, Filter.from(page.filter), page.rotation)
                        cropMode = false
                    }) { Text("Apply crop") }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = fileImageModel(viewModel.imageFile(docId, page)),
                        contentDescription = page.ocrText ?: "Scanned page ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                    PageBadge(
                        text = "${pageIndex + 1} / ${pages.size}",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Filter.entries.forEach { filter ->
                        FilterChip(
                            selected = Filter.from(page.filter) == filter,
                            onClick = { applyEdits(page.crop, filter, page.rotation) },
                            label = { Text(filter.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            cropPoints = page.crop ?: ImageProcessor.fullCrop()
                            cropMode = true
                        },
                        enabled = page.original != null
                    ) {
                        Icon(Icons.Filled.Crop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Crop")
                    }
                    TextButton(onClick = {
                        applyEdits(
                            page.crop,
                            Filter.from(page.filter),
                            page.rotation + 90
                        )
                    }) {
                        Icon(Icons.Filled.RotateRight, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Rotate")
                    }
                    TextButton(onClick = { textOpen = true }) {
                        Icon(Icons.Filled.TextFields, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Text")
                    }
                    TextButton(onClick = { signatureOpen = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Sign")
                    }
                    TextButton(onClick = {
                        insertImagePicker.launch(arrayOf("image/*"))
                    }) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Image")
                    }
                    TextButton(
                        onClick = {
                            val target = page
                            startOverlayEditor("Arrange layers", null, PageOverlay.IMAGE, target)
                        },
                        enabled = page.overlays.isNotEmpty()
                    ) {
                        Text("Layers")
                    }
                }
            }

            if (pages.isNotEmpty() && page != null) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                        val selected = index == pageIndex
                        Box(
                            modifier = Modifier
                                .size(width = 62.dp, height = 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    pageIndex = index
                                }
                        ) {
                            AsyncImage(
                                model = fileImageModel(viewModel.imageFile(docId, item)),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (!selected) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.18f))
                                )
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(width = 62.dp, height = 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { addSheetOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add page",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (renameOpen) {
        TextInputDialog(
            title = "Rename document",
            label = "Name",
            initialValue = currentDoc.name,
            onConfirm = { name ->
                scope.launch {
                    viewModel.renameDocument(docId, name)
                    reload()
                }
            },
            onDismiss = { renameOpen = false }
        )
    }

    if (deletePageOpen && page != null) {
        val target = page
        ConfirmDialog(
            title = "Delete page?",
            message = "Page ${pageIndex + 1} will be removed from this document.",
            onConfirm = {
                scope.launch {
                    viewModel.deletePage(docId, target.id)
                    reload()
                }
            },
            onDismiss = { deletePageOpen = false }
        )
    }

    if (ocrOpen) {
        OcrLanguageDialog(
            languages = viewModel.languages,
            selected = viewModel.ocrLanguage,
            onRun = { language ->
                ocrOpen = false
                val target = page
                if (target != null) {
                    scope.launch {
                        viewModel.runOcr(docId, target.id, language)
                        reload()
                        textOpen = true
                    }
                }
            },
            onDismiss = { ocrOpen = false }
        )
    }

    if (textOpen && page != null) {
        val text = page.ocrText.orEmpty()
        TextSheetDialog(
            title = "Extracted text",
            text = text,
            onDismiss = { textOpen = false },
            onShare = if (text.isNotBlank()) {
                { shareText(context, text) }
            } else {
                null
            }
        )
    }

    if (addSheetOpen) {
        ModalBottomSheet(onDismissRequest = { addSheetOpen = false }) {
            Text(
                text = "Add pages",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Camera") },
                supportingContent = { Text("Capture a new page") },
                leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                modifier = Modifier.clickable {
                    addSheetOpen = false
                    onAddPage()
                }
            )
            ListItem(
                headlineContent = { Text("Images") },
                supportingContent = { Text("Import one or more photos") },
                leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.clickable {
                    addSheetOpen = false
                    imagePicker.launch(arrayOf("image/*"))
                }
            )
            ListItem(
                headlineContent = { Text("PDF") },
                supportingContent = { Text("Append pages from a PDF file") },
                leadingContent = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                modifier = Modifier.clickable {
                    addSheetOpen = false
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (signatureOpen && page != null) {
        val target = page
        SignatureDialog(
            onSave = { file ->
                signatureOpen = false
                startOverlayEditor("Arrange signature", file, PageOverlay.SIGNATURE, target)
            },
            onDismiss = { signatureOpen = false }
        )
    }
    if (privacyOpen && privacyResult != null && page != null) {
        val result = privacyResult ?: return
        val target = page
        AlertDialog(
            onDismissRequest = {
                privacyOpen = false
                privacyResult = null
            },
            title = {
                Text(
                    if (result.boxes.isEmpty()) "No sensitive data found"
                    else "Redact sensitive data?"
                )
            },
            text = {
                Column {
                    if (result.boxes.isEmpty()) {
                        Text(
                            "No email addresses, phone numbers, card numbers or IBANs were " +
                                "detected on this page."
                        )
                    } else {
                        Text("${result.boxes.size} sensitive value" +
                            if (result.boxes.size == 1) "" else "s" + " detected:")
                        Spacer(Modifier.height(8.dp))
                        result.counts.forEach { (label, count) ->
                            Text("• $label: $count")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Redaction permanently blackens these areas and removes the " +
                                "original image and OCR text for this page. This cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        privacyOpen = false
                        privacyResult = null
                        if (result.boxes.isNotEmpty()) {
                            scope.launch {
                                if (viewModel.redactPage(docId, target.id, result.boxes)) {
                                    reload()
                                }
                            }
                        }
                    }
                ) {
                    Text(if (result.boxes.isEmpty()) "OK" else "Redact")
                }
            },
            dismissButton = if (result.boxes.isEmpty()) {
                null
            } else {
                {
                    TextButton(onClick = {
                        privacyOpen = false
                        privacyResult = null
                    }) { Text("Cancel") }
                }
            }
        )
    }

    overlaySession?.let { session ->
        OverlayEditorDialog(
            title = session.title,
            baseFile = session.base.file,
            baseWidth = session.base.width,
            baseHeight = session.base.height,
            initialDrafts = session.drafts,
            onSave = { drafts ->
                val items = drafts.map {
                    OverlaySaveItem(
                        id = it.id,
                        kind = it.kind,
                        source = it.file,
                        stored = it.stored,
                        x = it.x,
                        y = it.y,
                        width = it.width
                    )
                }
                session.base.file.delete()
                overlaySession = null
                scope.launch {
                    val saved = viewModel.saveOverlays(docId, session.pageId, items)
                    drafts.filterNot { it.stored }.forEach { runCatching { it.file.delete() } }
                    if (saved) reload()
                }
            },
            onDismiss = {
                session.drafts.filterNot { it.stored }
                    .forEach { runCatching { it.file.delete() } }
                session.base.file.delete()
                overlaySession = null
            }
        )
    }

}
