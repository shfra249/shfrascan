package app.openscan.ui.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.openscan.data.ScanDocument
import app.openscan.domain.PdfOptions
import app.openscan.ui.MainViewModel
import app.openscan.ui.components.ConfirmDialog
import app.openscan.ui.components.EmptyState
import app.openscan.ui.components.OcrLanguageDialog
import app.openscan.ui.components.PageBadge
import app.openscan.ui.components.TextInputDialog
import app.openscan.ui.components.TextSheetDialog
import app.openscan.ui.components.fileImageModel
import app.openscan.ui.components.shareText
import app.openscan.util.Exporter
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentScreen(
    viewModel: MainViewModel,
    docId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMerged: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var doc by remember { mutableStateOf<ScanDocument?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var exportProtect by remember { mutableStateOf(false) }
    var ocrOpen by remember { mutableStateOf(false) }
    var textOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    var mergeTargetId by remember { mutableStateOf<String?>(null) }
    var splitOpen by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch { viewModel.importImages(docId, uris) }
    }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { viewModel.importPdf(docId, uri) }
    }

    suspend fun reload() {
        doc = viewModel.loadDocument(docId)
    }

    LaunchedEffect(docId) { reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { reload() }
    }
    LaunchedEffect(viewModel.document) {
        val latest = viewModel.document
        if (latest?.id == docId) doc = latest
    }

    val currentDoc = doc
    val pages = currentDoc?.pages ?: emptyList()
    val pagerState = rememberPagerState(pageCount = { pages.size })

    fun runPdfExport(options: PdfOptions, saveToDownloads: Boolean) {
        scope.launch {
            val file = viewModel.exportPdf(docId, options) ?: return@launch
            if (saveToDownloads) {
                runCatching {
                    Exporter.saveToDownloads(context, file, file.name, "application/pdf")
                }.onSuccess {
                    viewModel.showMessage("Saved to Downloads/shfrascan/${file.name}")
                }.onFailure {
                    viewModel.showMessage(it.message ?: "Could not save PDF")
                }
            } else {
                runCatching {
                    Exporter.share(context, file, "application/pdf", currentDoc?.name ?: "Scan")
                }.onFailure {
                    viewModel.showMessage(it.message ?: "Could not share PDF")
                }
            }
        }
    }

    fun runShare(block: suspend () -> java.io.File?) {
        scope.launch {
            val file = block() ?: return@launch
            val mime = when (file.extension.lowercase()) {
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
            runCatching {
                Exporter.share(context, file, mime, currentDoc?.name ?: "Scan")
            }.onFailure {
                viewModel.showMessage(it.message ?: "Could not share file")
            }
        }
    }

    fun shareTextOfDocument() {
        val text = pages.mapIndexedNotNull { index, page ->
            page.ocrText?.takeIf { it.isNotBlank() }?.let { "— Page ${index + 1} —\n$it" }
        }.joinToString("\n\n")
        if (text.isBlank()) {
            ocrOpen = true
        } else {
            shareText(context, text, currentDoc?.name ?: "Scanned text")
        }
    }

    if (currentDoc == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
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
                            text = "${pages.size} page" + if (pages.size == 1) "" else "s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { shareTextOfDocument() },
                        enabled = pages.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.TextFields, contentDescription = "Text")
                    }
                    IconButton(
                        onClick = { runPdfExport(PdfOptions(), saveToDownloads = false) },
                        enabled = pages.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export / share PDF") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    exportProtect = false
                                    exportOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save PDF to Downloads") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    runPdfExport(PdfOptions(), saveToDownloads = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Password-protected PDF") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    exportProtect = true
                                    exportOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Compress PDF") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    runPdfExport(
                                        PdfOptions(jpegQuality = 0.55f),
                                        saveToDownloads = true
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as Word (.docx)") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    runShare { viewModel.exportDocx(docId) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as Excel (.xlsx)") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    runShare { viewModel.exportXlsx(docId) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as CSV (.csv)") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    runShare { viewModel.exportCsv(docId) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as text (.txt)") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    runShare { viewModel.exportText(docId) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export page images") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val files = viewModel.exportImages(docId)
                                        if (!files.isNullOrEmpty()) {
                                            runCatching {
                                                Exporter.shareMultiple(context, files, "image/jpeg")
                                            }.onFailure {
                                                viewModel.showMessage(
                                                    it.message ?: "Could not share images"
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Print") },
                                enabled = pages.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val file = viewModel.exportPdf(
                                            docId,
                                            PdfOptions(searchable = false)
                                        )
                                        if (file != null) {
                                            runCatching {
                                                Exporter.print(
                                                    context,
                                                    file,
                                                    currentDoc.name
                                                )
                                            }.onFailure {
                                                viewModel.showMessage(
                                                    it.message ?: "Could not print"
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Merge with another document") },
                                enabled = viewModel.documents.any { it.id != docId },
                                onClick = {
                                    menuOpen = false
                                    mergeTargetId = null
                                    mergeOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Split into single pages") },
                                enabled = pages.size >= 2,
                                onClick = {
                                    menuOpen = false
                                    splitOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add images") },
                                onClick = {
                                    menuOpen = false
                                    imagePicker.launch(arrayOf("image/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add PDF") },
                                onClick = {
                                    menuOpen = false
                                    pdfPicker.launch(arrayOf("application/pdf"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename document") },
                                onClick = {
                                    menuOpen = false
                                    renameOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete document") },
                                onClick = {
                                    menuOpen = false
                                    deleteOpen = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit, enabled = pages.isNotEmpty()) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit pages")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        exportProtect = false
                        exportOpen = true
                    },
                    enabled = pages.isNotEmpty()
                ) {
                    Text("Export PDF", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                EmptyState(
                    title = "No pages",
                    subtitle = "This document is empty. Add a page to continue.",
                    actionLabel = "Edit pages",
                    onAction = onEdit
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { index ->
                    val page = pages.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (page != null) {
                            AsyncImage(
                                model = fileImageModel(viewModel.imageFile(docId, page)),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                        PageBadge(
                            text = "${index + 1} / ${pages.size}",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (exportOpen) {
        ExportPdfDialog(
            initialProtect = exportProtect,
            onExport = { options, save ->
                exportOpen = false
                runPdfExport(options, save)
            },
            onDismiss = { exportOpen = false }
        )
    }

    if (ocrOpen) {
        OcrLanguageDialog(
            languages = viewModel.languages,
            selected = currentDoc.ocrLanguage,
            onRun = { language ->
                ocrOpen = false
                scope.launch {
                    viewModel.runOcr(docId, null, language)
                    reload()
                    textOpen = true
                }
            },
            onDismiss = { ocrOpen = false }
        )
    }

    if (textOpen) {
        val text = pages.mapIndexedNotNull { index, page ->
            page.ocrText?.takeIf { it.isNotBlank() }?.let { "— Page ${index + 1} —\n$it" }
        }.joinToString("\n\n")
        TextSheetDialog(
            title = "Extracted text",
            text = text,
            onDismiss = { textOpen = false },
            onShare = if (text.isNotBlank()) {
                { shareText(context, text, currentDoc.name) }
            } else {
                null
            }
        )
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

    if (mergeOpen) {
        val candidates = viewModel.documents.filter { it.id != docId }
        AlertDialog(
            onDismissRequest = { mergeOpen = false },
            title = { Text("Merge with another document") },
            text = {
                if (candidates.isEmpty()) {
                    Text("There are no other documents to merge with.")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        candidates.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { mergeTargetId = item.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = mergeTargetId == item.id,
                                    onClick = { mergeTargetId = item.id }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = item.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.pages.size} page" +
                                            if (item.pages.size == 1) "" else "s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = mergeTargetId != null,
                    onClick = {
                        val other = candidates.firstOrNull { it.id == mergeTargetId }
                        if (other != null) {
                            mergeOpen = false
                            scope.launch {
                                val newId = viewModel.mergeDocuments(
                                    listOf(docId, other.id),
                                    currentDoc.name + " + " + other.name
                                )
                                if (newId != null) onMerged(newId)
                            }
                        }
                    }
                ) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { mergeOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (splitOpen) {
        ConfirmDialog(
            title = "Split into single pages?",
            message = "Each page becomes its own document. The original document will be " +
                "removed after the split.",
            onConfirm = {
                splitOpen = false
                scope.launch {
                    if (viewModel.splitDocument(docId) != null) onBack()
                }
            },
            onDismiss = { splitOpen = false }
        )
    }

    if (deleteOpen) {
        ConfirmDialog(
            title = "Delete document?",
            message = "\"${currentDoc.name}\" and its ${pages.size} page(s) will be removed from this device.",
            onConfirm = {
                scope.launch {
                    viewModel.deleteDocument(docId)
                    onBack()
                }
            },
            onDismiss = { deleteOpen = false }
        )
    }
}

@Composable
private fun ExportPdfDialog(
    initialProtect: Boolean,
    onExport: (PdfOptions, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var searchable by remember { mutableStateOf(true) }
    var protect by remember { mutableStateOf(initialProtect) }
    var password by remember { mutableStateOf("") }
    var quality by remember { mutableFloatStateOf(0.82f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export PDF") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Searchable text layer")
                        Text(
                            "Runs offline OCR when needed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = searchable, onCheckedChange = { searchable = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Password protection")
                        Text(
                            "AES-128 encrypted PDF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = protect, onCheckedChange = { protect = it })
                }
                if (protect) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Image quality: ${(quality * 100).toInt()}%")
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 0.35f..0.95f
                )
                Text(
                    "Lower quality produces smaller files.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !protect || password.isNotBlank(),
                onClick = {
                    onExport(
                        PdfOptions(
                            searchable = searchable,
                            password = password.takeIf { protect },
                            jpegQuality = quality
                        ),
                        true
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    enabled = !protect || password.isNotBlank(),
                    onClick = {
                        onExport(
                            PdfOptions(
                                searchable = searchable,
                                password = password.takeIf { protect },
                                jpegQuality = quality
                            ),
                            false
                        )
                    }
                ) { Text("Share") }
            }
        }
    )
}
