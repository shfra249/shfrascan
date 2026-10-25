package app.openscan.data

import kotlinx.serialization.Serializable

/**
 * One scanned page.
 *
 * All file names are relative to the document directory.
 * [image] is the processed (warped, filtered, rotated) JPEG that is shown and exported.
 * [original] is the orientation-corrected camera frame used for non-destructive re-cropping.
 */
@Serializable
data class ScanPage(
    val id: String,
    val image: String,
    val original: String? = null,
    val filter: String = Filter.MAGIC.key,
    val rotation: Int = 0,
    val crop: List<Float>? = null,
    val width: Int = 0,
    val height: Int = 0,
    val origWidth: Int = 0,
    val origHeight: Int = 0,
    val ocrText: String? = null,
    val hocrFile: String? = null,
    val overlays: List<PageOverlay> = emptyList()
)

/**
 * A non-destructive layer drawn on top of a processed page.
 * Coordinates are normalized to the finished page image.
 */
@Serializable
data class PageOverlay(
    val id: String,
    val kind: String,
    val file: String,
    val x: Float,
    val y: Float,
    val width: Float
) {
    companion object {
        const val SIGNATURE = "signature"
        const val IMAGE = "image"
    }
}

@Serializable
data class ScanDocument(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pages: List<ScanPage> = emptyList(),
    val ocrLanguage: String = "eng"
) {
    val cover: ScanPage? get() = pages.firstOrNull()
}

enum class Filter(val key: String, val label: String) {
    ORIGINAL("original", "Original"),
    MAGIC("magic", "Magic color"),
    GRAY("gray", "Grayscale"),
    BW("bw", "Black & white");

    companion object {
        fun from(key: String?): Filter = entries.firstOrNull { it.key == key } ?: MAGIC
    }
}
