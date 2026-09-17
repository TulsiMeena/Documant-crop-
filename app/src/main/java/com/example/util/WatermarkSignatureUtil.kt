package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream

enum class WatermarkPosition {
    CENTER_DIAGONAL,
    CENTER_HORIZONTAL,
    TOP_BANNER,
    BOTTOM_BANNER
}

enum class SignaturePlacement {
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    CENTER
}

data class WatermarkOptions(
    val text: String = "CONFIDENTIAL",
    val position: WatermarkPosition = WatermarkPosition.CENTER_DIAGONAL,
    val opacity: Float = 0.25f, // 0.1 to 0.8
    val color: Int = Color.GRAY,
    val textSizeSp: Float = 36f
)

data class SignatureOptions(
    val signatureBitmap: Bitmap,
    val placement: SignaturePlacement = SignaturePlacement.BOTTOM_RIGHT,
    val applyToAllPages: Boolean = true,
    val targetPageIndex: Int = 0 // if not all pages
)

object WatermarkSignatureUtil {

    private const val TAG = "WatermarkSignatureUtil"

    /**
     * Applies watermark and/or signature to an existing PDF and saves to [outputPdf].
     */
    fun applyWatermarkAndSignature(
        context: Context,
        sourcePdf: File,
        outputPdf: File,
        watermark: WatermarkOptions? = null,
        signature: SignatureOptions? = null
    ): Boolean {
        if (!sourcePdf.exists()) return false
        if (watermark == null && signature == null) return false

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val newPdf = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            for (i in 0 until pageCount) {
                val origPage = renderer.openPage(i)
                val pw = origPage.width
                val ph = origPage.height

                // Render at high print DPI (~200 dpi)
                val scale = 200f / 72f
                val rw = (pw * scale).toInt().coerceAtLeast(300)
                val rh = (ph * scale).toInt().coerceAtLeast(300)

                val pageBitmap = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(pageBitmap)
                canvas.drawColor(Color.WHITE)
                origPage.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                origPage.close()

                // 1. Draw Watermark if present
                if (watermark != null && watermark.text.isNotBlank()) {
                    drawWatermarkOnCanvas(canvas, rw.toFloat(), rh.toFloat(), watermark)
                }

                // 2. Draw Signature if present
                if (signature != null) {
                    val shouldApply = signature.applyToAllPages || (i == signature.targetPageIndex) || (signature.targetPageIndex == -1 && i == pageCount - 1)
                    if (shouldApply) {
                        drawSignatureOnCanvas(canvas, rw.toFloat(), rh.toFloat(), signature)
                    }
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, i + 1).create()
                val pdfPage = newPdf.startPage(pageInfo)
                val pageCanvas = pdfPage.canvas

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                pageCanvas.drawBitmap(pageBitmap, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), paint)
                newPdf.finishPage(pdfPage)
                pageBitmap.recycle()
            }

            FileOutputStream(outputPdf).use { fos ->
                newPdf.writeTo(fos)
                fos.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying watermark/signature to PDF", e)
            return false
        } finally {
            try {
                newPdf.close()
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun drawWatermarkOnCanvas(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        options: WatermarkOptions
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.color
            alpha = (options.opacity.coerceIn(0.05f, 0.9f) * 255).toInt()
            textSize = (options.textSizeSp * (pageWidth / 600f)).coerceAtLeast(32f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        when (options.position) {
            WatermarkPosition.CENTER_DIAGONAL -> {
                canvas.save()
                canvas.translate(pageWidth / 2f, pageHeight / 2f)
                canvas.rotate(-35f)
                canvas.drawText(options.text, 0f, paint.textSize / 3f, paint)
                canvas.restore()
            }
            WatermarkPosition.CENTER_HORIZONTAL -> {
                canvas.save()
                canvas.translate(pageWidth / 2f, pageHeight / 2f)
                canvas.drawText(options.text, 0f, paint.textSize / 3f, paint)
                canvas.restore()
            }
            WatermarkPosition.TOP_BANNER -> {
                val bannerPaint = Paint().apply {
                    color = Color.LTGRAY
                    alpha = (options.opacity * 100).toInt().coerceAtLeast(20)
                }
                val bannerH = paint.textSize * 1.8f
                canvas.drawRect(0f, 40f, pageWidth, 40f + bannerH, bannerPaint)
                canvas.drawText(options.text, pageWidth / 2f, 40f + (bannerH / 2f) + (paint.textSize / 3f), paint)
            }
            WatermarkPosition.BOTTOM_BANNER -> {
                val bannerPaint = Paint().apply {
                    color = Color.LTGRAY
                    alpha = (options.opacity * 100).toInt().coerceAtLeast(20)
                }
                val bannerH = paint.textSize * 1.8f
                val top = pageHeight - 80f - bannerH
                canvas.drawRect(0f, top, pageWidth, top + bannerH, bannerPaint)
                canvas.drawText(options.text, pageWidth / 2f, top + (bannerH / 2f) + (paint.textSize / 3f), paint)
            }
        }
    }

    private fun drawSignatureOnCanvas(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        options: SignatureOptions
    ) {
        val sig = options.signatureBitmap
        val maxSigW = pageWidth * 0.35f
        val scale = maxSigW / sig.width.toFloat()
        val sigW = maxSigW
        val sigH = sig.height * scale

        val margin = pageWidth * 0.05f

        val (left, top) = when (options.placement) {
            SignaturePlacement.BOTTOM_RIGHT -> Pair(pageWidth - sigW - margin, pageHeight - sigH - margin * 1.5f)
            SignaturePlacement.BOTTOM_LEFT -> Pair(margin, pageHeight - sigH - margin * 1.5f)
            SignaturePlacement.BOTTOM_CENTER -> Pair((pageWidth - sigW) / 2f, pageHeight - sigH - margin * 1.5f)
            SignaturePlacement.CENTER -> Pair((pageWidth - sigW) / 2f, (pageHeight - sigH) / 2f)
        }

        val destRect = RectF(left, top, left + sigW, top + sigH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sig, null, destRect, paint)
    }
}
