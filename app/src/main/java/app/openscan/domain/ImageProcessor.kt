package app.openscan.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import app.openscan.data.Filter
import app.openscan.util.BitmapUtils
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OverlayPlacement(
    val bitmap: Bitmap,
    val x: Float,
    val y: Float,
    val width: Float
)

data class RedactionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * OpenCV based document pipeline: page detection, perspective correction, enhancement filters.
 */
object ImageProcessor {

    fun fullCrop(): List<Float> = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    /**
     * Detects the page quadrilateral in the frame. Returns 8 normalized coordinates
     * (top-left, top-right, bottom-right, bottom-left) in the 0..1 range.
     *
     * Two independent passes are used — Canny contour tracing and adaptive thresholding of
     * the bright page — then the best scoring convex quadrilateral is kept. This is much
     * more reliable than a single Canny pass on shadows, wood grain or patterned tables.
     */
    fun detect(bitmap: Bitmap, previewMaxDim: Int = 1200): List<Float> {
        val scale = min(1.0, previewMaxDim.toDouble() / max(bitmap.width, bitmap.height))
        val work = if (scale < 1.0) {
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true
            )
        } else {
            bitmap
        }
        val frameWidth = work.width
        val frameHeight = work.height
        val frameArea = frameWidth.toDouble() * frameHeight.toDouble()

        val candidates = ArrayList<Candidate>()
        val src = Mat()
        val gray = Mat()

        try {
            Utils.bitmapToMat(work, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            detectFromEdges(gray, frameWidth, frameHeight, frameArea, candidates)
            detectFromThreshold(gray, frameWidth, frameHeight, frameArea, candidates)
        } catch (t: Throwable) {
            // Fall through to the safe default below.
        } finally {
            src.release()
            gray.release()
            if (work !== bitmap) work.recycle()
        }

        val usable = candidates.filter { candidate ->
            validQuad(candidate.points, frameWidth, frameHeight, candidate.area, frameArea)
        }
        if (usable.isEmpty()) return fullCrop()

        val largest = usable.maxByOrNull { it.score }!!
        val interior = usable.filter {
            val ratio = it.area / frameArea
            ratio in 0.15..0.94
        }
        val chosen = if (largest.area / frameArea > 0.96 && interior.isNotEmpty() &&
            interior.maxOf { it.area } > largest.area * 0.35
        ) {
            interior.maxByOrNull { it.score }!!
        } else {
            largest
        }

        val ordered = order(chosen.points)
        val w = frameWidth.toFloat()
        val h = frameHeight.toFloat()
        return ordered.flatMap { point ->
            listOf(
                (point.x / w).toFloat().coerceIn(0f, 1f),
                (point.y / h).toFloat().coerceIn(0f, 1f)
            )
        }
    }

    private fun detectFromEdges(
        gray: Mat,
        frameWidth: Int,
        frameHeight: Int,
        frameArea: Double,
        out: MutableList<Candidate>
    ) {
        val edges = Mat()
        val closed = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        var kernel: Mat? = null
        try {
            Imgproc.Canny(gray, edges, 45.0, 135.0)
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel)
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(
                closed,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            contours.forEach { contour ->
                addCandidates(contour, frameWidth, frameHeight, frameArea, out)
            }
        } finally {
            edges.release()
            closed.release()
            hierarchy.release()
            kernel?.release()
            contours.forEach { it.release() }
        }
    }

    private fun detectFromThreshold(
        gray: Mat,
        frameWidth: Int,
        frameHeight: Int,
        frameArea: Double,
        out: MutableList<Candidate>
    ) {
        val binary = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        try {
            var block = (min(gray.width(), gray.height()) / 22).coerceIn(31, 151)
            if (block % 2 == 0) block++
            Imgproc.adaptiveThreshold(
                gray,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                block,
                12.0
            )
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
            Imgproc.findContours(
                binary,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            contours.forEach { contour ->
                addCandidates(contour, frameWidth, frameHeight, frameArea, out)
            }
        } finally {
            binary.release()
            hierarchy.release()
            kernel.release()
            contours.forEach { it.release() }
        }
    }

    private fun addCandidates(
        contour: MatOfPoint,
        frameWidth: Int,
        frameHeight: Int,
        frameArea: Double,
        out: MutableList<Candidate>
    ) {
        val area = Imgproc.contourArea(contour)
        if (area < frameArea * 0.06) return

        val curve = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        var rectArea = area
        try {
            val perimeter = Imgproc.arcLength(curve, true)
            var found = false
            for (epsilon in floatArrayOf(0.018f, 0.025f, 0.035f, 0.05f)) {
                Imgproc.approxPolyDP(curve, approx, epsilon * perimeter, true)
                val points = approx.toArray().toList()
                if (points.size == 4) {
                    val polygon = MatOfPoint(*points.toTypedArray())
                    val convex = Imgproc.isContourConvex(polygon)
                    polygon.release()
                    if (convex) {
                        out += Candidate(points, area, area, score(area, frameArea, area, area))
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                val rect = Imgproc.minAreaRect(curve)
                val rectArray = arrayOfNulls<Point>(4)
                rect.points(rectArray)
                val rectPoints = rectArray.filterNotNull()
                rectArea = rect.size.area()
                if (rectArea > 0 && area / rectArea > 0.55) {
                    out += Candidate(
                        rectPoints,
                        area,
                        rectArea,
                        score(area, frameArea, area, rectArea) * 0.92
                    )
                }
            }
        } finally {
            curve.release()
            approx.release()
        }
    }

    private fun score(area: Double, frameArea: Double, contourArea: Double, rectArea: Double): Double {
        val ratio = (contourArea / frameArea).coerceIn(0.0, 1.0)
        val rectangularity = (contourArea / rectArea.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return ratio * (0.65 + 0.35 * rectangularity)
    }

    private fun validQuad(
        rawPoints: List<Point>,
        frameWidth: Int,
        frameHeight: Int,
        area: Double,
        frameArea: Double
    ): Boolean {
        if (rawPoints.size != 4) return false
        if (area / frameArea < 0.07) return false
        val points = order(rawPoints)
        val minSide = min(frameWidth, frameHeight) * 0.07
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % 4]
            if (distance(a, b) < minSide) return false
        }
        for (index in points.indices) {
            val current = points[index]
            val prev = points[(index + 3) % 4]
            val next = points[(index + 1) % 4]
            val ax = prev.x - current.x
            val ay = prev.y - current.y
            val bx = next.x - current.x
            val by = next.y - current.y
            val lengthA = hypot(ax, ay)
            val lengthB = hypot(bx, by)
            if (lengthA < 1.0 || lengthB < 1.0) return false
            val cosine = (ax * bx + ay * by) / (lengthA * lengthB)
            if (cosine < -0.80) return false
        }
        val width = max(distance(points[0], points[1]), distance(points[3], points[2]))
        val height = max(distance(points[0], points[3]), distance(points[1], points[2]))
        if (width < 1.0 || height < 1.0) return false
        val aspect = width / height
        return aspect in 0.18..5.5
    }

    private data class Candidate(
        val points: List<Point>,
        val area: Double,
        val rectArea: Double,
        val score: Double
    )

    /** Warps the quadrilateral defined by [crop] to a straight rectangle. */
    fun warp(bitmap: Bitmap, crop: List<Float>?): Bitmap {
        if (crop == null || crop.size < 8) return bitmap
        val pts = toPixels(crop, bitmap.width, bitmap.height)
        val topWidth = distance(pts[0], pts[1])
        val bottomWidth = distance(pts[3], pts[2])
        val leftHeight = distance(pts[0], pts[3])
        val rightHeight = distance(pts[1], pts[2])

        val outWidth = max(topWidth, bottomWidth).roundToInt()
        val outHeight = max(leftHeight, rightHeight).roundToInt()
        if (outWidth < 32 || outHeight < 32) return bitmap
        if (outWidth > 6000 || outHeight > 6000) return bitmap

        val src = Mat()
        val warped = Mat()
        val srcQuad = MatOfPoint2f(*pts.toTypedArray())
        val dstQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((outWidth - 1).toDouble(), 0.0),
            Point((outWidth - 1).toDouble(), (outHeight - 1).toDouble()),
            Point(0.0, (outHeight - 1).toDouble())
        )
        return try {
            Utils.bitmapToMat(bitmap, src)
            val transform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
            Imgproc.warpPerspective(
                src,
                warped,
                transform,
                Size(outWidth.toDouble(), outHeight.toDouble()),
                Imgproc.INTER_CUBIC
            )
            transform.release()
            val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, result)
            result
        } catch (t: Throwable) {
            bitmap
        } finally {
            srcQuad.release()
            dstQuad.release()
            src.release()
            warped.release()
        }
    }

    fun applyFilter(bitmap: Bitmap, filter: Filter): Bitmap {
        if (filter == Filter.ORIGINAL) return bitmap
        val src = Mat()
        val out = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            when (filter) {
                Filter.ORIGINAL -> Unit

                Filter.GRAY -> {
                    val gray = Mat()
                    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGBA)
                    gray.release()
                }

                Filter.BW -> {
                    val gray = Mat()
                    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                    var block = (min(gray.width(), gray.height()) / 40).coerceIn(15, 75)
                    if (block % 2 == 0) block++
                    Imgproc.adaptiveThreshold(
                        gray,
                        gray,
                        255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY,
                        block,
                        10.0
                    )
                    Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGBA)
                    gray.release()
                }

                Filter.MAGIC -> {
                    val rgb = Mat()
                    val lab = Mat()
                    val hsv = Mat()
                    val blur = Mat()
                    val labChannels = ArrayList<Mat>()
                    val hsvChannels = ArrayList<Mat>()
                    try {
                        Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
                        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
                        Core.split(lab, labChannels)
                        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                        val enhanced = Mat()
                        clahe.apply(labChannels[0], enhanced)
                        enhanced.copyTo(labChannels[0])
                        enhanced.release()
                        Core.merge(labChannels, lab)
                        Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)

                        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
                        Core.split(hsv, hsvChannels)
                        val saturated = Mat()
                        hsvChannels[1].convertTo(saturated, CvType.CV_8UC1, 1.12, 0.0)
                        saturated.copyTo(hsvChannels[1])
                        saturated.release()
                        Core.merge(hsvChannels, hsv)
                        Imgproc.cvtColor(hsv, rgb, Imgproc.COLOR_HSV2RGB)

                        Imgproc.GaussianBlur(rgb, blur, Size(0.0, 0.0), 2.0)
                        Core.addWeighted(rgb, 1.25, blur, -0.25, 0.0, rgb)
                        Imgproc.cvtColor(rgb, out, Imgproc.COLOR_RGB2RGBA)
                    } finally {
                        rgb.release()
                        lab.release()
                        hsv.release()
                        blur.release()
                        labChannels.forEach { it.release() }
                        hsvChannels.forEach { it.release() }
                    }
                }
            }

            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, result)
            result
        } catch (t: Throwable) {
            bitmap
        } finally {
            src.release()
            out.release()
        }
    }

    fun process(
        original: Bitmap,
        crop: List<Float>?,
        filter: Filter,
        rotation: Int
    ): Bitmap {
        val warped = warp(original, crop)
        val filtered = applyFilter(warped, filter)
        val rotated = BitmapUtils.rotate(filtered, rotation)

        if (warped !== original && warped !== filtered && warped !== rotated) {
            warped.recycle()
        }
        if (filtered !== original && filtered !== rotated) {
            filtered.recycle()
        }
        return rotated
    }

    /** Draws movable layers on top of a processed page, preserving each layer's aspect ratio. */
    fun composite(base: Bitmap, overlays: List<OverlayPlacement>): Bitmap {
        if (overlays.isEmpty()) return base
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        overlays.forEach { overlay ->
            if (overlay.bitmap.width <= 0 || overlay.bitmap.height <= 0) return@forEach
            val targetWidth = (result.width * overlay.width)
                .roundToInt()
                .coerceIn(1, result.width)
            val targetHeight = (targetWidth.toFloat() * overlay.bitmap.height /
                overlay.bitmap.width).roundToInt().coerceIn(1, result.height)
            val left = (overlay.x * result.width)
                .roundToInt()
                .coerceIn(0, (result.width - targetWidth).coerceAtLeast(0))
            val top = (overlay.y * result.height)
                .roundToInt()
                .coerceIn(0, (result.height - targetHeight).coerceAtLeast(0))
            canvas.drawBitmap(
                overlay.bitmap,
                null,
                Rect(left, top, left + targetWidth, top + targetHeight),
                paint
            )
        }
        return result
    }

    /** Permanently burns black privacy boxes into a page. Coordinates are normalized. */
    fun redact(base: Bitmap, boxes: List<RedactionBox>): Bitmap {
        if (boxes.isEmpty()) return base
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val padding = (min(result.width, result.height) * 0.008f).roundToInt()
        boxes.forEach { box ->
            val left = (box.left * result.width).roundToInt() - padding
            val top = (box.top * result.height).roundToInt() - padding
            val right = (box.right * result.width).roundToInt() + padding
            val bottom = (box.bottom * result.height).roundToInt() + padding
            canvas.drawRect(
                Rect(
                    left.coerceAtLeast(0),
                    top.coerceAtLeast(0),
                    right.coerceAtMost(result.width),
                    bottom.coerceAtMost(result.height)
                ),
                paint
            )
        }
        return result
    }

    fun stampSignature(page: Bitmap, signature: Bitmap): Bitmap {
        val result = page.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val targetWidth = (result.width * 0.32f).roundToInt()
        val targetHeight =
            (targetWidth * signature.height.toFloat() / signature.width.coerceAtLeast(1)).roundToInt()
        val marginX = (result.width * 0.06f).roundToInt()
        val marginY = (result.height * 0.06f).roundToInt()
        val left = result.width - targetWidth - marginX
        val top = result.height - targetHeight - marginY
        val destination = Rect(left, top, left + targetWidth, top + targetHeight)
        canvas.drawBitmap(
            signature,
            null,
            destination,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        return result
    }

    private fun toPixels(crop: List<Float>, width: Int, height: Int): List<Point> =
        listOf(
            Point(crop[0].toDouble() * width, crop[1].toDouble() * height),
            Point(crop[2].toDouble() * width, crop[3].toDouble() * height),
            Point(crop[4].toDouble() * width, crop[5].toDouble() * height),
            Point(crop[6].toDouble() * width, crop[7].toDouble() * height)
        )

    private fun order(points: List<Point>): List<Point> {
        if (points.size != 4) return points
        val centerX = points.sumOf { it.x } / 4.0
        val centerY = points.sumOf { it.y } / 4.0
        val sorted = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val start = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: 0
        return List(4) { sorted[(start + it) % 4] }
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)
}
