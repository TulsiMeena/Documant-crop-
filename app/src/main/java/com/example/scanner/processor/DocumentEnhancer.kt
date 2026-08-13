package com.example.scanner.processor

import android.graphics.Bitmap
import android.graphics.Color
import com.example.scanner.model.AdjustParams
import com.example.scanner.model.EnhanceMode
import kotlin.math.max
import kotlin.math.min

object DocumentEnhancer {

    /**
     * Enhances a document bitmap according to the specified [mode] and manual [params].
     */
    fun enhance(
        sourceBitmap: Bitmap,
        mode: EnhanceMode,
        params: AdjustParams
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val pixels = IntArray(width * height)
        sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Apply Base Filter Mode Pipeline
        val filteredPixels = when (mode) {
            EnhanceMode.ORIGINAL -> pixels.clone()
            EnhanceMode.AUTO -> processAuto(pixels, width, height)
            EnhanceMode.COLOR -> processColor(pixels, width, height)
            EnhanceMode.GRAYSCALE -> processGrayscale(pixels, width, height)
            EnhanceMode.BLACK_AND_WHITE -> processAdaptiveBW(pixels, width, height)
        }

        // 2. Apply Manual Adjustment Sliders (Brightness, Contrast, Sharpness)
        val finalPixels = if (!params.isDefault) {
            applyAdjustments(filteredPixels, width, height, params)
        } else {
            filteredPixels
        }

        val resultBitmap = Bitmap.createBitmap(width, height, sourceBitmap.config ?: Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(finalPixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    /**
     * AUTO MODE: Adaptive contrast normalization, shadow correction, background whitening, mild sharpening.
     */
    private fun processAuto(pixels: IntArray, w: Int, h: Int): IntArray {
        val count = pixels.size
        val luma = IntArray(count)

        var minLuma = 255
        var maxLuma = 0
        var sumLuma = 0L

        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = (r * 299 + g * 587 + b * 114) / 1000
            luma[i] = y
            if (y < minLuma) minLuma = y
            if (y > maxLuma) maxLuma = y
            sumLuma += y
        }

        val avgLuma = (sumLuma / count).toInt()
        val range = max(1, maxLuma - minLuma)

        val out = IntArray(count)
        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = luma[i]

            // Contrast stretch & background boost
            val normY = ((y - minLuma).toFloat() / range * 255.0f)
            val boostedY = if (normY > avgLuma * 0.85f) {
                // Brighten paper background toward clean off-white
                min(255.0f, normY * 1.12f + 12.0f)
            } else {
                // Keep text dark and crisp
                max(0.0f, normY * 0.95f)
            }

            val scale = if (y > 0) boostedY / y.toFloat() else 1.0f

            val newR = (r * scale).toInt().coerceIn(0, 255)
            val newG = (g * scale).toInt().coerceIn(0, 255)
            val newB = (b * scale).toInt().coerceIn(0, 255)

            out[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        // Apply mild 3x3 unsharp mask for sharp text
        return applySharpen(out, w, h, 0.35f)
    }

    /**
     * COLOR MODE: Mild contrast boost (+15%), brightness (+8%), crisp text without saturation distortion.
     */
    private fun processColor(pixels: IntArray, w: Int, h: Int): IntArray {
        val count = pixels.size
        val out = IntArray(count)

        val contrastFactor = 1.15f
        val brightnessOffset = 15

        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val newR = (((r - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
            val newG = (((g - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
            val newB = (((b - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)

            out[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        return applySharpen(out, w, h, 0.25f)
    }

    /**
     * GRAYSCALE MODE: Converts to Luma and applies contrast stretch preserving handwriting/diagrams.
     */
    private fun processGrayscale(pixels: IntArray, w: Int, h: Int): IntArray {
        val count = pixels.size
        val out = IntArray(count)

        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val y = (r * 299 + g * 587 + b * 114) / 1000

            // Apply gentle document gamma curve to pop dark ink on light background
            val enhancedY = if (y > 210) {
                min(255, (y * 1.08f).toInt())
            } else if (y < 120) {
                max(0, (y * 0.90f).toInt())
            } else {
                y
            }

            out[i] = (0xFF shl 24) or (enhancedY shl 16) or (enhancedY shl 8) or enhancedY
        }

        return out
    }

    /**
     * BLACK & WHITE MODE: Integral-Image Adaptive Local Thresholding for clean white paper + dark text.
     */
    private fun processAdaptiveBW(pixels: IntArray, w: Int, h: Int): IntArray {
        val count = pixels.size
        val gray = IntArray(count)

        for (i in 0 until count) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        // Build 2D Integral Image for O(1) local window mean calculation
        val integral = LongArray((w + 1) * (h + 1))
        val stride = w + 1

        for (y in 0 until h) {
            var rowSum = 0L
            val iRow = (y + 1) * stride
            val iPrevRow = y * stride
            val gRow = y * w

            for (x in 0 until w) {
                rowSum += gray[gRow + x]
                integral[iRow + x + 1] = integral[iPrevRow + x + 1] + rowSum
            }
        }

        val out = IntArray(count)
        val windowSize = max(15, min(w, h) / 20) // Local window adaptively scaled
        val halfW = windowSize / 2
        val offsetC = 10 // Threshold offset constant to preserve thin ink strokes

        for (y in 0 until h) {
            val y1 = max(0, y - halfW)
            val y2 = min(h - 1, y + halfW)
            val gRow = y * w

            for (x in 0 until w) {
                val x1 = max(0, x - halfW)
                val x2 = min(w - 1, x + halfW)

                val countArea = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                        integral[y1 * stride + (x2 + 1)] -
                        integral[(y2 + 1) * stride + x1] +
                        integral[y1 * stride + x1]

                val localMean = (sum / countArea).toInt()
                val pixelVal = gray[gRow + x]

                // Threshold test
                val bwVal = if (pixelVal < localMean - offsetC) 0 else 255
                val colorVal = (0xFF shl 24) or (bwVal shl 16) or (bwVal shl 8) or bwVal
                out[gRow + x] = colorVal
            }
        }

        return out
    }

    /**
     * Applies manual Brightness, Contrast, and Sharpness adjustments.
     */
    private fun applyAdjustments(
        pixels: IntArray,
        w: Int,
        h: Int,
        params: AdjustParams
    ): IntArray {
        var current = pixels

        // 1. Brightness & Contrast
        if (params.brightness != 0.0f || params.contrast != 0.0f) {
            val count = current.size
            val out = IntArray(count)

            val brightnessOffset = (params.brightness * 128.0f).toInt()
            val contrastFactor = (1.0f + params.contrast) * (1.0f + params.contrast)

            for (i in 0 until count) {
                val p = current[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val newR = (((r - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
                val newG = (((g - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
                val newB = (((b - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)

                out[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
            current = out
        }

        // 2. Sharpness
        if (params.sharpness > 0.0f) {
            current = applySharpen(current, w, h, params.sharpness)
        }

        return current
    }

    /**
     * High-pass unsharp filter for sharpening document text edges.
     */
    private fun applySharpen(pixels: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val out = IntArray(pixels.size)

        for (y in 0 until h) {
            val rCurr = y * w
            val rAbove = if (y > 0) (y - 1) * w else rCurr
            val rBelow = if (y < h - 1) (y + 1) * w else rCurr

            for (x in 0 until w) {
                val xLeft = if (x > 0) x - 1 else x
                val xRight = if (x < w - 1) x + 1 else x

                val p = pixels[rCurr + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // Neighbor pixels
                val pUp = pixels[rAbove + x]
                val pDown = pixels[rBelow + x]
                val pLeft = pixels[rCurr + xLeft]
                val pRight = pixels[rCurr + xRight]

                val nR = (((pUp shr 16) and 0xFF) + ((pDown shr 16) and 0xFF) + ((pLeft shr 16) and 0xFF) + ((pRight shr 16) and 0xFF)) / 4
                val nG = (((pUp shr 8) and 0xFF) + ((pDown shr 8) and 0xFF) + ((pLeft shr 8) and 0xFF) + ((pRight shr 8) and 0xFF)) / 4
                val nB = ((pUp and 0xFF) + (pDown and 0xFF) + (pLeft and 0xFF) + (pRight and 0xFF)) / 4

                val sharpR = (r + amount * (r - nR)).toInt().coerceIn(0, 255)
                val sharpG = (g + amount * (g - nG)).toInt().coerceIn(0, 255)
                val sharpB = (b + amount * (b - nB)).toInt().coerceIn(0, 255)

                out[rCurr + x] = (0xFF shl 24) or (sharpR shl 16) or (sharpG shl 8) or sharpB
            }
        }

        return out
    }
}
