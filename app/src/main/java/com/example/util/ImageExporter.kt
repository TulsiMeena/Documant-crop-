package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageExporter {

    enum class ImageFormat(val extension: String, val mimeType: String) {
        JPG("jpg", "image/jpeg"),
        PNG("png", "image/png")
    }

    enum class DimensionUnit(val label: String) {
        ORIGINAL("Original Size"),
        PIXELS("Pixels (px)"),
        INCHES("Inches (in)")
    }

    data class ExportOptions(
        val format: ImageFormat = ImageFormat.JPG,
        val dimensionUnit: DimensionUnit = DimensionUnit.ORIGINAL,
        val customWidthPx: Int? = null,
        val customHeightPx: Int? = null,
        val customWidthInches: Float? = null,
        val customHeightInches: Float? = null,
        val dpi: Int = 300,
        val targetSizeKb: Int? = null, // null for ultra 100% crystal high quality, or e.g. 50, 100, 500 KB
        val saveToGallery: Boolean = true
    )

    data class ExportResult(
        val file: File,
        val widthPx: Int,
        val heightPx: Int,
        val fileSizeBytes: Long,
        val savedToGalleryUri: String? = null
    )

    /**
     * Ultra High-Quality image export engine with high-fidelity resampling
     * and lossless/maximum-quality compression directly into phone Gallery.
     */
    fun exportAndSave(
        context: Context,
        sourceImagePath: String,
        documentTitle: String,
        options: ExportOptions
    ): ExportResult? {
        val srcFile = File(sourceImagePath)
        if (!srcFile.exists()) return null

        try {
            val decodeOpts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inDither = false
                inScaled = false
            }
            var bitmap = BitmapFactory.decodeFile(sourceImagePath, decodeOpts) ?: return null

            val originalW = bitmap.width
            val originalH = bitmap.height

            // 1. Calculate Target Dimensions
            var targetW = originalW
            var targetH = originalH

            when (options.dimensionUnit) {
                DimensionUnit.ORIGINAL -> {
                    targetW = originalW
                    targetH = originalH
                }
                DimensionUnit.PIXELS -> {
                    val reqW = options.customWidthPx ?: originalW
                    val reqH = options.customHeightPx ?: originalH
                    targetW = reqW.coerceAtLeast(50)
                    targetH = reqH.coerceAtLeast(50)
                }
                DimensionUnit.INCHES -> {
                    val wInches = options.customWidthInches ?: (originalW.toFloat() / options.dpi)
                    val hInches = options.customHeightInches ?: (originalH.toFloat() / options.dpi)
                    targetW = (wInches * options.dpi).toInt().coerceAtLeast(50)
                    targetH = (hInches * options.dpi).toInt().coerceAtLeast(50)
                }
            }

            // High-fidelity scaling with filtering
            if (targetW != originalW || targetH != originalH) {
                val scaled = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(scaled)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                val srcRect = Rect(0, 0, originalW, originalH)
                val dstRect = Rect(0, 0, targetW, targetH)
                canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                bitmap = scaled
            }

            // 2. Compress with Ultra-High Quality (100% / Lossless PNG when targetKb is null)
            val byteData = compressToTargetKb(bitmap, options.format, options.targetSizeKb)

            // 3. Save to App Cache
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cleanTitle = documentTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(30)
            val fileName = "Scanova_${cleanTitle}_$timeStamp.${options.format.extension}"

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outputFile = File(exportDir, fileName)

            FileOutputStream(outputFile).use { fos ->
                fos.write(byteData)
                fos.flush()
            }

            // 4. Save Directly to Public Android Gallery (Pictures/Scanova)
            var galleryUriString: String? = null
            if (options.saveToGallery) {
                galleryUriString = saveToPublicGallery(context, fileName, options.format.mimeType, byteData)
            }

            val finalWidth = bitmap.width
            val finalHeight = bitmap.height

            return ExportResult(
                file = outputFile,
                widthPx = finalWidth,
                heightPx = finalHeight,
                fileSizeBytes = outputFile.length(),
                savedToGalleryUri = galleryUriString
            )
        } catch (e: Exception) {
            Log.e("ImageExporter", "Error exporting image", e)
            return null
        }
    }

    /**
     * Ultra high-clarity compression.
     * When targetKb is null, outputs at 99-100% crystal quality.
     */
    private fun compressToTargetKb(
        initialBitmap: Bitmap,
        format: ImageFormat,
        targetKb: Int?
    ): ByteArray {
        var currentBitmap = initialBitmap
        val maxBytes = if (targetKb != null && targetKb > 0) targetKb * 1024L else Long.MAX_VALUE

        val baos = ByteArrayOutputStream()

        if (format == ImageFormat.JPG) {
            var quality = if (targetKb == null) 100 else 98
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

            // If quality reduction is requested to meet custom small KB target
            while (baos.toByteArray().size > maxBytes && quality > 15) {
                baos.reset()
                quality -= 5
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }

            var scaleFactor = 0.92f
            while (baos.toByteArray().size > maxBytes && currentBitmap.width > 200 && currentBitmap.height > 200) {
                baos.reset()
                val newW = (currentBitmap.width * scaleFactor).toInt().coerceAtLeast(100)
                val newH = (currentBitmap.height * scaleFactor).toInt().coerceAtLeast(100)
                val resized = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
                currentBitmap = resized
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }
        } else {
            // PNG (100% Lossless)
            currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)

            var scaleFactor = 0.90f
            while (baos.toByteArray().size > maxBytes && currentBitmap.width > 200 && currentBitmap.height > 200) {
                baos.reset()
                val newW = (currentBitmap.width * scaleFactor).toInt().coerceAtLeast(100)
                val newH = (currentBitmap.height * scaleFactor).toInt().coerceAtLeast(100)
                val resized = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
                currentBitmap = resized
                currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            }
        }

        return baos.toByteArray()
    }

    /**
     * Instantly stores image bytes into phone's public MediaStore (Pictures/Scanova)
     * so it shows up at the top of the device's Gallery app immediately.
     */
    private fun saveToPublicGallery(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): String? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Scanova")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                    os.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }

                uri.toString()
            } else null
        } catch (e: Exception) {
            Log.e("ImageExporter", "Error saving to MediaStore Gallery", e)
            null
        }
    }
}
