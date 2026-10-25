package app.openscan.domain

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Thin wrapper around Tesseract4Android. One instance is used per recognition call because
 * TessBaseAPI is not thread safe.
 */
class OcrEngine(dataRoot: File) {

    data class Result(val text: String, val hocr: String)

    private val dataPath = dataRoot.apply { mkdirs() }.absolutePath

    fun recognize(bitmap: Bitmap, language: String): Result {
        val api = TessBaseAPI()
        try {
            if (!api.init(dataPath, language, TessBaseAPI.OEM_LSTM_ONLY)) {
                error("OCR language \"$language\" is not installed")
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setVariable("preserve_interword_spaces", "1")
            api.setImage(bitmap)
            val text = api.utF8Text ?: ""
            val hocr = runCatching { api.getHOCRText(0) }.getOrNull() ?: ""
            return Result(text.trim(), hocr)
        } finally {
            runCatching { api.recycle() }
        }
    }
}
