package app.openscan.domain

import android.content.Context
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import java.io.File

data class PdfPageSource(val image: File, val hocr: String?)

data class PdfOptions(
    val searchable: Boolean = true,
    val password: String? = null,
    val jpegQuality: Float = 0.82f
)

/**
 * PDF generation: JPEG pages, optional invisible OCR text layer (searchable/selectable),
 * optional AES/RC4 password protection and adjustable compression.
 *
 * PDF pages are sized so that 150 image pixels map to 72 PDF points (72 dpi), which
 * keeps the hOCR pixel coordinates easy to translate into PDF space.
 */
object PdfTools {

    private const val IMAGE_DPI = 150f
    private const val POINTS_PER_INCH = 72f

    fun export(
        context: Context,
        pages: List<PdfPageSource>,
        output: File,
        options: PdfOptions,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        if (pages.isEmpty()) error("Document has no pages")
        PDFBoxResourceLoader.init(context.applicationContext)

        var document: PDDocument? = null
        try {
            val pdf = PDDocument()
            document = pdf

            val font: PDType0Font? = if (options.searchable && pages.any { !it.hocr.isNullOrBlank() }) {
                runCatching {
                    context.assets.open("fonts/NotoSans-Regular.ttf").use { input ->
                        PDType0Font.load(pdf, input, true)
                    }
                }.getOrNull()
            } else {
                null
            }

            pages.forEachIndexed { index, source ->
                val bitmap = BitmapFactory.decodeFile(source.image.absolutePath)
                if (bitmap != null) {
                    try {
                        val pointsPerPixel = POINTS_PER_INCH / IMAGE_DPI
                        val widthPt = bitmap.width * pointsPerPixel
                        val heightPt = bitmap.height * pointsPerPixel
                        val page = PDPage(PDRectangle(widthPt, heightPt))
                        pdf.addPage(page)

                        val image = JPEGFactory.createFromImage(pdf, bitmap, options.jpegQuality)
                        PDPageContentStream(pdf, page).use { stream ->
                            stream.drawImage(image, 0f, 0f, widthPt, heightPt)
                        }

                        if (font != null && !source.hocr.isNullOrBlank()) {
                            drawInvisibleText(
                                pdf,
                                page,
                                font,
                                source.hocr,
                                widthPt,
                                heightPt,
                                bitmap.width,
                                bitmap.height
                            )
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(index + 1, pages.size)
            }

            if (!options.password.isNullOrBlank()) {
                val policy = StandardProtectionPolicy(
                    options.password,
                    options.password,
                    AccessPermission()
                )
                policy.encryptionKeyLength = 128
                pdf.protect(policy)
            }

            output.parentFile?.mkdirs()
            pdf.save(output)
        } finally {
            runCatching { document?.close() }
        }
    }

    private fun drawInvisibleText(
        pdf: PDDocument,
        page: PDPage,
        font: PDType0Font,
        hocr: String,
        widthPt: Float,
        heightPt: Float,
        pixelWidth: Int,
        pixelHeight: Int
    ) {
        val words = parseWords(hocr)
        if (words.isEmpty()) return
        val scaleX = widthPt / pixelWidth.coerceAtLeast(1)
        val scaleY = heightPt / pixelHeight.coerceAtLeast(1)

        PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.setRenderingMode(RenderingMode.NEITHER)
            words.forEach { word ->
                val text = word.text.trim()
                if (text.isEmpty()) return@forEach
                val fontSize = ((word.bottom - word.top) * scaleY * 0.82f).coerceIn(2f, 96f)
                val x = word.left * scaleX
                val y = heightPt - (word.bottom * scaleY) + fontSize * 0.18f
                runCatching {
                    stream.beginText()
                    stream.setFont(font, fontSize)
                    stream.newLineAtOffset(x, y)
                    stream.showText(text)
                    stream.endText()
                }.onFailure {
                    runCatching { stream.endText() }
                }
            }
        }
    }

    data class OcrWord(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private val wordSpan = Regex(
        """<span\b[^>]*class\s*=\s*["']ocrx_word["'][^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val bbox = Regex("""bbox\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)""")
    private val tag = Regex("<[^>]+>")

    /** Returns the pixel size of the image Tesseract used for this hOCR document. */
    fun parsePageSize(hocr: String): Pair<Int, Int>? {
        val pageIndex = hocr.indexOf("ocr_page")
        if (pageIndex < 0) return null
        val section = hocr.substring(pageIndex, minOf(hocr.length, pageIndex + 800))
        val match = bbox.find(section) ?: return null
        val values = match.groupValues
        val width = values[3].toIntOrNull() ?: return null
        val height = values[4].toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) return null
        return width to height
    }

    fun parseWords(hocr: String): List<OcrWord> {
        val words = ArrayList<OcrWord>()
        wordSpan.findAll(hocr).forEach { match ->
            val openingTag = match.value.substringBefore('>')
            val box = bbox.find(openingTag) ?: return@forEach
            val values = box.groupValues
            val text = unescape(match.groupValues[1].replace(tag, "")).trim()
            if (text.isNotEmpty()) {
                words += OcrWord(
                    text = text,
                    left = values[1].toFloat(),
                    top = values[2].toFloat(),
                    right = values[3].toFloat(),
                    bottom = values[4].toFloat()
                )
            }
        }
        return words
    }

    private fun unescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}
