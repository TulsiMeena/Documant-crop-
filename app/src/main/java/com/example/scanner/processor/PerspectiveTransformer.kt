package com.example.scanner.processor

import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.scanner.model.DocumentQuad
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object PerspectiveTransformer {

    /**
     * Warps a quadrilateral region of [sourceBitmap] defined by normalized [quad]
     * into a perfectly straightened, high-fidelity rectangular [Bitmap] using
     * exact inverse homography bilinear interpolation (zero streaks, zero artifacts).
     */
    fun transform(
        sourceBitmap: Bitmap,
        quad: DocumentQuad,
        rotationDegrees: Int = 0
    ): Bitmap {
        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        if (srcW <= 0 || srcH <= 0) return sourceBitmap

        // 1. Convert normalized quad coordinates to source pixel coordinates
        val tlX = (quad.topLeft.x * srcW).coerceIn(0f, srcW.toFloat())
        val tlY = (quad.topLeft.y * srcH).coerceIn(0f, srcH.toFloat())

        val trX = (quad.topRight.x * srcW).coerceIn(0f, srcW.toFloat())
        val trY = (quad.topRight.y * srcH).coerceIn(0f, srcH.toFloat())

        val brX = (quad.bottomRight.x * srcW).coerceIn(0f, srcW.toFloat())
        val brY = (quad.bottomRight.y * srcH).coerceIn(0f, srcH.toFloat())

        val blX = (quad.bottomLeft.x * srcW).coerceIn(0f, srcW.toFloat())
        val blY = (quad.bottomLeft.y * srcH).coerceIn(0f, srcH.toFloat())

        // 2. Calculate output rectangle dimensions based on average edge lengths
        val topWidth = hypot((trX - tlX).toDouble(), (trY - tlY).toDouble()).toFloat()
        val bottomWidth = hypot((brX - blX).toDouble(), (brY - blY).toDouble()).toFloat()
        val rawWidth = max(topWidth, bottomWidth)

        val leftHeight = hypot((blX - tlX).toDouble(), (blY - tlY).toDouble()).toFloat()
        val rightHeight = hypot((brX - trX).toDouble(), (brY - trY).toDouble()).toFloat()
        val rawHeight = max(leftHeight, rightHeight)

        val maxDim = 3200f
        val scale = if (max(rawWidth, rawHeight) > maxDim) {
            maxDim / max(rawWidth, rawHeight)
        } else {
            1.0f
        }

        val targetW = max(100, (rawWidth * scale).toInt())
        val targetH = max(100, (rawHeight * scale).toInt())

        // 3. Construct Inverse Homography Matrix: maps Destination (0..targetW, 0..targetH) -> Source (quad)
        val dstPoints = floatArrayOf(
            0f, 0f,                               // TL
            targetW.toFloat(), 0f,                // TR
            targetW.toFloat(), targetH.toFloat(), // BR
            0f, targetH.toFloat()                 // BL
        )

        val srcPoints = floatArrayOf(
            tlX, tlY,
            trX, trY,
            brX, brY,
            blX, blY
        )

        val matrix = Matrix()
        val matrixCalculated = matrix.setPolyToPoly(dstPoints, 0, srcPoints, 0, 4)

        val outputBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)

        if (matrixCalculated) {
            // High-precision Bilinear Inverse Homography Warper
            val srcPixels = IntArray(srcW * srcH)
            sourceBitmap.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
            val dstPixels = IntArray(targetW * targetH)

            val m = FloatArray(9)
            matrix.getValues(m)
            val m00 = m[Matrix.MSCALE_X]
            val m01 = m[Matrix.MSKEW_X]
            val m02 = m[Matrix.MTRANS_X]
            val m10 = m[Matrix.MSKEW_Y]
            val m11 = m[Matrix.MSCALE_Y]
            val m12 = m[Matrix.MTRANS_Y]
            val m20 = m[Matrix.MPERSP_0]
            val m21 = m[Matrix.MPERSP_1]
            val m22 = m[Matrix.MPERSP_2]

            val maxSrcX = (srcW - 1).toFloat()
            val maxSrcY = (srcH - 1).toFloat()

            for (v in 0 until targetH) {
                val rowOffset = v * targetW
                val hx = m01 * v + m02
                val hy = m11 * v + m12
                val hw = m21 * v + m22

                for (u in 0 until targetW) {
                    val currW = m20 * u + hw
                    val invW = if (currW != 0f) 1.0f / currW else 1.0f
                    val srcX = ((m00 * u + hx) * invW).coerceIn(0f, maxSrcX)
                    val srcY = ((m10 * u + hy) * invW).coerceIn(0f, maxSrcY)

                    val x0 = srcX.toInt()
                    val y0 = srcY.toInt()
                    val x1 = min(x0 + 1, srcW - 1)
                    val y1 = min(y0 + 1, srcH - 1)

                    val fx = srcX - x0
                    val fy = srcY - y0
                    val w00 = (1f - fx) * (1f - fy)
                    val w10 = fx * (1f - fy)
                    val w01 = (1f - fx) * fy
                    val w11 = fx * fy

                    val p00 = srcPixels[y0 * srcW + x0]
                    val p10 = srcPixels[y0 * srcW + x1]
                    val p01 = srcPixels[y1 * srcW + x0]
                    val p11 = srcPixels[y1 * srcW + x1]

                    val a = ((p00 ushr 24) and 0xFF) * w00 + ((p10 ushr 24) and 0xFF) * w10 + ((p01 ushr 24) and 0xFF) * w01 + ((p11 ushr 24) and 0xFF) * w11
                    val r = ((p00 shr 16) and 0xFF) * w00 + ((p10 shr 16) and 0xFF) * w10 + ((p01 shr 16) and 0xFF) * w01 + ((p11 shr 16) and 0xFF) * w11
                    val g = ((p00 shr 8) and 0xFF) * w00 + ((p10 shr 8) and 0xFF) * w10 + ((p01 shr 8) and 0xFF) * w01 + ((p11 shr 8) and 0xFF) * w11
                    val b = (p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11

                    dstPixels[rowOffset + u] = ((a.toInt() and 0xFF) shl 24) or
                            ((r.toInt() and 0xFF) shl 16) or
                            ((g.toInt() and 0xFF) shl 8) or
                            (b.toInt() and 0xFF)
                }
            }

            outputBitmap.setPixels(dstPixels, 0, targetW, 0, 0, targetW, targetH)
        } else {
            // Fallback direct crop
            val minX = minOf(tlX, trX, brX, blX).toInt().coerceIn(0, srcW - 1)
            val minY = minOf(tlY, trY, brY, blY).toInt().coerceIn(0, srcH - 1)
            val maxX = maxOf(tlX, trX, brX, blX).toInt().coerceIn(minX + 1, srcW)
            val maxY = maxOf(tlY, trY, brY, blY).toInt().coerceIn(minY + 1, srcH)
            val cropW = max(1, maxX - minX)
            val cropH = max(1, maxY - minY)
            val cropped = Bitmap.createBitmap(sourceBitmap, minX, minY, cropW, cropH)
            return if (rotationDegrees % 360 != 0) rotateBitmap(cropped, rotationDegrees) else cropped
        }

        // 4. Apply Rotation if specified
        return if (rotationDegrees % 360 != 0) {
            rotateBitmap(outputBitmap, rotationDegrees)
        } else {
            outputBitmap
        }
    }

    /**
     * Rotates a bitmap by a multiple of 90 degrees.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
