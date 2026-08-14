package com.example.scanner.detector

import android.graphics.PointF
import androidx.camera.core.ImageProxy
import com.example.scanner.model.DocumentQuad
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time Document Edge and Corner Detector analyzing CameraX YUV_420_888 frames.
 */
class DocumentDetector {

    /**
     * Analyzes an [ImageProxy] frame and returns detected document quad in normalized (0..1) space.
     * Always safely closes the [ImageProxy].
     */
    fun detectDocument(image: ImageProxy): DocumentQuad? {
        return try {
            val rotationDegrees = image.imageInfo.rotationDegrees
            val yBuffer = image.planes[0].buffer
            val width = image.width
            val height = image.height
            val rowStride = image.planes[0].rowStride

            val rawQuad = detectInYBuffer(yBuffer, width, height, rowStride) ?: return null

            // Map normalized points accounting for CameraX rotation
            mapRotation(rawQuad, rotationDegrees)
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }
    }

    private fun detectInYBuffer(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int
    ): DocumentQuad? {
        // Downsample for fast analysis (target ~160x120 grid)
        val scale = max(1, width / 160)
        val targetW = width / scale
        val targetH = height / scale

        if (targetW < 40 || targetH < 40) return null

        val gray = ByteArray(targetW * targetH)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Fill downsampled grayscale grid
        for (y in 0 until targetH) {
            val srcY = y * scale
            val rowStart = srcY * rowStride
            for (x in 0 until targetW) {
                val srcX = x * scale
                val index = rowStart + srcX
                if (index < bytes.size) {
                    gray[y * targetW + x] = bytes[index]
                }
            }
        }

        // Compute edge magnitude grid
        val edges = FloatArray(targetW * targetH)
        var maxGrad = 0.0f

        for (y in 1 until targetH - 1) {
            val rowAbove = (y - 1) * targetW
            val rowCurrent = y * targetW
            val rowBelow = (y + 1) * targetW

            for (x in 1 until targetW - 1) {
                val gx = (gray[rowCurrent + x + 1].toInt() and 0xFF) - (gray[rowCurrent + x - 1].toInt() and 0xFF)
                val gy = (gray[rowBelow + x].toInt() and 0xFF) - (gray[rowAbove + x].toInt() and 0xFF)
                val mag = (gx * gx + gy * gy).toFloat()
                edges[rowCurrent + x] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        if (maxGrad < 400.0f) return null // Insufficient contrast / uniform background

        // Radial ray scanning from center to find 4 document boundaries
        val centerX = targetW / 2
        val centerY = targetH / 2

        var topY = centerY
        var bottomY = centerY
        var leftX = centerX
        var rightX = centerX

        // Scan Up for Top Edge
        val threshold = maxGrad * 0.15f
        for (y in centerY downTo 2) {
            var sumGrad = 0f
            for (x in (targetW * 0.25).toInt()..(targetW * 0.75).toInt()) {
                sumGrad += edges[y * targetW + x]
            }
            if (sumGrad > threshold * (targetW * 0.5f)) {
                topY = y
                break
            }
        }

        // Scan Down for Bottom Edge
        for (y in centerY until targetH - 2) {
            var sumGrad = 0f
            for (x in (targetW * 0.25).toInt()..(targetW * 0.75).toInt()) {
                sumGrad += edges[y * targetW + x]
            }
            if (sumGrad > threshold * (targetW * 0.5f)) {
                bottomY = y
                break
            }
        }

        // Scan Left for Left Edge
        for (x in centerX downTo 2) {
            var sumGrad = 0f
            for (y in (targetH * 0.25).toInt()..(targetH * 0.75).toInt()) {
                sumGrad += edges[y * targetW + x]
            }
            if (sumGrad > threshold * (targetH * 0.5f)) {
                leftX = x
                break
            }
        }

        // Scan Right for Right Edge
        for (x in centerX until targetW - 2) {
            var sumGrad = 0f
            for (y in (targetH * 0.25).toInt()..(targetH * 0.75).toInt()) {
                sumGrad += edges[y * targetW + x]
            }
            if (sumGrad > threshold * (targetH * 0.5f)) {
                rightX = x
                break
            }
        }

        // Corner Refinement (Search inward from corners for highest gradient concentration)
        val tl = findCornerInRegion(edges, targetW, targetH, 2, leftX, 2, topY, true)
            ?: PointF(leftX.toFloat() / targetW, topY.toFloat() / targetH)

        val tr = findCornerInRegion(edges, targetW, targetH, rightX, targetW - 2, 2, topY, false)
            ?: PointF(rightX.toFloat() / targetW, topY.toFloat() / targetH)

        val br = findCornerInRegion(edges, targetW, targetH, rightX, targetW - 2, bottomY, targetH - 2, false)
            ?: PointF(rightX.toFloat() / targetW, bottomY.toFloat() / targetH)

        val bl = findCornerInRegion(edges, targetW, targetH, 2, leftX, bottomY, targetH - 2, true)
            ?: PointF(leftX.toFloat() / targetW, bottomY.toFloat() / targetH)

        val quad = DocumentQuad(tl, tr, br, bl)

        return if (quad.isValidQuad()) quad else null
    }

    private fun findCornerInRegion(
        edges: FloatArray,
        w: Int,
        h: Int,
        xMin: Int,
        xMax: Int,
        yMin: Int,
        yMax: Int,
        preferLeft: Boolean
    ): PointF? {
        if (xMin >= xMax || yMin >= yMax) return null

        var maxVal = 0f
        var bestX = (xMin + xMax) / 2
        var bestY = (yMin + yMax) / 2

        for (y in max(0, yMin)..min(h - 1, yMax)) {
            val row = y * w
            for (x in max(0, xMin)..min(w - 1, xMax)) {
                val valGrad = edges[row + x]
                if (valGrad > maxVal) {
                    maxVal = valGrad
                    bestX = x
                    bestY = y
                }
            }
        }

        return if (maxVal > 100f) {
            PointF(bestX.toFloat() / w, bestY.toFloat() / h)
        } else {
            null
        }
    }

    private fun mapRotation(quad: DocumentQuad, rotationDegrees: Int): DocumentQuad {
        return when (rotationDegrees) {
            90 -> DocumentQuad(
                topLeft = PointF(1f - quad.bottomLeft.y, quad.bottomLeft.x),
                topRight = PointF(1f - quad.topLeft.y, quad.topLeft.x),
                bottomRight = PointF(1f - quad.topRight.y, quad.topRight.x),
                bottomLeft = PointF(1f - quad.bottomRight.y, quad.bottomRight.x)
            )
            180 -> DocumentQuad(
                topLeft = PointF(1f - quad.bottomRight.x, 1f - quad.bottomRight.y),
                topRight = PointF(1f - quad.bottomLeft.x, 1f - quad.bottomLeft.y),
                bottomRight = PointF(1f - quad.topLeft.x, 1f - quad.topLeft.y),
                bottomLeft = PointF(1f - quad.topRight.x, 1f - quad.topRight.y)
            )
            270 -> DocumentQuad(
                topLeft = PointF(quad.topRight.y, 1f - quad.topRight.x),
                topRight = PointF(quad.bottomRight.y, 1f - quad.bottomRight.x),
                bottomRight = PointF(quad.bottomLeft.y, 1f - quad.bottomLeft.x),
                bottomLeft = PointF(quad.topLeft.y, 1f - quad.topLeft.x)
            )
            else -> quad
        }
    }
}
