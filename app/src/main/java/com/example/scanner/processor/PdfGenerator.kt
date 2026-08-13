package com.example.scanner.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.example.scanner.model.ScannedPage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PdfGenerator {

    data class PdfResult(
        val pdfFile: File,
        val thumbnailFile: File,
        val pageCount: Int,
        val fileSizeBytes: Long
    )

    /**
     * Generates a real multi-page PDF document on device from [pages].
     */
    fun generatePdf(
        context: Context,
        rawDocumentTitle: String,
        pages: List<ScannedPage>,
        pageSizeOption: String = "Auto"
    ): PdfResult {
        require(pages.isNotEmpty()) { "Cannot create PDF with empty pages list" }

        val pdfDocument = PdfDocument()

        // 1. Sanitize Document Title
        val sanitizedTitle = sanitizeFilename(rawDocumentTitle)
        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (sanitizedTitle.isBlank()) "Scan_$timestampStr.pdf" else "$sanitizedTitle.pdf"

        // 2. Prepare Output Directories
        val docsDir = File(context.filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()

        val thumbsDir = File(context.filesDir, "thumbnails")
        if (!thumbsDir.exists()) thumbsDir.mkdirs()

        val pdfFile = File(docsDir, fileName)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        // Sort pages by pageOrder
        val sortedPages = pages.sortedBy { it.pageOrder }

        var firstPageThumbnailFile: File? = null

        // 3. Render Each Page onto PDF Canvas
        for ((index, pageItem) in sortedPages.withIndex()) {
            val pageNum = index + 1
            val imgPath = pageItem.enhancedImagePath

            val sourceBitmap = try {
                BitmapFactory.decodeFile(imgPath)
            } catch (e: Exception) {
                Log.e("PdfGenerator", "Failed decoding image for PDF page $pageNum", e)
                null
            } ?: continue

            // Handle optional page rotation
            val bitmap = if (pageItem.rotationDegrees % 360 != 0) {
                rotateBitmap(sourceBitmap, pageItem.rotationDegrees)
            } else {
                sourceBitmap
            }

            // Generate thumbnail for the first page
            if (index == 0) {
                firstPageThumbnailFile = saveThumbnail(thumbsDir, bitmap)
            }

            // Determine PDF Page Dimensions (Points: 1 pt = 1/72 inch)
            val (pageWidthPt, pageHeightPt) = when (pageSizeOption) {
                "A4" -> Pair(595, 842)
                "Letter" -> Pair(612, 792)
                else -> { // "Auto" / "Original"
                    val targetW = 595
                    val targetH = (targetW * (bitmap.height.toFloat() / bitmap.width.toFloat())).toInt()
                        .coerceIn(200, 1200)
                    Pair(targetW, targetH)
                }
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, pageNum).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = pdfPage.canvas

            // Fill white background
            canvas.drawColor(Color.WHITE)

            // Draw bitmap fitted inside page with margin
            val marginPt = 12f
            val availW = pageWidthPt - (marginPt * 2)
            val availH = pageHeightPt - (marginPt * 2)

            val srcW = bitmap.width.toFloat()
            val srcH = bitmap.height.toFloat()

            val scale = minOf(availW / srcW, availH / srcH)
            val destW = srcW * scale
            val destH = srcH * scale

            val destLeft = marginPt + (availW - destW) / 2f
            val destTop = marginPt + (availH - destH) / 2f

            val destRect = RectF(destLeft, destTop, destLeft + destW, destTop + destH)
            canvas.drawBitmap(bitmap, null, destRect, paint)

            pdfDocument.finishPage(pdfPage)

            if (bitmap != sourceBitmap) {
                bitmap.recycle()
            }
            sourceBitmap.recycle()
        }

        // 4. Write PDF Stream
        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        val thumbFile = firstPageThumbnailFile ?: File(thumbsDir, "thumb_${UUID.randomUUID()}.jpg")

        return PdfResult(
            pdfFile = pdfFile,
            thumbnailFile = thumbFile,
            pageCount = sortedPages.size,
            fileSizeBytes = pdfFile.length()
        )
    }

    private fun saveThumbnail(thumbsDir: File, bitmap: Bitmap): File {
        val thumbFile = File(thumbsDir, "thumb_${UUID.randomUUID()}.jpg")
        val maxDim = 320
        val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
        val tw = (bitmap.width * scale).toInt()
        val th = (bitmap.height * scale).toInt()

        val thumbBitmap = Bitmap.createScaledBitmap(bitmap, tw, th, true)
        val fos = FileOutputStream(thumbFile)
        thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        fos.flush()
        fos.close()
        thumbBitmap.recycle()
        return thumbFile
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
            .trim()
            .replace("\\s+".toRegex(), "_")
    }
}
