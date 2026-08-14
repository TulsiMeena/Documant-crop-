package com.example.scanner.processor

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.scanner.model.DocumentQuad
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Robust document corner and edge detector for static Bitmap images.
 * Uses multi-pass gradient and luminance analysis to detect document boundaries,
 * with graceful fallback to well-proportioned document insets.
 */
object BitmapDocumentDetector {

    /**
     * Attempts to locate document 4 corners in normalized coordinates (0.0 .. 1.0).
     */
    fun detectCorners(bitmap: Bitmap): DocumentQuad? {
        val origW = bitmap.width
        val origH = bitmap.height

        if (origW < 40 || origH < 40) return getDefaultInsetQuad()

        // 1. Downscale to ~320px for fast, noise-resistant edge detection
        val targetDim = 320
        val scale = min(1.0f, targetDim.toFloat() / max(origW, origH))
        val sampleW = max(30, (origW * scale).toInt())
        val sampleH = max(30, (origH * scale).toInt())

        val scaledBitmap = try {
            Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        } catch (e: Exception) {
            return getDefaultInsetQuad()
        }

        val pixels = IntArray(sampleW * sampleH)
        scaledBitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        // 2. Grayscale conversion
        val gray = IntArray(sampleW * sampleH)
        var totalLum = 0L
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            gray[i] = lum
            totalLum += lum
        }

        // 3. Sobel Gradient Magnitude
        val edges = FloatArray(sampleW * sampleH)
        var maxMag = 0f
        var totalMag = 0f
        var edgeCount = 0

        for (y in 1 until sampleH - 1) {
            val rAbove = (y - 1) * sampleW
            val rCurr = y * sampleW
            val rBelow = (y + 1) * sampleW

            for (x in 1 until sampleW - 1) {
                val gx = (gray[rCurr + x + 1] - gray[rCurr + x - 1])
                val gy = (gray[rBelow + x] - gray[rAbove + x])
                val mag = sqrt((gx * gx + gy * gy).toFloat())
                edges[rCurr + x] = mag
                totalMag += mag
                edgeCount++
                if (mag > maxMag) maxMag = mag
            }
        }

        val avgMag = if (edgeCount > 0) totalMag / edgeCount else 0f
        val threshold = max(avgMag * 1.5f, maxMag * 0.18f).coerceAtLeast(15f)

        val centerX = sampleW / 2
        val centerY = sampleH / 2

        // Strategy A: Scan outwards from center to find 4 boundary edges
        var topY = 2
        var bottomY = sampleH - 3
        var leftX = 2
        var rightX = sampleW - 3

        val xRangeStart = (sampleW * 0.20f).toInt()
        val xRangeEnd = (sampleW * 0.80f).toInt()
        val xRangeLen = max(1, xRangeEnd - xRangeStart)

        // Scan Up
        for (y in centerY downTo 2) {
            var sum = 0f
            val rowOffset = y * sampleW
            for (x in xRangeStart..xRangeEnd) {
                sum += edges[rowOffset + x]
            }
            if (sum / xRangeLen > threshold) {
                topY = y
                break
            }
        }

        // Scan Down
        for (y in centerY until sampleH - 2) {
            var sum = 0f
            val rowOffset = y * sampleW
            for (x in xRangeStart..xRangeEnd) {
                sum += edges[rowOffset + x]
            }
            if (sum / xRangeLen > threshold) {
                bottomY = y
                break
            }
        }

        val yRangeStart = (sampleH * 0.20f).toInt()
        val yRangeEnd = (sampleH * 0.80f).toInt()
        val yRangeLen = max(1, yRangeEnd - yRangeStart)

        // Scan Left
        for (x in centerX downTo 2) {
            var sum = 0f
            for (y in yRangeStart..yRangeEnd) {
                sum += edges[y * sampleW + x]
            }
            if (sum / yRangeLen > threshold) {
                leftX = x
                break
            }
        }

        // Scan Right
        for (x in centerX until sampleW - 2) {
            var sum = 0f
            for (y in yRangeStart..yRangeEnd) {
                sum += edges[y * sampleW + x]
            }
            if (sum / yRangeLen > threshold) {
                rightX = x
                break
            }
        }

        // Refine corners around detected boundary intersections
        val tl = findCornerPeak(edges, sampleW, sampleH, 1, max(3, leftX + 4), 1, max(3, topY + 4))
            ?: PointF(leftX.toFloat() / sampleW, topY.toFloat() / sampleH)

        val tr = findCornerPeak(edges, sampleW, sampleH, min(sampleW - 4, rightX - 4), sampleW - 2, 1, max(3, topY + 4))
            ?: PointF(rightX.toFloat() / sampleW, topY.toFloat() / sampleH)

        val br = findCornerPeak(edges, sampleW, sampleH, min(sampleW - 4, rightX - 4), sampleW - 2, min(sampleH - 4, bottomY - 4), sampleH - 2)
            ?: PointF(rightX.toFloat() / sampleW, bottomY.toFloat() / sampleH)

        val bl = findCornerPeak(edges, sampleW, sampleH, 1, max(3, leftX + 4), min(sampleH - 4, bottomY - 4), sampleH - 2)
            ?: PointF(leftX.toFloat() / sampleW, bottomY.toFloat() / sampleH)

        // Clamp normalized bounds
        val normTL = PointF(tl.x.coerceIn(0.02f, 0.40f), tl.y.coerceIn(0.02f, 0.40f))
        val normTR = PointF(tr.x.coerceIn(0.60f, 0.98f), tr.y.coerceIn(0.02f, 0.40f))
        val normBR = PointF(br.x.coerceIn(0.60f, 0.98f), br.y.coerceIn(0.60f, 0.98f))
        val normBL = PointF(bl.x.coerceIn(0.02f, 0.40f), bl.y.coerceIn(0.60f, 0.98f))

        val detectedQuad = DocumentQuad(normTL, normTR, normBR, normBL)

        return if (detectedQuad.isValidQuad() && detectedQuad.area() >= 0.15f) {
            detectedQuad
        } else {
            getDefaultInsetQuad()
        }
    }

    /**
     * Returns default manual inset quad (5% comfortable inset margin).
     */
    fun getDefaultInsetQuad(): DocumentQuad {
        return DocumentQuad(
            topLeft = PointF(0.05f, 0.05f),
            topRight = PointF(0.95f, 0.05f),
            bottomRight = PointF(0.95f, 0.95f),
            bottomLeft = PointF(0.05f, 0.95f)
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
        val minX = max(0, min(xMin, xMax))
        val maxX = min(w - 1, max(xMin, xMax))
        val minY = max(0, min(yMin, yMax))
        val maxY = min(h - 1, max(yMin, yMax))

        if (minX >= maxX || minY >= maxY) return null

        var maxGrad = 0f
        var bestX = (minX + maxX) / 2
        var bestY = (minY + maxY) / 2

        for (y in minY..maxY) {
            val row = y * w
            for (x in minX..maxX) {
                val g = edges[row + x]
                if (g > maxGrad) {
                    maxGrad = g
                    bestX = x
                    bestY = y
                }
            }
        }

        return if (maxGrad > 15f) {
            PointF(bestX.toFloat() / w, bestY.toFloat() / h)
        } else {
            null
        }
    }
}
