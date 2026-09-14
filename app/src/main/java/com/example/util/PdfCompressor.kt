package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

object PdfCompressor {

    private const val TAG = "PdfCompressor"

    data class CompressResult(
        val success: Boolean,
        val file: File? = null,
        val fileSizeBytes: Long = 0L,
        val savedToDownloadsUri: String? = null,
        val displayPath: String = "",
        val errorMessage: String? = null
    )

    /**
     * Formats bytes into human-readable string (e.g., "450 KB", "1.2 MB").
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }

    /**
     * Compresses [sourcePdf] to be under [targetMaxKb] (e.g., 500 KB, 1024 KB, 2048 KB).
     * If [targetMaxKb] is null or source is already under target, returns the file without degrading quality.
     */
    fun compressPdf(
        context: Context,
        sourcePdf: File,
        targetMaxKb: Int?
    ): File {
        if (!sourcePdf.exists() || sourcePdf.length() <= 0) return sourcePdf
        if (targetMaxKb == null || targetMaxKb <= 0) return sourcePdf

        val targetBytes = targetMaxKb * 1024L
        if (sourcePdf.length() <= targetBytes) {
            // Already within target budget
            return sourcePdf
        }

        val cacheDir = File(context.cacheDir, "compressed_pdf").apply { mkdirs() }
        val outputFile = File(cacheDir, "comp_${System.currentTimeMillis()}.pdf")

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            if (pageCount <= 0) return sourcePdf

            // First pass estimation
            val currentBytes = sourcePdf.length()
            val ratio = targetBytes.toFloat() / currentBytes.toFloat()

            // Linear scale factor for dimensions: sqrt of target/current with 10% safety margin
            var dimScale = (sqrt(ratio) * 0.90f).coerceIn(0.20f, 0.95f)
            var jpegQuality = when {
                ratio < 0.25f -> 50
                ratio < 0.50f -> 65
                ratio < 0.75f -> 78
                else -> 85
            }

            fun buildPdf(scale: Float, quality: Int, destFile: File): Long {
                val pdfDoc = PdfDocument()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

                try {
                    for (i in 0 until pageCount) {
                        val page = renderer.openPage(i)
                        val origW = page.width
                        val origH = page.height

                        // Calculate rendered bitmap dimensions based on scale
                        val targetDpi = (300f * scale).coerceIn(90f, 300f)
                        val renderScale = targetDpi / 72.0f
                        val bmpW = (origW * renderScale).toInt().coerceAtLeast(100)
                        val bmpH = (origH * renderScale).toInt().coerceAtLeast(100)

                        val rawBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        val rawCanvas = Canvas(rawBitmap)
                        rawCanvas.drawColor(Color.WHITE)
                        page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        // Quantize / compress through JPEG to reduce entropy
                        val baos = ByteArrayOutputStream()
                        rawBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                        val compressedBytes = baos.toByteArray()
                        rawBitmap.recycle()

                        val quantizedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                        val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, i + 1).create()
                        val pdfPage = pdfDoc.startPage(pageInfo)
                        val canvas = pdfPage.canvas

                        val destRect = RectF(0f, 0f, origW.toFloat(), origH.toFloat())
                        canvas.drawBitmap(quantizedBitmap, null, destRect, paint)
                        pdfDoc.finishPage(pdfPage)
                        quantizedBitmap.recycle()
                    }

                    FileOutputStream(destFile).use { fos ->
                        pdfDoc.writeTo(fos)
                        fos.flush()
                    }
                } finally {
                    pdfDoc.close()
                }
                return destFile.length()
            }

            var resultLen = buildPdf(dimScale, jpegQuality, outputFile)

            // If still slightly over target, do a fast adaptive second pass
            if (resultLen > targetBytes) {
                val overshoot = resultLen.toFloat() / targetBytes.toFloat()
                dimScale = (dimScale / sqrt(overshoot) * 0.90f).coerceIn(0.15f, 0.90f)
                jpegQuality = (jpegQuality - 15).coerceIn(40, 80)

                val pass2File = File(cacheDir, "comp_pass2_${System.currentTimeMillis()}.pdf")
                val pass2Len = buildPdf(dimScale, jpegQuality, pass2File)
                if (pass2Len <= targetBytes || pass2Len < resultLen) {
                    if (outputFile.exists()) outputFile.delete()
                    return pass2File
                }
            }

            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing PDF", e)
            return sourcePdf
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Compresses [sourcePdf] to [targetMaxKb] and saves it directly into the user's
     * public Downloads folder (`Downloads/Scanova/`).
     */
    fun savePdfToPhone(
        context: Context,
        sourcePdf: File,
        documentTitle: String,
        targetMaxKb: Int?
    ): CompressResult {
        if (!sourcePdf.exists()) {
            return CompressResult(
                success = false,
                errorMessage = "Source PDF file does not exist"
            )
        }

        try {
            // 1. Compress to target size if requested
            val finalPdfFile = compressPdf(context, sourcePdf, targetMaxKb)
            val finalSize = finalPdfFile.length()

            // 2. Generate clean filename
            val cleanTitle = documentTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(35).ifBlank { "Document" }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val sizeLabel = if (targetMaxKb != null && targetMaxKb > 0) {
                if (targetMaxKb >= 1024) "_${targetMaxKb / 1024}MB" else "_${targetMaxKb}KB"
            } else ""
            val fileName = "Scanova_${cleanTitle}${sizeLabel}_$timeStamp.pdf"

            // 3. Keep cached copy for opening / sharing
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val exportFile = File(exportsDir, fileName)
            FileInputStream(finalPdfFile).use { input ->
                FileOutputStream(exportFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 4. Save to Android Public Downloads Directory (Downloads/Scanova)
            var downloadsUriString: String? = null
            var displayPath = "Downloads/Scanova/$fileName"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanova")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    FileInputStream(finalPdfFile).use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                    downloadsUriString = uri.toString()
                }
            } else {
                // Android 9 and below: Direct public storage access
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Scanova"
                ).apply { mkdirs() }
                val targetFile = File(downloadsDir, fileName)
                FileInputStream(finalPdfFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                displayPath = targetFile.absolutePath
            }

            return CompressResult(
                success = true,
                file = exportFile,
                fileSizeBytes = finalSize,
                savedToDownloadsUri = downloadsUriString,
                displayPath = displayPath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF to phone", e)
            return CompressResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Failed to save PDF to phone"
            )
        }
    }
}
