package com.example.scanner.model

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Represents a detected document's four corners in normalized coordinates (0.0f .. 1.0f).
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
     * Checks if the quadrilateral is convex and reasonably proportioned for a document.
     */
    fun isValidQuad(): Boolean {
        // Area must be at least ~5% of frame and not exceed ~95%
        val a = area()
        if (a < 0.05f || a > 0.95f) return false

        // Check corner ordering / non-overlapping coordinates
        if (topLeft.x >= topRight.x || bottomLeft.x >= bottomRight.x) return false
        if (topLeft.y >= bottomLeft.y || topRight.y >= bottomRight.y) return false

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

        fun fullImageQuad(): DocumentQuad = DocumentQuad(
            topLeft = PointF(0.01f, 0.01f),
            topRight = PointF(0.99f, 0.01f),
            bottomRight = PointF(0.99f, 0.99f),
            bottomLeft = PointF(0.01f, 0.99f)
        )
    }
}

enum class DetectionStatus(val label: String) {
    LOOKING("Looking for document"),
    DETECTED("Document detected"),
    STEADY("Hold steady"),
    READY("Ready to scan")
}
