package app.openscan.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.openscan.data.DocumentRepository
import app.openscan.data.Filter
import app.openscan.data.PageOverlay
import app.openscan.data.ScanDocument
import app.openscan.data.ScanPage
import app.openscan.domain.DocxExporter
import app.openscan.domain.ImageProcessor
import app.openscan.domain.OverlayPlacement
import app.openscan.domain.RedactionBox
import app.openscan.domain.OcrEngine
import app.openscan.domain.PdfOptions
import app.openscan.domain.PdfPageSource
import app.openscan.domain.PdfTools
import app.openscan.domain.XlsxExporter
import app.openscan.util.BitmapUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class OverlaySaveItem(
    val id: String?,
    val kind: String,
    val source: File,
    val stored: Boolean,
    val x: Float,
    val y: Float,
    val width: Float
)

data class OverlayBaseImage(
    val file: File,
    val width: Int,
    val height: Int
)

data class PrivacyRiskResult(
    val boxes: List<RedactionBox>,
    val counts: Map<String, Int>
)

private data class OcrWordRef(
    val word: PdfTools.OcrWord,
    val start: Int,
    val end: Int
)

private data class RiskMatch(
    val start: Int,
    val end: Int,
    val label: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)
    private val ocrEngine = OcrEngine(repository.tesseractDir)
    private val preferences = application.getSharedPreferences("shfrascan", Context.MODE_PRIVATE)
    private val ocrMutex = Mutex()

    var documents by mutableStateOf<List<ScanDocument>>(emptyList())
        private set

    var document by mutableStateOf<ScanDocument?>(null)
        private set

    var busy by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var languages by mutableStateOf<List<String>>(emptyList())
        private set

    var defaultFilter by mutableStateOf(Filter.MAGIC)
        private set

    var ocrLanguage by mutableStateOf("eng")
        private set

    init {
        defaultFilter = Filter.from(preferences.getString(KEY_FILTER, Filter.MAGIC.key))
        ocrLanguage = preferences.getString(KEY_LANGUAGE, "eng") ?: "eng"
        viewModelScope.launch {
            runCatching {
                repository.ensureBundledAssets()
                languages = repository.installedLanguages()
                if (languages.isNotEmpty() && ocrLanguage !in languages) {
                    ocrLanguage = languages.first()
                }
                documents = repository.list()
            }.onFailure { message = it.message ?: "Could not load documents" }
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }

    fun imageFile(docId: String, page: ScanPage): File =
        repository.file(docId, page.image)

    fun originalFile(docId: String, page: ScanPage): File =
        repository.file(docId, page.original ?: page.image)

    fun overlayFile(docId: String, overlay: PageOverlay): File =
        repository.file(docId, overlay.file)

    suspend fun detectCrop(docId: String, pageId: String): List<Float>? =
        withContext(Dispatchers.Default) {
            val doc = repository.load(docId) ?: return@withContext null
            val page = doc.pages.firstOrNull { it.id == pageId } ?: return@withContext null
            val bitmap = BitmapUtils.decode(
                repository.file(docId, page.original ?: page.image),
                MAX_PAGE_DIMENSION
            ) ?: return@withContext null
            try {
                ImageProcessor.detect(bitmap, 1600)
            } finally {
                bitmap.recycle()
            }
        }

    suspend fun overlayBaseFile(docId: String, pageId: String): OverlayBaseImage? =
        guarded("Preparing layers…") {
            val doc = repository.load(docId) ?: error("Document not found")
            val page = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
            val base = renderPage(
                docId = docId,
                page = page,
                crop = page.crop,
                filter = Filter.from(page.filter),
                rotation = page.rotation,
                overlays = emptyList()
            )
            val cache = File(
                getApplication<Application>().cacheDir,
                "overlay_base_${page.id}_${System.currentTimeMillis()}.jpg"
            )
            BitmapUtils.saveJpeg(base, cache, 95)
            val width = base.width
            val height = base.height
            base.recycle()
            getApplication<Application>().cacheDir
                .listFiles { file -> file.name.startsWith("overlay_base_${page.id}_") }
                ?.filter { it != cache }
                ?.forEach { runCatching { it.delete() } }
            OverlayBaseImage(cache, width, height)
        }

    suspend fun saveOverlays(
        docId: String,
        pageId: String,
        items: List<OverlaySaveItem>
    ): Boolean = guarded("Applying layers…") {
        val doc = repository.load(docId) ?: error("Document not found")
        val page = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        val oldFiles = page.overlays.map { it.file }.toSet()
        val keptFiles = HashSet<String>()
        val records = items.mapNotNull { item ->
            val fileName = if (item.stored) {
                item.source.name
            } else {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.decode(item.source, MAX_PAGE_DIMENSION)
                } ?: return@mapNotNull null
                val name = "overlay_${UUID.randomUUID()}.png"
                try {
                    withContext(Dispatchers.IO) {
                        BitmapUtils.savePng(bitmap, repository.file(docId, name))
                    }
                } finally {
                    bitmap.recycle()
                }
                name
            }
            if (!repository.file(docId, fileName).exists()) return@mapNotNull null
            keptFiles += fileName
            val width = item.width.coerceIn(0.03f, 1f)
            PageOverlay(
                id = item.id ?: UUID.randomUUID().toString(),
                kind = item.kind,
                file = fileName,
                x = item.x.coerceIn(0f, 1f - width),
                y = item.y.coerceIn(0f, 0.99f),
                width = width
            )
        }

        val rendered = renderPage(
            docId = docId,
            page = page,
            crop = page.crop,
            filter = Filter.from(page.filter),
            rotation = page.rotation,
            overlays = records
        )
        try {
            withContext(Dispatchers.IO) {
                BitmapUtils.saveJpeg(rendered, repository.file(docId, page.image), 92)
                page.hocrFile?.let { runCatching { repository.file(docId, it).delete() } }
            }
            (oldFiles - keptFiles).forEach { stale ->
                runCatching { repository.file(docId, stale).delete() }
            }
            val updated = repository.update(docId) { current ->
                current.copy(
                    pages = current.pages.map { item ->
                        if (item.id == pageId) {
                            item.copy(
                                overlays = records,
                                width = rendered.width,
                                height = rendered.height,
                                ocrText = null,
                                hocrFile = null
                            )
                        } else {
                            item
                        }
                    }
                )
            }
            document = updated ?: document
            documents = repository.list()
            true
        } finally {
            rendered.recycle()
        }
    } ?: false

    fun refresh() {
        viewModelScope.launch {
            documents = withContext(Dispatchers.IO) { repository.list() }
        }
    }

    suspend fun loadDocument(id: String): ScanDocument? {
        val loaded = repository.load(id)
        document = loaded
        return loaded
    }

    fun clearDocument() {
        document = null
    }

    fun chooseDefaultFilter(filter: Filter) {
        defaultFilter = filter
        preferences.edit().putString(KEY_FILTER, filter.key).apply()
    }

    fun chooseOcrLanguage(language: String) {
        ocrLanguage = language
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
    }

    suspend fun importLanguage(uri: Uri): Boolean = guarded("Installing language…") {
        val language = repository.importTrainedData(uri)
        languages = repository.installedLanguages()
        chooseOcrLanguage(language)
        message = "OCR language \"$language\" installed"
        true
    } ?: false

    suspend fun importImages(docId: String?, uris: List<Uri>): String? =
        guarded("Importing images…") {
            if (uris.isEmpty()) error("No images selected")
            val doc = if (docId == null) repository.create(scanName("Import"))
            else repository.load(docId) ?: error("Document not found")

            var pages = doc.pages
            var imported = 0
            var failed = 0
            uris.forEach { uri ->
                val temp = File(
                    getApplication<Application>().cacheDir,
                    "import_${UUID.randomUUID()}.img"
                )
                try {
                    withContext(Dispatchers.IO) {
                        BitmapUtils.copyUriToFile(getApplication(), uri, temp)
                    }
                    val original = withContext(Dispatchers.IO) {
                        BitmapUtils.decodeOriented(temp, MAX_PAGE_DIMENSION)
                    } ?: error("Could not decode the selected image")
                    val crop = withContext(Dispatchers.Default) { ImageProcessor.detect(original) }
                    val processed = withContext(Dispatchers.Default) {
                        ImageProcessor.process(original, crop, defaultFilter, 0)
                    }
                    val page = repository.newPage(
                        docId = doc.id,
                        sourceFile = temp,
                        processed = processed,
                        original = original,
                        crop = crop,
                        filter = defaultFilter,
                        rotation = 0
                    )
                    original.recycle()
                    processed.recycle()
                    pages = pages + page
                    repository.update(doc.id) {
                        it.copy(
                            pages = pages,
                            ocrLanguage = it.ocrLanguage.ifBlank { ocrLanguage }
                        )
                    }?.let { updated -> pages = updated.pages; document = updated }
                    imported++
                } catch (cancellation: CancellationException) {
                    temp.delete()
                    throw cancellation
                } catch (t: Throwable) {
                    temp.delete()
                    failed++
                }
            }

            if (imported == 0) error("Could not import the selected image(s)")
            documents = repository.list()
            document = repository.load(doc.id) ?: document
            message = if (failed == 0) {
                "Imported $imported page(s)"
            } else {
                "Imported $imported page(s), skipped $failed"
            }
            doc.id
        }

    suspend fun importPdf(docId: String?, uri: Uri): String? =
        guarded("Importing PDF…") {
            val doc = if (docId == null) repository.create(scanName("PDF import"))
            else repository.load(docId) ?: error("Document not found")

            val temp = File(
                getApplication<Application>().cacheDir,
                "import_${UUID.randomUUID()}.pdf"
            )
            var pages = doc.pages
            var imported = 0
            var failed = 0

            try {
                withContext(Dispatchers.IO) {
                    BitmapUtils.copyUriToFile(getApplication(), uri, temp)
                }
                val descriptor = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                try {
                    val renderer = withContext(Dispatchers.IO) { PdfRenderer(descriptor) }
                    try {
                        val pageCount = renderer.pageCount
                        if (pageCount == 0) error("This PDF has no pages")
                        for (index in 0 until pageCount) {
                            val bitmap = try {
                                withContext(Dispatchers.IO) { renderPdfPage(renderer, index) }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (t: Throwable) {
                                failed++
                                continue
                            }
                            try {
                                val crop = ImageProcessor.fullCrop()
                                val processed = withContext(Dispatchers.Default) {
                                    ImageProcessor.process(bitmap, crop, Filter.ORIGINAL, 0)
                                }
                                val page = repository.newPage(
                                    docId = doc.id,
                                    sourceFile = null,
                                    processed = processed,
                                    original = bitmap,
                                    crop = crop,
                                    filter = Filter.ORIGINAL,
                                    rotation = 0
                                )
                                bitmap.recycle()
                                processed.recycle()
                                pages = pages + page
                                repository.update(doc.id) {
                                    it.copy(
                                        pages = pages,
                                        ocrLanguage = it.ocrLanguage.ifBlank { ocrLanguage }
                                    )
                                }?.let { updated -> pages = updated.pages; document = updated }
                                imported++
                            } catch (cancellation: CancellationException) {
                                bitmap.recycle()
                                throw cancellation
                            } catch (t: Throwable) {
                                bitmap.recycle()
                                failed++
                            }
                        }
                    } finally {
                        runCatching { renderer.close() }
                    }
                } finally {
                    runCatching { descriptor.close() }
                }
            } finally {
                temp.delete()
            }

            if (imported == 0) error("Could not import this PDF")
            documents = repository.list()
            document = repository.load(doc.id) ?: document
            message = if (failed == 0) {
                "Imported $imported PDF page(s)"
            } else {
                "Imported $imported PDF page(s), skipped $failed"
            }
            doc.id
        }

    private fun renderPdfPage(renderer: PdfRenderer, index: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val width = (page.width * PDF_RENDER_SCALE).toInt().coerceIn(300, 2480)
            val height = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(300)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun scanName(prefix: String): String =
        prefix + " " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    suspend fun addCapturedPage(docId: String?, cameraFile: File): String? =
        guarded("Processing page…") {
            val doc = if (docId == null) {
                repository.create(
                    "Scan " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                )
            } else {
                repository.load(docId) ?: error("Document not found")
            }

            val original = withContext(Dispatchers.IO) {
                BitmapUtils.decodeOriented(cameraFile, MAX_PAGE_DIMENSION)
            } ?: error("Could not decode the photo")

            val crop = withContext(Dispatchers.Default) { ImageProcessor.detect(original) }
            val processed = withContext(Dispatchers.Default) {
                ImageProcessor.process(original, crop, defaultFilter, 0)
            }

            val page = repository.newPage(
                docId = doc.id,
                sourceFile = cameraFile,
                processed = processed,
                original = original,
                crop = crop,
                filter = defaultFilter,
                rotation = 0
            )

            val updated = repository.update(doc.id) { current ->
                current.copy(
                    pages = current.pages + page,
                    ocrLanguage = current.ocrLanguage.ifBlank { ocrLanguage }
                )
            }

            document = updated
            documents = repository.list()
            doc.id
        }

    suspend fun applyPageEdits(
        docId: String,
        pageId: String,
        crop: List<Float>?,
        filter: Filter,
        rotation: Int
    ): Boolean = guarded("Applying changes…") {
        val doc = repository.load(docId) ?: error("Document not found")
        val page = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        val normalizedRotation = ((rotation % 360) + 360) % 360
        val processed = renderPage(
            docId = docId,
            page = page,
            crop = crop,
            filter = filter,
            rotation = normalizedRotation,
            overlays = page.overlays
        )

        val processedWidth = processed.width
        val processedHeight = processed.height
        try {
            withContext(Dispatchers.IO) {
                BitmapUtils.saveJpeg(processed, repository.file(docId, page.image), 92)
                page.hocrFile?.let { runCatching { repository.file(docId, it).delete() } }
            }
        } finally {
            processed.recycle()
        }

        val updated = repository.update(docId) { current ->
            current.copy(
                pages = current.pages.map { item ->
                    if (item.id == pageId) {
                        item.copy(
                            filter = filter.key,
                            rotation = normalizedRotation,
                            crop = crop,
                            width = processedWidth,
                            height = processedHeight,
                            ocrText = null,
                            hocrFile = null
                        )
                    } else {
                        item
                    }
                }
            )
        }

        document = updated ?: document
        documents = repository.list()
        true
    } ?: false

    suspend fun rotatePage(docId: String, pageId: String, degrees: Int): Boolean {
        val doc = repository.load(docId) ?: return false
        val page = doc.pages.firstOrNull { it.id == pageId } ?: return false
        return applyPageEdits(
            docId = docId,
            pageId = pageId,
            crop = page.crop,
            filter = Filter.from(page.filter),
            rotation = page.rotation + degrees
        )
    }

    suspend fun stampSignature(docId: String, pageId: String, signature: File): Boolean {
        val doc = repository.load(docId) ?: return false
        val page = doc.pages.firstOrNull { it.id == pageId } ?: return false
        val width = 0.32f
        val items = page.overlays.map { overlay ->
            OverlaySaveItem(
                id = overlay.id,
                kind = overlay.kind,
                source = repository.file(docId, overlay.file),
                stored = true,
                x = overlay.x,
                y = overlay.y,
                width = overlay.width
            )
        } + OverlaySaveItem(
            id = null,
            kind = PageOverlay.SIGNATURE,
            source = signature,
            stored = false,
            x = 0.62f,
            y = 0.75f,
            width = width
        )
        return saveOverlays(docId, pageId, items)
    }

    suspend fun deletePage(docId: String, pageId: String) {
        val doc = repository.load(docId) ?: return
        val page = doc.pages.firstOrNull { it.id == pageId } ?: return
        repository.deletePageFiles(docId, page)
        val updated = repository.update(docId) { current ->
            current.copy(pages = current.pages.filterNot { it.id == pageId })
        }
        document = updated ?: document
        documents = repository.list()
    }

    suspend fun movePage(docId: String, pageId: String, offset: Int) {
        val updated = repository.update(docId) { current ->
            val index = current.pages.indexOfFirst { it.id == pageId }
            if (index < 0) return@update current
            val target = (index + offset).coerceIn(0, current.pages.lastIndex)
            if (target == index) return@update current
            val mutable = current.pages.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            current.copy(pages = mutable)
        }
        document = updated ?: document
        documents = repository.list()
    }

    suspend fun renameDocument(docId: String, name: String) {
        val updated = repository.update(docId) {
            it.copy(name = name.trim().ifBlank { it.name })
        }
        document = updated ?: document
        documents = repository.list()
    }

    suspend fun deleteDocument(docId: String) {
        repository.delete(docId)
        if (document?.id == docId) document = null
        documents = repository.list()
    }

    suspend fun mergeDocuments(ids: List<String>, name: String): String? =
        guarded("Merging documents…") {
            if (ids.size < 2) error("Select at least two documents")
            val docs = ids.mapNotNull { repository.load(it) }
            if (docs.size < 2) error("Could not load documents")
            val target = repository.create(name.ifBlank { "Merged scan" })
            val pages = repository.copyPages(
                target.id,
                docs.flatMap { doc -> doc.pages.map { page -> doc.id to page } }
            )
            val updated = repository.update(target.id) { it.copy(pages = pages) }
            documents = repository.list()
            document = updated
            message = "Merged ${docs.size} documents"
            target.id
        }

    suspend fun runOcr(
        docId: String,
        pageId: String? = null,
        language: String? = null
    ): Boolean = guarded("Recognising text…") {
        val doc = repository.load(docId) ?: error("Document not found")
        val chosen = language ?: doc.ocrLanguage.ifBlank { ocrLanguage }
        if (chosen !in repository.installedLanguages()) {
            error("OCR language \"$chosen\" is not installed. Import it in Settings.")
        }
        chooseOcrLanguage(chosen)
        val result = recognize(doc, pageId, chosen)
        document = result
        documents = repository.list()
        true
    } ?: false

    suspend fun exportPdf(docId: String, options: PdfOptions): File? =
        guarded("Preparing PDF…") {
            var doc = repository.load(docId) ?: error("Document not found")
            if (options.searchable) {
                doc = ensureOcr(doc)
            }
            val sources = doc.pages.mapNotNull { page ->
                val image = repository.file(doc.id, page.image)
                if (!image.exists()) return@mapNotNull null
                PdfPageSource(image, page.hocrFile?.let { readHocr(doc.id, it) })
            }
            if (sources.isEmpty()) error("This document has no pages")

            val output = repository.exportFile(doc.name, "pdf")
            PdfTools.export(getApplication<Application>(), sources, output, options) { done, total ->
                busy = "Rendering PDF $done/$total…"
            }
            document = repository.load(docId)
            message = "PDF saved: ${output.name}"
            output
        }

    suspend fun exportDocx(docId: String): File? = guarded("Preparing DOCX…") {
        var doc = repository.load(docId) ?: error("Document not found")
        doc = ensureOcr(doc)
        val pages = doc.pages.map { it.ocrText.orEmpty() }
        val output = repository.exportFile(doc.name, "docx")
        withContext(Dispatchers.IO) { DocxExporter.export(pages, output) }
        message = "DOCX saved: ${output.name}"
        output
    }

    suspend fun exportText(docId: String): File? = guarded("Preparing text…") {
        var doc = repository.load(docId) ?: error("Document not found")
        doc = ensureOcr(doc)
        val pages = doc.pages.map { it.ocrText.orEmpty() }
        val output = repository.exportFile(doc.name, "txt")
        withContext(Dispatchers.IO) { DocxExporter.exportText(pages, output) }
        message = "Text saved: ${output.name}"
        output
    }

    suspend fun exportXlsx(docId: String): File? = guarded("Preparing Excel…") {
        var doc = repository.load(docId) ?: error("Document not found")
        doc = ensureOcr(doc)
        val pages = doc.pages.map { it.ocrText.orEmpty() }
        if (pages.all { it.isBlank() }) error("No text was recognized in this document")
        val output = repository.exportFile(doc.name, "xlsx")
        withContext(Dispatchers.IO) { XlsxExporter.exportXlsx(pages, output) }
        message = "Excel saved: ${output.name}"
        output
    }

    suspend fun exportCsv(docId: String): File? = guarded("Preparing CSV…") {
        var doc = repository.load(docId) ?: error("Document not found")
        doc = ensureOcr(doc)
        val pages = doc.pages.map { it.ocrText.orEmpty() }
        if (pages.all { it.isBlank() }) error("No text was recognized in this document")
        val output = repository.exportFile(doc.name, "csv")
        withContext(Dispatchers.IO) { XlsxExporter.exportCsv(pages, output) }
        message = "CSV saved: ${output.name}"
        output
    }

    suspend fun splitDocument(docId: String): Int? = guarded("Splitting document…") {
        val doc = repository.load(docId) ?: error("Document not found")
        if (doc.pages.size < 2) error("A document needs at least two pages to split")
        doc.pages.forEachIndexed { index, page ->
            val target = repository.create(doc.name + " " + (index + 1))
            val copied = repository.copyPages(target.id, listOf(docId to page))
            repository.update(target.id) {
                it.copy(pages = copied, ocrLanguage = doc.ocrLanguage)
            }
        }
        repository.delete(docId)
        if (document?.id == docId) document = null
        documents = repository.list()
        message = "Split into ${doc.pages.size} single-page documents"
        doc.pages.size
    }

    suspend fun exportImages(docId: String): List<File>? = guarded("Exporting images…") {
        val doc = repository.load(docId) ?: error("Document not found")
        if (doc.pages.isEmpty()) error("This document has no pages")
        val safeName = doc.name.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifBlank { "scan" }
        val directory = File(repository.exportsDir, safeName).apply { mkdirs() }
        withContext(Dispatchers.IO) {
            doc.pages.mapIndexed { index, page ->
                val target = File(directory, "%03d.jpg".format(index + 1))
                repository.file(doc.id, page.image).copyTo(target, overwrite = true)
                target
            }
        }
    }

    private suspend fun renderPage(
        docId: String,
        page: ScanPage,
        crop: List<Float>?,
        filter: Filter,
        rotation: Int,
        overlays: List<PageOverlay>
    ): Bitmap {
        val sourceName = page.original ?: page.image
        val original = withContext(Dispatchers.IO) {
            BitmapUtils.decode(repository.file(docId, sourceName), MAX_PAGE_DIMENSION)
        } ?: error("Could not decode page")
        val base = if (page.original == null) {
            original
        } else {
            withContext(Dispatchers.Default) {
                ImageProcessor.process(original, crop, filter, rotation)
            }
        }
        val placements = overlays.mapNotNull { overlay ->
            val bitmap = withContext(Dispatchers.IO) {
                BitmapUtils.decode(repository.file(docId, overlay.file), MAX_PAGE_DIMENSION)
            } ?: return@mapNotNull null
            OverlayPlacement(
                bitmap = bitmap,
                x = overlay.x,
                y = overlay.y,
                width = overlay.width
            )
        }
        val result = try {
            if (placements.isEmpty()) {
                base
            } else {
                withContext(Dispatchers.Default) {
                    ImageProcessor.composite(base, placements)
                }
            }
        } finally {
            placements.forEach { it.bitmap.recycle() }
        }
        if (base !== original) {
            if (result !== base) base.recycle()
            original.recycle()
        } else if (result !== original) {
            original.recycle()
        }
        return result
    }

    suspend fun findPrivacyRisks(docId: String, pageId: String): PrivacyRiskResult? =
        guarded("Checking for private data…") {
            var doc = repository.load(docId) ?: error("Document not found")
            var page = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
            if (page.ocrText.isNullOrBlank() || page.hocrFile == null) {
                val language = doc.ocrLanguage.ifBlank { ocrLanguage }
                if (language !in repository.installedLanguages()) {
                    error("OCR language \"$language\" is not installed")
                }
                doc = recognize(doc, pageId, language)
                page = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
                document = doc
                documents = repository.list()
            }
            val hocr = page.hocrFile?.let { readHocr(docId, it) }
                ?: return@guarded PrivacyRiskResult(emptyList(), emptyMap())
            val words = PdfTools.parseWords(hocr)
            if (words.isEmpty()) {
                return@guarded PrivacyRiskResult(emptyList(), emptyMap())
            }
            val pageSize = PdfTools.parsePageSize(hocr)
            val pageWidth = (pageSize?.first ?: words.maxOf { it.right }).toFloat().coerceAtLeast(1f)
            val pageHeight = (pageSize?.second ?: words.maxOf { it.bottom }).toFloat().coerceAtLeast(1f)

            val text = StringBuilder()
            val refs = ArrayList<OcrWordRef>()
            words.forEachIndexed { index, word ->
                if (index > 0) text.append(' ')
                val start = text.length
                text.append(word.text)
                refs += OcrWordRef(word, start, text.length)
            }
            val joined = text.toString()

            val candidates = ArrayList<RiskMatch>()
            fun collect(pattern: Regex, label: String, validate: (String) -> Boolean = { true }) {
                pattern.findAll(joined).forEach { match ->
                    if (validate(match.value)) {
                        candidates += RiskMatch(match.range.first, match.range.last + 1, label)
                    }
                }
            }
            collect(EMAIL_REGEX, "Email addresses")
            collect(CARD_REGEX, "Card numbers") { luhn(it) }
            collect(IBAN_REGEX, "IBAN / account numbers")
            collect(PHONE_REGEX, "Phone numbers") { it.count(Char::isDigit) >= 7 }

            val accepted = ArrayList<RiskMatch>()
            candidates.sortedBy { it.start }.forEach { match ->
                if (accepted.none { it.start < match.end && match.start < it.end }) {
                    accepted += match
                }
            }

            val boxes = ArrayList<RedactionBox>()
            val counts = LinkedHashMap<String, Int>()
            accepted.forEach { match ->
                val selected = refs.filter { it.end > match.start && it.start < match.end }
                if (selected.isEmpty()) return@forEach
                val left = selected.minOf { it.word.left }
                val top = selected.minOf { it.word.top }
                val right = selected.maxOf { it.word.right }
                val bottom = selected.maxOf { it.word.bottom }
                val box = RedactionBox(
                    left = (left / pageWidth).coerceIn(0f, 1f),
                    top = (top / pageHeight).coerceIn(0f, 1f),
                    right = (right / pageWidth).coerceIn(0f, 1f),
                    bottom = (bottom / pageHeight).coerceIn(0f, 1f)
                )
                if (boxes.none { overlaps(it, box) }) {
                    boxes += box
                    counts[match.label] = (counts[match.label] ?: 0) + 1
                }
            }
            PrivacyRiskResult(boxes = boxes, counts = counts)
        }

    suspend fun redactPage(
        docId: String,
        pageId: String,
        boxes: List<RedactionBox>
    ): Boolean = guarded("Redacting…") {
        val doc = repository.load(docId) ?: error("Document not found")
        val page = doc.pages.firstOrNull { it.id == pageId } ?: error("Page not found")
        if (boxes.isEmpty()) error("Nothing to redact")
        val bitmap = withContext(Dispatchers.IO) {
            BitmapUtils.decode(repository.file(docId, page.image), MAX_PAGE_DIMENSION)
        } ?: error("Could not decode page")
        val redacted = try {
            withContext(Dispatchers.Default) { ImageProcessor.redact(bitmap, boxes) }
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
        try {
            val originalName = page.original ?: "original_${page.id}.jpg"
            withContext(Dispatchers.IO) {
                BitmapUtils.saveJpeg(redacted, repository.file(docId, page.image), 92)
                BitmapUtils.saveJpeg(redacted, repository.file(docId, originalName), 95)
                page.hocrFile?.let { runCatching { repository.file(docId, it).delete() } }
                page.overlays.forEach { overlay ->
                    runCatching { repository.file(docId, overlay.file).delete() }
                }
            }
            val updated = repository.update(docId) { current ->
                current.copy(
                    pages = current.pages.map { item ->
                        if (item.id == pageId) {
                            item.copy(
                                original = originalName,
                                crop = ImageProcessor.fullCrop(),
                                filter = Filter.ORIGINAL.key,
                                rotation = 0,
                                overlays = emptyList(),
                                width = redacted.width,
                                height = redacted.height,
                                ocrText = null,
                                hocrFile = null
                            )
                        } else {
                            item
                        }
                    }
                )
            }
            document = updated ?: document
            documents = repository.list()
            message = "Redacted ${boxes.size} sensitive value" +
                if (boxes.size == 1) "" else "s"
            true
        } finally {
            if (redacted !== bitmap) bitmap.recycle()
            redacted.recycle()
        }
    } ?: false

    private fun overlaps(a: RedactionBox, b: RedactionBox): Boolean {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return false
        val intersection = (right - left) * (bottom - top)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        return intersection / minOf(aArea, bArea).coerceAtLeast(0.0001f) > 0.35f
    }

    private fun luhn(value: String): Boolean {
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 13..19) return false
        var sum = 0
        var double = false
        for (index in digits.lastIndex downTo 0) {
            var digit = digits[index] - '0'
            if (double) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            double = !double
        }
        return sum % 10 == 0
    }

    private suspend fun ensureOcr(doc: ScanDocument): ScanDocument {
        val missing = doc.pages.filter { it.ocrText.isNullOrBlank() || it.hocrFile == null }
        if (missing.isEmpty()) return doc
        val language = doc.ocrLanguage.ifBlank { ocrLanguage }
        if (language !in repository.installedLanguages()) {
            error("OCR language \"$language\" is not installed. Import it in Settings.")
        }
        var current = doc
        for (page in missing) {
            current = recognize(current, pageId = page.id, language = language)
        }
        return current
    }

    private suspend fun recognize(
        doc: ScanDocument,
        pageId: String?,
        language: String
    ): ScanDocument = withContext(Dispatchers.IO) {
        ocrMutex.withLock {
            var current = doc
            val targets = doc.pages.filter { pageId == null || it.id == pageId }
            targets.forEachIndexed { index, page ->
                busy = "Recognising page ${index + 1}/${targets.size}…"
                val bitmap = BitmapUtils.decode(
                    repository.file(doc.id, page.image),
                    MAX_OCR_DIMENSION
                ) ?: return@forEachIndexed
                val result = try {
                    ocrEngine.recognize(bitmap, language)
                } finally {
                    bitmap.recycle()
                }
                val hocrFile = repository.writeHocr(doc.id, page.id, result.hocr)
                current = repository.update(doc.id) { latest ->
                    latest.copy(
                        ocrLanguage = language,
                        pages = latest.pages.map { item ->
                            if (item.id == page.id) {
                                item.copy(ocrText = result.text, hocrFile = hocrFile)
                            } else {
                                item
                            }
                        }
                    )
                } ?: current
            }
            current
        }
    }

    private fun readHocr(docId: String, name: String): String? =
        runCatching { repository.file(docId, name).readText() }.getOrNull()

    private suspend fun <T> guarded(label: String, block: suspend () -> T): T? {
        busy = label
        return try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            message = t.message ?: "Unexpected error"
            null
        } finally {
            busy = null
        }
    }

    companion object {
        private const val KEY_FILTER = "default_filter"
        private const val KEY_LANGUAGE = "ocr_language"
        private const val MAX_PAGE_DIMENSION = 3000
        private const val MAX_OCR_DIMENSION = 2200
        private const val PDF_RENDER_SCALE = 150f / 72f
        private val EMAIL_REGEX =
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        private val CARD_REGEX = Regex("""(?<!\d)(?:\d[ -]?){13,19}(?!\d)""")
        private val IBAN_REGEX = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b""")
        private val PHONE_REGEX =
            Regex("""(?<!\d)(?:\+?\d[\d ()\-.]{6,}\d)(?!\d)""")
    }
}
