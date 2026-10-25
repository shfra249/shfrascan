package app.openscan.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openscan.data.ScanDocument
import app.openscan.domain.PdfOptions
import app.openscan.ui.MainViewModel
import app.openscan.ui.components.ConfirmDialog
import app.openscan.ui.components.DocumentTitle
import app.openscan.ui.components.EmptyState
import app.openscan.ui.components.PageBadge
import app.openscan.ui.components.TextInputDialog
import app.openscan.ui.components.fileImageModel
import app.openscan.ui.components.formatDate
import app.openscan.util.Exporter
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onOpenDocument: (String) -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents = viewModel.documents

    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var renameTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteSelection by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val normalizedQuery = query.trim()
    val matches = remember(documents, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            documents.mapNotNull { doc ->
                val matchedPage = doc.pages.firstOrNull {
                    it.ocrText?.contains(normalizedQuery, ignoreCase = true) == true
                }
                if (doc.name.contains(normalizedQuery, ignoreCase = true) || matchedPage != null) {
                    DocMatch(
                        doc = doc,
                        snippet = matchedPage?.ocrText?.let { snippet(it, normalizedQuery) }
                    )
                } else {
                    null
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val id = viewModel.importImages(null, uris)
                if (id != null) onOpenDocument(id)
            }
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val id = viewModel.importPdf(null, uri)
                if (id != null) onOpenDocument(id)
            }
        }
    }

    fun toggleSelection(id: String) {
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
        if (selected.isEmpty()) selectionMode = false
    }

    fun exitSelection() {
        selectionMode = false
        selected.clear()
    }

    androidx.activity.compose.BackHandler(enabled = selectionMode || searchOpen) {
        if (selectionMode) exitSelection() else { searchOpen = false; query = "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search names and OCR text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            if (selectionMode) "${selected.size} selected" else "shfrascan",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    if (searchOpen) {
                        IconButton(onClick = { searchOpen = false; query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    } else if (selectionMode) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = { mergeOpen = true },
                            enabled = selected.size >= 2
                        ) {
                            Icon(Icons.Filled.MergeType, contentDescription = "Merge")
                        }
                        IconButton(
                            onClick = { deleteSelection = true },
                            enabled = selected.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    } else if (searchOpen) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Outlined.Checklist, contentDescription = "Select documents")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (searchOpen) return@Scaffold
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    SmallFloatingActionButton(
                        onClick = { importMenu = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Import")
                    }
                    DropdownMenu(
                        expanded = importMenu,
                        onDismissRequest = { importMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import images") },
                            leadingIcon = {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            },
                            onClick = {
                                importMenu = false
                                imagePicker.launch(arrayOf("image/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import PDF") },
                            leadingIcon = {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            },
                            onClick = {
                                importMenu = false
                                pdfPicker.launch(arrayOf("application/pdf"))
                            }
                        )
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Scan") }
                )
            }
        }
    ) { padding ->
        if (searchOpen && normalizedQuery.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                EmptyState(
                    title = "Search your scans",
                    subtitle = "Find documents by name, or search inside pages that were already " +
                        "recognized with OCR. Everything stays on this device.",
                    icon = Icons.Filled.Search
                )
            }
        } else if (searchOpen) {
            if (matches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    EmptyState(
                        title = "No matches",
                        subtitle = "No document name or recognized page contains \"$normalizedQuery\".",
                        icon = Icons.Filled.Search
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(matches.size, key = { matches[it].doc.id }) { index ->
                        SearchResultRow(
                            match = matches[index],
                            query = normalizedQuery,
                            onClick = { onOpenDocument(matches[index].doc.id) }
                        )
                    }
                }
            }
        } else if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                EmptyState(
                    title = "No scans yet",
                    subtitle = "Capture documents with automatic edge detection, enhance them, " +
                        "run OCR in 10 languages and export watermark-free PDFs.",
                    icon = Icons.Outlined.Description,
                    actionLabel = "Start scanning",
                    onAction = onScan
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentCard(
                        doc = doc,
                        selected = selected.contains(doc.id),
                        selectionMode = selectionMode,
                        coverFile = doc.cover?.let { viewModel.imageFile(doc.id, it) },
                        onClick = {
                            if (selectionMode) toggleSelection(doc.id) else onOpenDocument(doc.id)
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selected.add(doc.id)
                            }
                        },
                        onRename = { renameTarget = doc },
                        onSharePdf = {
                            scope.launch {
                                val file = viewModel.exportPdf(doc.id, PdfOptions())
                                if (file != null) {
                                    Exporter.share(context, file, "application/pdf", doc.name)
                                }
                            }
                        },
                        onSavePdf = {
                            scope.launch {
                                val file = viewModel.exportPdf(doc.id, PdfOptions())
                                if (file != null) {
                                    runCatching {
                                        Exporter.saveToDownloads(
                                            context,
                                            file,
                                            file.name,
                                            "application/pdf"
                                        )
                                    }.onSuccess {
                                        viewModel.showMessage("Saved to Downloads/shfrascan/${file.name}")
                                    }.onFailure {
                                        viewModel.showMessage(it.message ?: "Could not save PDF")
                                    }
                                }
                            }
                        },
                        onExportImages = {
                            scope.launch {
                                val files = viewModel.exportImages(doc.id)
                                if (!files.isNullOrEmpty()) {
                                    runCatching {
                                        Exporter.shareMultiple(context, files, "image/jpeg")
                                    }.onFailure {
                                        viewModel.showMessage(it.message ?: "Could not share images")
                                    }
                                }
                            }
                        },
                        onDelete = { deleteTarget = doc }
                    )
                }
            }
        }
    }

    renameTarget?.let { doc ->
        TextInputDialog(
            title = "Rename document",
            label = "Name",
            initialValue = doc.name,
            onConfirm = { name ->
                scope.launch { viewModel.renameDocument(doc.id, name) }
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { doc ->
        ConfirmDialog(
            title = "Delete document?",
            message = "\"${doc.name}\" and its ${doc.pages.size} page(s) will be removed from this device.",
            onConfirm = {
                scope.launch {
                    viewModel.deleteDocument(doc.id)
                    selected.remove(doc.id)
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (deleteSelection) {
        ConfirmDialog(
            title = "Delete ${selected.size} documents?",
            message = "The selected scans and all of their pages will be removed from this device.",
            onConfirm = {
                val ids = selected.toList()
                scope.launch {
                    ids.forEach { viewModel.deleteDocument(it) }
                    exitSelection()
                }
            },
            onDismiss = { deleteSelection = false }
        )
    }

    if (mergeOpen) {
        TextInputDialog(
            title = "Merge ${selected.size} documents",
            label = "New document name",
            initialValue = "Merged scan",
            confirmLabel = "Merge",
            onConfirm = { name ->
                val ids = selected.toList()
                scope.launch {
                    val id = viewModel.mergeDocuments(ids, name)
                    if (id != null) {
                        exitSelection()
                        onOpenDocument(id)
                    }
                }
            },
            onDismiss = { mergeOpen = false }
        )
    }
}

private data class DocMatch(
    val doc: ScanDocument,
    val snippet: String?
)

@Composable
private fun SearchResultRow(
    match: DocMatch,
    query: String,
    onClick: () -> Unit
) {
    val doc = match.doc
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = highlighted(doc.name, query, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            match.snippet?.let { snippet ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = highlighted(snippet, query, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${doc.pages.size} " +
                    (if (doc.pages.size == 1) "page" else "pages") +
                    "  ·  " + formatDate(doc.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun highlighted(text: String, query: String, color: Color): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    if (query.isNotBlank()) {
        var start = text.indexOf(query, ignoreCase = true)
        while (start >= 0) {
            builder.addStyle(
                SpanStyle(color = color, fontWeight = FontWeight.Bold),
                start,
                start + query.length
            )
            start = text.indexOf(query, start + query.length, ignoreCase = true)
        }
    }
    return builder.toAnnotatedString()
}

private fun snippet(text: String, query: String): String {
    val compact = text.replace(Regex("\\s+"), " ").trim()
    val index = compact.indexOf(query, ignoreCase = true)
    if (index < 0) return compact.take(160)
    val start = (index - 50).coerceAtLeast(0)
    val end = (index + query.length + 110).coerceAtMost(compact.length)
    return buildString {
        if (start > 0) append("…")
        append(compact.substring(start, end))
        if (end < compact.length) append("…")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(
    doc: ScanDocument,
    selected: Boolean,
    selectionMode: Boolean,
    coverFile: java.io.File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePdf: () -> Unit,
    onExportImages: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (coverFile != null) {
                    AsyncImage(
                        model = fileImageModel(coverFile),
                        contentDescription = doc.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Checklist,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }

                PageBadge(
                    text = "${doc.pages.size} page" + if (doc.pages.size == 1) "" else "s",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )

                if (!selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Document menu",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                menuOpen = false
                                onRename()
                            })
                            DropdownMenuItem(text = { Text("Share PDF") }, onClick = {
                                menuOpen = false
                                onSharePdf()
                            })
                            DropdownMenuItem(text = { Text("Save PDF to Downloads") }, onClick = {
                                menuOpen = false
                                onSavePdf()
                            })
                            DropdownMenuItem(text = { Text("Export page images") }, onClick = {
                                menuOpen = false
                                onExportImages()
                            })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                menuOpen = false
                                onDelete()
                            })
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                DocumentTitle(doc.name)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDate(doc.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = doc.ocrLanguage,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
