package com.example.scanner.detector

import android.graphics.PointF
import com.example.scanner.model.DetectionStatus
import com.example.scanner.model.DocumentQuad

/**
 * Applies exponential smoothing (low-pass filtering) to detected corners
 * and determines stability for auto-capture and status messaging.
 */
class QuadSmoother(
    private val alpha: Float = 0.35f, // Smoothing factor (higher = faster response, lower = smoother)
    private val stabilityThreshold: Float = 0.025f // Max allowed drift in normalized coords
) {
    private var currentQuad: DocumentQuad? = null
    private var stableFrameCount = 0
    private var consecutiveMisses = 0
    private val maxMissesBeforeReset = 5

    fun process(rawQuad: DocumentQuad?): Pair<DocumentQuad?, DetectionStatus> {
        if (rawQuad == null) {
            consecutiveMisses++
            if (consecutiveMisses >= maxMissesBeforeReset) {
                currentQuad = null
                stableFrameCount = 0
            }
            return Pair(currentQuad, DetectionStatus.LOOKING)
        }

        consecutiveMisses = 0

        val smoothed = if (currentQuad == null) {
            rawQuad
        } else {
            val old = currentQuad!!
            val drift = rawQuad.maxDrift(old)

            if (drift < stabilityThreshold) {
                stableFrameCount++
            } else {
                stableFrameCount = maxOf(0, stableFrameCount - 1)
            }

            DocumentQuad(
                topLeft = smoothPoint(old.topLeft, rawQuad.topLeft, alpha),
                topRight = smoothPoint(old.topRight, rawQuad.topRight, alpha),
                bottomRight = smoothPoint(old.bottomRight, rawQuad.bottomRight, alpha),
                bottomLeft = smoothPoint(old.bottomLeft, rawQuad.bottomLeft, alpha)
            )
        }

        currentQuad = smoothed

        val status = when {
            stableFrameCount >= 10 && smoothed.area() > 0.12f -> DetectionStatus.READY
            stableFrameCount >= 4 -> DetectionStatus.STEADY
            else -> DetectionStatus.DETECTED
        }

        return Pair(smoothed, status)
    }

    fun reset() {
        currentQuad = null
        stableFrameCount = 0
        consecutiveMisses = 0
    }

    private fun smoothPoint(old: PointF, target: PointF, a: Float): PointF {
        val x = old.x + a * (target.x - old.x)
        val y = old.y + a * (target.y - old.y)
        return PointF(x, y)
    }
}
