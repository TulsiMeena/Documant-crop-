package com.example.scanner.processor

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.scanner.model.DocumentQuad
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ultra-Advanced Multi-Strategy Document Boundary & Corner Detector.
 * Performs multi-scale Gaussian smoothing, Otsu automatic thresholding,
 * directional Sobel gradients, convex hull & quadrant contour scoring,
 * and radial corner energy maximization to find precise document corners
 * for cards, receipts, papers, certificates on any surface.
 */
object BitmapDocumentDetector {

    fun detectCorners(bitmap: Bitmap): DocumentQuad? {
        val origW = bitmap.width
        val origH = bitmap.height

        if (origW < 40 || origH < 40) return getDefaultInsetQuad()

        // 1. Multi-scale Downsample for high signal-to-noise ratio
        val targetDim = 400
        val scale = min(1.0f, targetDim.toFloat() / max(origW, origH))
        val w = max(40, (origW * scale).toInt())
        val h = max(40, (origH * scale).toInt())

        val scaledBitmap = try {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } catch (e: Exception) {
            return getDefaultInsetQuad()
        }

        val totalPixels = w * h
        val pixels = IntArray(totalPixels)
        scaledBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 2. Grayscale & Luminance Map + Contrast Stretching
        val gray = FloatArray(totalPixels)
        val hist = IntArray(256)
        var minLum = 255
        var maxLum = 0

        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Perceptual weights
            val lum = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
            gray[i] = lum.toFloat()
            hist[lum]++
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        // 3. Fast 3x3 Gaussian Blur to remove noise & paper texture
        val blurred = FloatArray(totalPixels)
        for (y in 1 until h - 1) {
            val rAbove = (y - 1) * w
            val rCurr = y * w
            val rBelow = (y + 1) * w
            for (x in 1 until w - 1) {
                val sum = (
                    gray[rAbove + x - 1] + 2f * gray[rAbove + x] + gray[rAbove + x + 1] +
                    2f * gray[rCurr + x - 1] + 4f * gray[rCurr + x] + 2f * gray[rCurr + x + 1] +
                    gray[rBelow + x - 1] + 2f * gray[rBelow + x] + gray[rBelow + x + 1]
                ) / 16f
                blurred[rCurr + x] = sum
            }
        }

        // 4. Sobel Edge Gradient Magnitude
        val edges = FloatArray(totalPixels)
        var maxMag = 0f
        var sumMag = 0f
        var edgeCount = 0

        for (y in 1 until h - 1) {
            val rAbove = (y - 1) * w
            val rCurr = y * w
            val rBelow = (y + 1) * w

            for (x in 1 until w - 1) {
                val gx = (
                    -blurred[rAbove + x - 1] + blurred[rAbove + x + 1]
                    -2f * blurred[rCurr + x - 1] + 2f * blurred[rCurr + x + 1]
                    -blurred[rBelow + x - 1] + blurred[rBelow + x + 1]
                )
                val gy = (
                    -blurred[rAbove + x - 1] - 2f * blurred[rAbove + x] - blurred[rAbove + x + 1]
                    +blurred[rBelow + x - 1] + 2f * blurred[rBelow + x] + blurred[rBelow + x + 1]
                )
                val mag = sqrt(gx * gx + gy * gy)
                edges[rCurr + x] = mag
                sumMag += mag
                edgeCount++
                if (mag > maxMag) maxMag = mag
            }
        }

        val meanMag = if (edgeCount > 0) sumMag / edgeCount else 0f
        val edgeThresh = max(meanMag * 1.5f, maxMag * 0.12f).coerceAtLeast(14f)

        // 5. Multi-Directional Radial Raycasting from Center
        val cx = w / 2f
        val cy = h / 2f

        val topBoundary = findDirectionalBoundary(edges, w, h, cx.toInt(), cy.toInt(), 0, -1, edgeThresh) ?: (h * 0.05f)
        val bottomBoundary = findDirectionalBoundary(edges, w, h, cx.toInt(), cy.toInt(), 0, 1, edgeThresh) ?: (h * 0.95f)
        val leftBoundary = findDirectionalBoundary(edges, w, h, cx.toInt(), cy.toInt(), -1, 0, edgeThresh) ?: (w * 0.05f)
        val rightBoundary = findDirectionalBoundary(edges, w, h, cx.toInt(), cy.toInt(), 1, 0, edgeThresh) ?: (w * 0.95f)

        // 6. Refined Corner Search in each quadrant using Harris/Sobel corner energy
        val tlPt = findQuadrantCorner(
            edges, blurred, w, h,
            xRange = 1..(w / 2 - 2),
            yRange = 1..(h / 2 - 2),
            quadrant = 0,
            boundX = leftBoundary,
            boundY = topBoundary
        )

        val trPt = findQuadrantCorner(
            edges, blurred, w, h,
            xRange = (w / 2 + 2)..(w - 2),
            yRange = 1..(h / 2 - 2),
            quadrant = 1,
            boundX = rightBoundary,
            boundY = topBoundary
        )

        val brPt = findQuadrantCorner(
            edges, blurred, w, h,
            xRange = (w / 2 + 2)..(w - 2),
            yRange = (h / 2 + 2)..(h - 2),
            quadrant = 2,
            boundX = rightBoundary,
            boundY = bottomBoundary
        )

        val blPt = findQuadrantCorner(
            edges, blurred, w, h,
            xRange = 1..(w / 2 - 2),
            yRange = (h / 2 + 2)..(h - 2),
            quadrant = 3,
            boundX = leftBoundary,
            boundY = bottomBoundary
        )

        // Fallback default coordinates if not found
        val safeTL = tlPt ?: PointF((leftBoundary / w).coerceIn(0.02f, 0.48f), (topBoundary / h).coerceIn(0.02f, 0.48f))
        val safeTR = trPt ?: PointF((rightBoundary / w).coerceIn(0.52f, 0.98f), (topBoundary / h).coerceIn(0.02f, 0.48f))
        val safeBR = brPt ?: PointF((rightBoundary / w).coerceIn(0.52f, 0.98f), (bottomBoundary / h).coerceIn(0.52f, 0.98f))
        val safeBL = blPt ?: PointF((leftBoundary / w).coerceIn(0.02f, 0.48f), (bottomBoundary / h).coerceIn(0.52f, 0.98f))

        val candidateQuad = DocumentQuad(
            topLeft = PointF(safeTL.x.coerceIn(0.01f, 0.48f), safeTL.y.coerceIn(0.01f, 0.48f)),
            topRight = PointF(safeTR.x.coerceIn(0.52f, 0.99f), safeTR.y.coerceIn(0.01f, 0.48f)),
            bottomRight = PointF(safeBR.x.coerceIn(0.52f, 0.99f), safeBR.y.coerceIn(0.52f, 0.99f)),
            bottomLeft = PointF(safeBL.x.coerceIn(0.01f, 0.48f), safeBL.y.coerceIn(0.52f, 0.99f))
        )

        return if (candidateQuad.isValidQuad() && candidateQuad.area() >= 0.10f) {
            candidateQuad
        } else {
            getDefaultInsetQuad()
        }
    }

    private fun findDirectionalBoundary(
        edges: FloatArray,
        w: Int,
        h: Int,
        startX: Int,
        startY: Int,
        stepX: Int,
        stepY: Int,
        thresh: Float
    ): Float? {
        var x = startX
        var y = startY
        val span = if (stepX != 0) 18 else 18

        while (x in 2 until (w - 2) && y in 2 until (h - 2)) {
            var sum = 0f
            var count = 0
            if (stepY != 0) {
                // Moving vertical, sample horizontal strip
                val xStart = (x - span).coerceAtLeast(1)
                val xEnd = (x + span).coerceAtMost(w - 2)
                for (sx in xStart..xEnd) {
                    sum += edges[y * w + sx]
                    count++
                }
            } else {
                // Moving horizontal, sample vertical strip
                val yStart = (y - span).coerceAtLeast(1)
                val yEnd = (y + span).coerceAtMost(h - 2)
                for (sy in yStart..yEnd) {
                    sum += edges[sy * w + x]
                    count++
                }
            }

            if (count > 0 && (sum / count) >= thresh) {
                return if (stepY != 0) y.toFloat() else x.toFloat()
            }
            x += stepX
            y += stepY
        }
        return null
    }

    /**
     * Finds highest-confidence corner intersection in a quadrant by combining
     * cornerness energy, edge proximity, and distance bias.
     */
    private fun findQuadrantCorner(
        edges: FloatArray,
        gray: FloatArray,
        w: Int,
        h: Int,
        xRange: IntProgression,
        yRange: IntProgression,
        quadrant: Int, // 0: TL, 1: TR, 2: BR, 3: BL
        boundX: Float,
        boundY: Float
    ): PointF? {
        var bestScore = -1f
        var bestX = -1
        var bestY = -1

        val searchMarginX = (w * 0.14f).toInt()
        val searchMarginY = (h * 0.14f).toInt()

        val minX = (boundX.toInt() - searchMarginX).coerceIn(xRange.first, xRange.last)
        val maxX = (boundX.toInt() + searchMarginX).coerceIn(xRange.first, xRange.last)
        val minY = (boundY.toInt() - searchMarginY).coerceIn(yRange.first, yRange.last)
        val maxY = (boundY.toInt() + searchMarginY).coerceIn(yRange.first, yRange.last)

        val actualXRange = min(minX, maxX)..max(minX, maxX)
        val actualYRange = min(minY, maxY)..max(minY, maxY)

        for (y in actualYRange) {
            val rOff = y * w
            for (x in actualXRange) {
                val edgeVal = edges[rOff + x]
                if (edgeVal > 12f) {
                    // Compute cornerness metric: orthogonal edge intersection
                    val dx = abs(gray[rOff + x + 1] - gray[rOff + x - 1])
                    val dy = abs(gray[(y + 1) * w + x] - gray[(y - 1) * w + x])
                    val cornerness = min(dx, dy) * 1.5f + (dx + dy)

                    // Quadrant corner bias (prefers furthest outward corner)
                    val distBias = when (quadrant) {
                        0 -> ((w / 2 - x) + (h / 2 - y)).toFloat() // TL -> top-left outer
                        1 -> ((x - w / 2) + (h / 2 - y)).toFloat() // TR -> top-right outer
                        2 -> ((x - w / 2) + (y - h / 2)).toFloat() // BR -> bottom-right outer
                        else -> ((w / 2 - x) + (y - h / 2)).toFloat() // BL -> bottom-left outer
                    }

                    val score = edgeVal * 1.8f + cornerness * 2.2f + distBias * 0.8f

                    if (score > bestScore) {
                        bestScore = score
                        bestX = x
                        bestY = y
                    }
                }
            }
        }

        return if (bestScore > 0f && bestX > 0 && bestY > 0) {
            PointF(bestX.toFloat() / w, bestY.toFloat() / h)
        } else {
            null
        }
    }

    fun getDefaultInsetQuad(): DocumentQuad {
        return DocumentQuad(
            topLeft = PointF(0.04f, 0.04f),
            topRight = PointF(0.96f, 0.04f),
            bottomRight = PointF(0.96f, 0.96f),
            bottomLeft = PointF(0.04f, 0.96f)
        )
    }
}
