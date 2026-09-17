package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import java.io.File
import java.io.FileOutputStream

data class EditablePdfPage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalPageIndex: Int = -1, // -1 if newly imported image
    var rotationDegrees: Int = 0,    // 0, 90, 180, 270
    val thumbnailBitmap: Bitmap,
    val sourceImagePath: String? = null // if added from gallery
)

object PdfEditUtil {

    private const val TAG = "PdfEditUtil"

    /**
     * Loads page thumbnails from [pdfFile] for interactive editing.
     */
    fun loadPagesFromPdf(context: Context, pdfFile: File): List<EditablePdfPage> {
        val result = mutableListOf<EditablePdfPage>()
        if (!pdfFile.exists()) return result

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val w = page.width
                val h = page.height

                // Render thumbnail for smooth UI display
                val thumbScale = 300f / maxOf(w, h).toFloat()
                val thumbW = (w * thumbScale).toInt().coerceAtLeast(100)
                val thumbH = (h * thumbScale).toInt().coerceAtLeast(100)

                val thumbBitmap = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(thumbBitmap)
                canvas.drawColor(Color.WHITE)
                page.render(thumbBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                result.add(
                    EditablePdfPage(
                        originalPageIndex = i,
                        rotationDegrees = 0,
                        thumbnailBitmap = thumbBitmap
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pages from PDF", e)
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) {}
        }
        return result
    }

    /**
     * Rebuilds the PDF from the modified list of [pages] (respecting new order, rotations, and deletions).
     */
    fun rebuildPdf(
        context: Context,
        sourcePdf: File,
        pages: List<EditablePdfPage>,
        outputFile: File
    ): Boolean {
        if (pages.isEmpty()) return false

        val pdfDoc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            if (sourcePdf.exists()) {
                pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
            }

            for ((pageIdx, pageItem) in pages.withIndex()) {
                val origBitmap: Bitmap = if (pageItem.originalPageIndex >= 0 && renderer != null) {
                    val page = renderer.openPage(pageItem.originalPageIndex)
                    val pw = page.width
                    val ph = page.height

                    // Render at high print DPI (~200 dpi)
                    val renderScale = 200f / 72.0f
                    val rw = (pw * renderScale).toInt().coerceAtLeast(200)
                    val rh = (ph * renderScale).toInt().coerceAtLeast(200)

                    val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(bmp)
                    cv.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()
                    bmp
                } else if (pageItem.sourceImagePath != null) {
                    android.graphics.BitmapFactory.decodeFile(pageItem.sourceImagePath)
                        ?: pageItem.thumbnailBitmap
                } else {
                    pageItem.thumbnailBitmap
                }

                // Apply rotation if needed
                val finalBitmap = if (pageItem.rotationDegrees % 360 != 0) {
                    val matrix = Matrix().apply { postRotate(pageItem.rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(origBitmap, 0, 0, origBitmap.width, origBitmap.height, matrix, true)
                    if (rotated != origBitmap) origBitmap.recycle()
                    rotated
                } else {
                    origBitmap
                }

                val pageWidth = finalBitmap.width
                val pageHeight = finalBitmap.height

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                canvas.drawBitmap(finalBitmap, null, RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat()), paint)
                pdfDoc.finishPage(pdfPage)
                finalBitmap.recycle()
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDoc.writeTo(fos)
                fos.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error rebuilding edited PDF", e)
            return false
        } finally {
            try {
                pdfDoc.close()
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Splits [sourcePdf] by extracting only [selectedPageIndices] (0-indexed) into [outputFile].
     */
    fun splitPdf(
        context: Context,
        sourcePdf: File,
        selectedPageIndices: List<Int>,
        outputFile: File
    ): Boolean {
        if (!sourcePdf.exists() || selectedPageIndices.isEmpty()) return false

        val pdfDoc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val maxPages = renderer.pageCount

            for ((outPageNum, pageIdx) in selectedPageIndices.withIndex()) {
                if (pageIdx < 0 || pageIdx >= maxPages) continue

                val page = renderer.openPage(pageIdx)
                val pw = page.width
                val ph = page.height

                val renderScale = 200f / 72f
                val rw = (pw * renderScale).toInt().coerceAtLeast(200)
                val rh = (ph * renderScale).toInt().coerceAtLeast(200)

                val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                val cv = Canvas(bmp)
                cv.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, outPageNum + 1).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                canvas.drawBitmap(bmp, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), paint)
                pdfDoc.finishPage(pdfPage)
                bmp.recycle()
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDoc.writeTo(fos)
                fos.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error splitting PDF", e)
            return false
        } finally {
            try {
                pdfDoc.close()
                renderer?.close()
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Merges multiple [sourcePdfs] into a single unified [outputFile].
     */
    fun mergePdfs(
        context: Context,
        sourcePdfs: List<File>,
        outputFile: File
    ): Boolean {
        val validFiles = sourcePdfs.filter { it.exists() && it.length() > 0 }
        if (validFiles.isEmpty()) return false

        // Fast path with PDFBox merger
        return try {
            PdfSecurityUtil.initPdfBox(context)
            val merger = PDFMergerUtility()
            for (f in validFiles) {
                merger.addSource(f)
            }
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            true
        } catch (e: Exception) {
            Log.w(TAG, "PDFBox merge failed, attempting native PdfRenderer fallback", e)
            fallbackNativeMerge(validFiles, outputFile)
        }
    }

    private fun fallbackNativeMerge(sourcePdfs: List<File>, outputFile: File): Boolean {
        val pdfDoc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        var totalPagesWritten = 0

        try {
            for (file in sourcePdfs) {
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val pw = page.width
                        val ph = page.height

                        val renderScale = 200f / 72f
                        val bmp = Bitmap.createBitmap((pw * renderScale).toInt(), (ph * renderScale).toInt(), Bitmap.Config.ARGB_8888)
                        val cv = Canvas(bmp)
                        cv.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        totalPagesWritten++
                        val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, totalPagesWritten).create()
                        val pdfPage = pdfDoc.startPage(pageInfo)
                        pdfPage.canvas.drawBitmap(bmp, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), paint)
                        pdfDoc.finishPage(pdfPage)
                        bmp.recycle()
                    }
                } finally {
                    renderer?.close()
                    pfd?.close()
                }
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDoc.writeTo(fos)
                fos.flush()
            }
            return totalPagesWritten > 0
        } catch (e: Exception) {
            Log.e(TAG, "Fallback merge failed", e)
            return false
        } finally {
            pdfDoc.close()
        }
    }
}
