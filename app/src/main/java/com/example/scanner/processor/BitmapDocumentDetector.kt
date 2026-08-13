package com.example.scanner.processor

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.scanner.model.DocumentQuad
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detects document corners on high-resolution Bitmaps (Captured camera photos or imported gallery images).
 */
object BitmapDocumentDetector {

    /**
     * Attempts to locate document 4 corners in normalized coordinates (0.0 .. 1.0).
     * Returns null if confidence is low or edges cannot be clearly established.
     */
    fun detectCorners(bitmap: Bitmap): DocumentQuad? {
        val origW = bitmap.width
        val origH = bitmap.height

        if (origW < 100 || origH < 100) return null

        // Downsample to ~320px max dimension for fast & robust edge detection
        val targetDim = 320
        val scale = min(1.0f, targetDim.toFloat() / max(origW, origH))
        val sampleW = max(40, (origW * scale).toInt())
        val sampleH = max(40, (origH * scale).toInt())

        val scaledBitmap = try {
            Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        } catch (e: Exception) {
            return null
        }

        val pixels = IntArray(sampleW * sampleH)
        scaledBitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        // Grayscale conversion
        val gray = IntArray(sampleW * sampleH)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        // Sobel Gradient Calculation
        val edges = FloatArray(sampleW * sampleH)
        var maxMag = 0f

        for (y in 1 until sampleH - 1) {
            val rAbove = (y - 1) * sampleW
            val rCurr = y * sampleW
            val rBelow = (y + 1) * sampleW

            for (x in 1 until sampleW - 1) {
                val gx = (gray[rCurr + x + 1] - gray[rCurr + x - 1])
                val gy = (gray[rBelow + x] - gray[rAbove + x])
                val mag = sqrt((gx * gx + gy * gy).toFloat())
                edges[rCurr + x] = mag
                if (mag > maxMag) maxMag = mag
            }
        }

        // If overall gradient magnitude across image is too uniform or weak, detection fails
        if (maxMag < 35f) return null

        val centerX = sampleW / 2
        val centerY = sampleH / 2
        val thresh = maxMag * 0.18f

        // Search boundaries along 4 main directional rays from center
        var topY = 2
        var bottomY = sampleH - 3
        var leftX = 2
        var rightX = sampleW - 3

        // Ray Up
        for (y in centerY downTo 2) {
            var sum = 0f
            val count = (sampleW * 0.5f).toInt()
            for (x in (sampleW * 0.25).toInt()..(sampleW * 0.75).toInt()) {
                sum += edges[y * sampleW + x]
            }
            if (sum / count > thresh) {
                topY = y
                break
            }
        }

        // Ray Down
        for (y in centerY until sampleH - 2) {
            var sum = 0f
            val count = (sampleW * 0.5f).toInt()
            for (x in (sampleW * 0.25).toInt()..(sampleW * 0.75).toInt()) {
                sum += edges[y * sampleW + x]
            }
            if (sum / count > thresh) {
                bottomY = y
                break
            }
        }

        // Ray Left
        for (x in centerX downTo 2) {
            var sum = 0f
            val count = (sampleH * 0.5f).toInt()
            for (y in (sampleH * 0.25).toInt()..(sampleH * 0.75).toInt()) {
                sum += edges[y * sampleW + x]
            }
            if (sum / count > thresh) {
                leftX = x
                break
            }
        }

        // Ray Right
        for (x in centerX until sampleW - 2) {
            var sum = 0f
            val count = (sampleH * 0.5f).toInt()
            for (y in (sampleH * 0.25).toInt()..(sampleH * 0.75).toInt()) {
                sum += edges[y * sampleW + x]
            }
            if (sum / count > thresh) {
                rightX = x
                break
            }
        }

        // Refine corners around detected boundary intersections
        val tl = findCornerPeak(edges, sampleW, sampleH, 2, max(3, leftX), 2, max(3, topY))
            ?: PointF(leftX.toFloat() / sampleW, topY.toFloat() / sampleH)

        val tr = findCornerPeak(edges, sampleW, sampleH, min(sampleW - 3, rightX), sampleW - 2, 2, max(3, topY))
            ?: PointF(rightX.toFloat() / sampleW, topY.toFloat() / sampleH)

        val br = findCornerPeak(edges, sampleW, sampleH, min(sampleW - 3, rightX), sampleW - 2, min(sampleH - 3, bottomY), sampleH - 2)
            ?: PointF(rightX.toFloat() / sampleW, bottomY.toFloat() / sampleH)

        val bl = findCornerPeak(edges, sampleW, sampleH, 2, max(3, leftX), min(sampleH - 3, bottomY), sampleH - 2)
            ?: PointF(leftX.toFloat() / sampleW, bottomY.toFloat() / sampleH)

        val quad = DocumentQuad(tl, tr, br, bl)

        // Validate detected quad quality
        return if (quad.isValidQuad() && quad.area() in 0.08f..0.94f) {
            quad
        } else {
            null
        }
    }

    /**
     * Returns default manual inset quad (e.g. 8% inset margin from image border).
     */
    fun getDefaultInsetQuad(): DocumentQuad {
        return DocumentQuad(
            topLeft = PointF(0.08f, 0.08f),
            topRight = PointF(0.92f, 0.08f),
            bottomRight = PointF(0.92f, 0.92f),
            bottomLeft = PointF(0.08f, 0.92f)
        )
    }

    private fun findCornerPeak(
        edges: FloatArray,
        w: Int,
        h: Int,
        xMin: Int,
        xMax: Int,
        yMin: Int,
        yMax: Int
    ): PointF? {
        if (xMin >= xMax || yMin >= yMax) return null

        var maxGrad = 0f
        var bestX = (xMin + xMax) / 2
        var bestY = (yMin + yMax) / 2

        for (y in max(0, yMin)..min(h - 1, yMax)) {
            val row = y * w
            for (x in max(0, xMin)..min(w - 1, xMax)) {
                val g = edges[row + x]
                if (g > maxGrad) {
                    maxGrad = g
                    bestX = x
                    bestY = y
                }
            }
        }

        return if (maxGrad > 20f) {
            PointF(bestX.toFloat() / w, bestY.toFloat() / h)
        } else {
            null
        }
    }
}
