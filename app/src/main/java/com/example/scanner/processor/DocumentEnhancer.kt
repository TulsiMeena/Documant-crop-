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
     * Always preserves full resolution, high dynamic range, and razor-sharp text clarity.
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
     * AUTO MODE: Precision S-curve contrast normalization, background brightening,
     * crisp dark ink definition, preserving color stamps/seals, and text edge sharpening.
     */
    private fun processAuto(pixels: IntArray, w: Int, h: Int): IntArray {
        val count = pixels.size
        val out = IntArray(count)

        // Look-Up Table (LUT) for smooth, high-fidelity tone curve
        val lut = IntArray(256)
        for (i in 0..255) {
            val v = if (i > 185) {
                // Bright paper background -> clean bright white
                min(255, (i + (255 - i) * 0.45f).toInt())
            } else if (i < 100) {
                // Dark ink / text -> rich crisp dark
                max(0, (i * 0.85f).toInt())
            } else {
                // Midtones -> natural contrast boost
                val norm = (i - 100) / 85.0f
                (85 + norm * 100).toInt().coerceIn(0, 255)
            }
            lut[i] = v
        }

        for (i in 0 until count) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val newR = lut[r]
            val newG = lut[g]
            val newB = lut[b]

            out[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        // Apply high-clarity edge sharpening for sharp text
        return applySharpen(out, w, h, 0.40f)
    }

    /**
     * COLOR MODE: Vibrant colors, punchy contrast (+20%), preserving photo and stamp fidelity.
     */
    private fun processColor(pixels: IntArray, w: Int, h: Int): IntArray {
        val count = pixels.size
        val out = IntArray(count)

        val contrastFactor = 1.18f
        val brightnessOffset = 10

        for (i in 0 until count) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val newR = (((r - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
            val newG = (((g - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
            val newB = (((b - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)

            out[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        return applySharpen(out, w, h, 0.30f)
    }

    /**
     * GRAYSCALE MODE: Converts to high-contrast Luma, removing paper discoloration while keeping all ink crisp.
     */
    private fun processGrayscale(pixels: IntArray, w: Int, h: Int): IntArray {
        val count = pixels.size
        val out = IntArray(count)

        val lut = IntArray(256)
        for (i in 0..255) {
            val v = if (i > 180) {
                min(255, (i + (255 - i) * 0.50f).toInt())
            } else if (i < 110) {
                max(0, (i * 0.80f).toInt())
            } else {
                i
            }
            lut[i] = v
        }

        for (i in 0 until count) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val luma = (r * 299 + g * 587 + b * 114) / 1000
            val enhancedY = lut[luma.coerceIn(0, 255)]

            out[i] = (a shl 24) or (enhancedY shl 16) or (enhancedY shl 8) or enhancedY
        }

        return applySharpen(out, w, h, 0.35f)
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

        // Build 2D Integral Image for fast O(1) local window mean calculation
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
        val windowSize = max(15, min(w, h) / 25)
        val halfW = windowSize / 2
        val offsetC = 8

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
                val a = (p ushr 24) and 0xFF
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val newR = (((r - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
                val newG = (((g - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)
                val newB = (((b - 128) * contrastFactor) + 128 + brightnessOffset).toInt().coerceIn(0, 255)

                out[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
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
        if (amount <= 0f) return pixels
        val out = IntArray(pixels.size)

        for (y in 0 until h) {
            val rCurr = y * w
            val rAbove = if (y > 0) (y - 1) * w else rCurr
            val rBelow = if (y < h - 1) (y + 1) * w else rCurr

            for (x in 0 until w) {
                val xLeft = if (x > 0) x - 1 else x
                val xRight = if (x < w - 1) x + 1 else x

                val p = pixels[rCurr + x]
                val a = (p ushr 24) and 0xFF
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

                out[rCurr + x] = (a shl 24) or (sharpR shl 16) or (sharpG shl 8) or sharpB
            }
        }

        return out
    }
}
