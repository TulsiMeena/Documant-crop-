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
import com.example.util.PdfCompressor
import java.io.File
import java.io.FileInputStream
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
        pageSizeOption: String = "Auto",
        targetSizeKb: Int? = null
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
            val isStandardPaper = pageSizeOption == "A4" || pageSizeOption == "Letter"
            val (pageWidthPt, pageHeightPt) = when (pageSizeOption) {
                "A4" -> Pair(595, 842)
                "Letter" -> Pair(612, 792)
                else -> { // "Auto" / "Original" - Fit page exactly to document aspect ratio (zero extra margins)
                    val baseW = 595
                    val calcH = (baseW * (bitmap.height.toFloat() / bitmap.width.toFloat())).toInt()
                    Pair(baseW, calcH.coerceIn(100, 3000))
                }
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, pageNum).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = pdfPage.canvas

            if (!isStandardPaper) {
                // Borderless edge-to-edge drawing for Auto/Original scan
                val destRect = RectF(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat())
                canvas.drawBitmap(bitmap, null, destRect, paint)
            } else {
                // For standard paper sizes, fit cleanly without artificial margins
                canvas.drawColor(Color.WHITE)
                val srcW = bitmap.width.toFloat()
                val srcH = bitmap.height.toFloat()
                val scale = minOf(pageWidthPt.toFloat() / srcW, pageHeightPt.toFloat() / srcH)
                val destW = srcW * scale
                val destH = srcH * scale
                val destLeft = (pageWidthPt - destW) / 2f
                val destTop = (pageHeightPt - destH) / 2f
                val destRect = RectF(destLeft, destTop, destLeft + destW, destTop + destH)
                canvas.drawBitmap(bitmap, null, destRect, paint)
            }

            pdfDocument.finishPage(pdfPage)
        }

        // 4. Write PDF Stream
        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        // 5. If user requested a custom target size limit (e.g. 500 KB, 1 MB, 2 MB), compress to meet it
        val finalPdfFile = if (targetSizeKb != null && targetSizeKb > 0 && pdfFile.length() > targetSizeKb * 1024L) {
            val compressed = PdfCompressor.compressPdf(context, pdfFile, targetSizeKb)
            if (compressed != pdfFile && compressed.exists()) {
                // Copy compressed content over pdfFile
                FileInputStream(compressed).use { inStream ->
                    FileOutputStream(pdfFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }
            pdfFile
        } else {
            pdfFile
        }

        val thumbFile = firstPageThumbnailFile ?: File(thumbsDir, "thumb_${UUID.randomUUID()}.jpg")

        return PdfResult(
            pdfFile = finalPdfFile,
            thumbnailFile = thumbFile,
            pageCount = sortedPages.size,
            fileSizeBytes = finalPdfFile.length()
        )
    }

    private fun saveThumbnail(thumbsDir: File, bitmap: Bitmap): File {
        val thumbFile = File(thumbsDir, "thumb_${UUID.randomUUID()}.jpg")
        val maxDim = 4096
        val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
        val tw = (bitmap.width * scale).toInt().coerceAtLeast(100)
        val th = (bitmap.height * scale).toInt().coerceAtLeast(100)

        val thumbBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, tw, th, true)
        } else {
            bitmap
        }
        val fos = FileOutputStream(thumbFile)
        thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        fos.flush()
        fos.close()
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
