package com.example.scanner.processor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.example.scanner.model.DocumentQuad
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object PerspectiveTransformer {

    /**
     * Warps a quadrilateral region of [sourceBitmap] defined by normalized [quad]
     * into a straight, perspective-corrected rectangular [Bitmap].
     */
    fun transform(
        sourceBitmap: Bitmap,
        quad: DocumentQuad,
        rotationDegrees: Int = 0
    ): Bitmap {
        val srcW = sourceBitmap.width.toFloat()
        val srcH = sourceBitmap.height.toFloat()

        // 1. Convert normalized quad coordinates to pixel coordinates
        val tlX = quad.topLeft.x * srcW
        val tlY = quad.topLeft.y * srcH

        val trX = quad.topRight.x * srcW
        val trY = quad.topRight.y * srcH

        val brX = quad.bottomRight.x * srcW
        val brY = quad.bottomRight.y * srcH

        val blX = quad.bottomLeft.x * srcW
        val blY = quad.bottomLeft.y * srcH

        // 2. Calculate output rectangle dimensions based on average edge lengths
        val topWidth = hypot((trX - tlX).toDouble(), (trY - tlY).toDouble()).toFloat()
        val bottomWidth = hypot((brX - blX).toDouble(), (brY - blY).toDouble()).toFloat()
        val rawWidth = max(topWidth, bottomWidth)

        val leftHeight = hypot((blX - tlX).toDouble(), (blY - tlY).toDouble()).toFloat()
        val rightHeight = hypot((brX - trX).toDouble(), (brY - trY).toDouble()).toFloat()
        val rawHeight = max(leftHeight, rightHeight)

        // Clamp target dimensions to safe bounds (min 200px, max 3200px)
        val maxTargetDim = 3200f
        val scale = if (max(rawWidth, rawHeight) > maxTargetDim) {
            maxTargetDim / max(rawWidth, rawHeight)
        } else {
            1.0f
        }

        val targetW = max(200, (rawWidth * scale).toInt())
        val targetH = max(200, (rawHeight * scale).toInt())

        // 3. Construct Perspective Transformation Matrix
        val srcPoints = floatArrayOf(
            tlX, tlY,
            trX, trY,
            brX, brY,
            blX, blY
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetW.toFloat(), 0f,
            targetW.toFloat(), targetH.toFloat(),
            0f, targetH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        // 4. Render Warped Perspective onto target Bitmap Canvas using Shader for pristine accuracy
        val outputBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        val invMatrix = Matrix()
        if (matrix.invert(invMatrix)) {
            val shader = BitmapShader(sourceBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(invMatrix)
            paint.shader = shader
            canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), paint)
        } else {
            canvas.drawBitmap(sourceBitmap, matrix, paint)
        }

        // 5. Apply Rotation if specified
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
