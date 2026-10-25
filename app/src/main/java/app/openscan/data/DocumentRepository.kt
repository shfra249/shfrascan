package app.openscan.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.openscan.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * File based document store. Everything lives in the app's private storage:
 *
 *   files/scans/<document-id>/doc.json
 *   files/scans/<document-id>/page_<id>.jpg
 *   files/scans/<document-id>/original_<id>.jpg
 *   files/scans/<document-id>/hocr_<id>.html
 *   files/tesseract/tessdata/<lang>.traineddata
 *
 * Nothing leaves the device.
 */
class DocumentRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val root = File(context.filesDir, "scans").apply { mkdirs() }
    private val mutex = Mutex()

    val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
    val tesseractDir = File(context.filesDir, "tesseract").apply { mkdirs() }
    val tessdataDir = File(tesseractDir, "tessdata").apply { mkdirs() }

    fun documentDir(id: String): File = File(root, id).apply { mkdirs() }

    fun file(docId: String, name: String): File = File(documentDir(docId), name)

    suspend fun ensureBundledAssets() = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.list("tessdata")?.forEach { name ->
                if (!name.endsWith(".traineddata")) return@forEach
                val target = File(tessdataDir, name)
                if (!target.exists()) {
                    context.assets.open("tessdata/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    fun installedLanguages(): List<String> =
        tessdataDir.listFiles { f -> f.isFile && f.name.endsWith(".traineddata") }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    suspend fun importTrainedData(uri: Uri): String = withContext(Dispatchers.IO) {
        val display = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: "imported.traineddata"

        if (!display.endsWith(".traineddata", ignoreCase = true)) {
            error("Pick a *.traineddata file")
        }
        val language = display.removeSuffix(".traineddata")
        val target = File(tessdataDir, "$language.traineddata")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read file")
        language
    }

    suspend fun list(): List<ScanDocument> = withContext(Dispatchers.IO) {
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { loadNow(it.name) }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    suspend fun load(id: String): ScanDocument? = withContext(Dispatchers.IO) { loadNow(id) }

    suspend fun create(name: String): ScanDocument = withContext(Dispatchers.IO) {
        val doc = ScanDocument(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Scan" }
        )
        documentDir(doc.id)
        saveNow(doc)
    }

    suspend fun save(doc: ScanDocument): ScanDocument = withContext(Dispatchers.IO) {
        mutex.withLock { saveNow(doc) }
    }

    suspend fun update(
        id: String,
        transform: (ScanDocument) -> ScanDocument
    ): ScanDocument? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = loadNow(id) ?: return@withLock null
            saveNow(transform(current))
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        documentDir(id).deleteRecursively()
    }

    suspend fun deletePageFiles(docId: String, page: ScanPage) = withContext(Dispatchers.IO) {
        runCatching { file(docId, page.image).delete() }
        page.original?.let { runCatching { file(docId, it).delete() } }
        page.hocrFile?.let { runCatching { file(docId, it).delete() } }
        page.overlays.forEach { runCatching { file(docId, it.file).delete() } }
    }

    suspend fun writeHocr(docId: String, pageId: String, hocr: String): String =
        withContext(Dispatchers.IO) {
            val name = "hocr_$pageId.html"
            file(docId, name).writeText(hocr)
            name
        }

    suspend fun readHocr(docId: String, page: ScanPage): String? = withContext(Dispatchers.IO) {
        page.hocrFile?.let { name ->
            runCatching { file(docId, name).readText() }.getOrNull()
        }
    }

    suspend fun copyPages(
        targetDocId: String,
        sources: List<Pair<String, ScanPage>>
    ): List<ScanPage> = withContext(Dispatchers.IO) {
        sources.map { (sourceDocId, page) ->
            val newId = UUID.randomUUID().toString()
            val imageName = "page_$newId.jpg"
            val originalName = page.original?.let { "original_$newId.jpg" }
            val hocrName = page.hocrFile?.let { "hocr_$newId.html" }

            file(sourceDocId, page.image).copyTo(file(targetDocId, imageName), overwrite = true)
            if (page.original != null && originalName != null) {
                runCatching {
                    file(sourceDocId, page.original).copyTo(file(targetDocId, originalName), overwrite = true)
                }
            }
            if (page.hocrFile != null && hocrName != null) {
                runCatching {
                    file(sourceDocId, page.hocrFile).copyTo(file(targetDocId, hocrName), overwrite = true)
                }
            }

            val newOverlays = page.overlays.mapNotNull { overlay ->
                val extension = overlay.file.substringAfterLast('.', "png")
                val overlayName = "overlay_${UUID.randomUUID()}.$extension"
                runCatching {
                    file(sourceDocId, overlay.file).copyTo(file(targetDocId, overlayName), true)
                }.map { overlay.copy(file = overlayName) }.getOrNull()
            }

            page.copy(
                id = newId,
                image = imageName,
                original = originalName,
                hocrFile = hocrName,
                overlays = newOverlays
            )
        }
    }

    suspend fun newPage(
        docId: String,
        sourceFile: File?,
        processed: android.graphics.Bitmap,
        original: android.graphics.Bitmap,
        crop: List<Float>?,
        filter: Filter,
        rotation: Int
    ): ScanPage = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val imageName = "page_$id.jpg"
        val originalName = "original_$id.jpg"

        BitmapUtils.saveJpeg(processed, file(docId, imageName), 92)
        BitmapUtils.saveJpeg(original, file(docId, originalName), 95)
        sourceFile?.let { runCatching { it.delete() } }

        ScanPage(
            id = id,
            image = imageName,
            original = originalName,
            filter = filter.key,
            rotation = rotation,
            crop = crop,
            width = processed.width,
            height = processed.height,
            origWidth = original.width,
            origHeight = original.height
        )
    }

    fun exportFile(baseName: String, extension: String): File {
        val safe = baseName.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifBlank { "document" }
        var candidate = File(exportsDir, "$safe.$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(exportsDir, "$safe-${index++}.$extension")
        }
        return candidate
    }

    private fun loadNow(id: String): ScanDocument? {
        val file = File(documentDir(id), "doc.json")
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(ScanDocument.serializer(), file.readText())
        }.getOrNull()
    }

    private fun saveNow(doc: ScanDocument): ScanDocument {
        val updated = doc.copy(updatedAt = System.currentTimeMillis())
        val dir = documentDir(updated.id)
        val tmp = File(dir, "doc.json.tmp")
        tmp.writeText(json.encodeToString(ScanDocument.serializer(), updated))
        val target = File(dir, "doc.json")
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        return updated
    }
}
