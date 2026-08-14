package com.example.scanner.model

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Represents a document's four corners in normalized coordinates (0.0f .. 1.0f).
 * Top-Left, Top-Right, Bottom-Right, Bottom-Left.
 */
data class DocumentQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    /**
     * Calculates the approximate area of the normalized quad using Shoelace formula.
     * Returned value is between 0.0 and 1.0.
     */
    fun area(): Float {
        val x1 = topLeft.x; val y1 = topLeft.y
        val x2 = topRight.x; val y2 = topRight.y
        val x3 = bottomRight.x; val y3 = bottomRight.y
        val x4 = bottomLeft.x; val y4 = bottomLeft.y

        val shoelace = (x1 * y2 - y1 * x2) +
                (x2 * y3 - y2 * x3) +
                (x3 * y4 - y3 * x4) +
                (x4 * y1 - y4 * x1)

        return abs(shoelace) / 2.0f
    }

    /**
     * Checks if the quadrilateral is non-degenerate and has meaningful area.
     */
    fun isValidQuad(): Boolean {
        val a = area()
        // Allow anything from minimal crop to full boundary (0.005f to 1.0f)
        if (a < 0.005f) return false
        
        // Basic non-inversion check: Left corners shouldn't cross right corners completely
        if (topLeft.x > bottomRight.x && bottomLeft.x > topRight.x) return false
        if (topLeft.y > bottomRight.y && topRight.y > bottomLeft.y) return false

        return true
    }

    /**
     * Calculates maximum corner drift between this quad and another quad.
     */
    fun maxDrift(other: DocumentQuad): Float {
        val d1 = hypot((topLeft.x - other.topLeft.x).toDouble(), (topLeft.y - other.topLeft.y).toDouble())
        val d2 = hypot((topRight.x - other.topRight.x).toDouble(), (topRight.y - other.topRight.y).toDouble())
        val d3 = hypot((bottomRight.x - other.bottomRight.x).toDouble(), (bottomRight.y - other.bottomRight.y).toDouble())
        val d4 = hypot((bottomLeft.x - other.bottomLeft.x).toDouble(), (bottomLeft.y - other.bottomLeft.y).toDouble())

        return maxOf(d1, d2, d3, d4).toFloat()
    }

    companion object {
        fun defaultQuad(): DocumentQuad = DocumentQuad(
            topLeft = PointF(0.08f, 0.08f),
            topRight = PointF(0.92f, 0.08f),
            bottomRight = PointF(0.92f, 0.92f),
            bottomLeft = PointF(0.08f, 0.92f)
        )

        fun defaultInsetQuad(): DocumentQuad = defaultQuad()

        fun fullImageQuad(): DocumentQuad = DocumentQuad(
            topLeft = PointF(0.005f, 0.005f),
            topRight = PointF(0.995f, 0.005f),
            bottomRight = PointF(0.995f, 0.995f),
            bottomLeft = PointF(0.005f, 0.995f)
        )

        fun a4Quad(imageRatio: Float = 0.75f): DocumentQuad {
            // A4 is roughly 1 : 1.414 (width : height ratio = ~0.707)
            val halfW = 0.38f
            val halfH = 0.44f
            return DocumentQuad(
                topLeft = PointF((0.5f - halfW).coerceAtLeast(0.02f), (0.5f - halfH).coerceAtLeast(0.02f)),
                topRight = PointF((0.5f + halfW).coerceAtMost(0.98f), (0.5f - halfH).coerceAtLeast(0.02f)),
                bottomRight = PointF((0.5f + halfW).coerceAtMost(0.98f), (0.5f + halfH).coerceAtMost(0.98f)),
                bottomLeft = PointF((0.5f - halfW).coerceAtLeast(0.02f), (0.5f + halfH).coerceAtMost(0.98f))
            )
        }

        fun idCardQuad(): DocumentQuad {
            // ID Card is standard 85.60 × 53.98 mm (approx 1.58 : 1 horizontal)
            val halfW = 0.44f
            val halfH = 0.28f
            return DocumentQuad(
                topLeft = PointF((0.5f - halfW).coerceAtLeast(0.02f), (0.5f - halfH).coerceAtLeast(0.02f)),
                topRight = PointF((0.5f + halfW).coerceAtMost(0.98f), (0.5f - halfH).coerceAtLeast(0.02f)),
                bottomRight = PointF((0.5f + halfW).coerceAtMost(0.98f), (0.5f + halfH).coerceAtMost(0.98f)),
                bottomLeft = PointF((0.5f - halfW).coerceAtLeast(0.02f), (0.5f + halfH).coerceAtMost(0.98f))
            )
        }

        fun squareQuad(): DocumentQuad {
            val half = 0.38f
            return DocumentQuad(
                topLeft = PointF(0.5f - half, 0.5f - half),
                topRight = PointF(0.5f + half, 0.5f - half),
                bottomRight = PointF(0.5f + half, 0.5f + half),
                bottomLeft = PointF(0.5f - half, 0.5f + half)
            )
        }
    }
}

enum class DetectionStatus(val label: String) {
    LOOKING("Looking for document"),
    DETECTED("Document detected"),
    STEADY("Hold steady"),
    READY("Ready to scan")
}

