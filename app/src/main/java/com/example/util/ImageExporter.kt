package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
        val targetSizeKb: Int? = null, // e.g. 30, 50, 100, 500 KB or null for original quality
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
     * Processes source image according to target dimensions, format, and target KB size,
     * then saves to local cache and public Android Gallery (Pictures/Scanova).
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
            var bitmap = BitmapFactory.decodeFile(sourceImagePath) ?: return null

            val originalW = bitmap.width
            val originalH = bitmap.height

            // 1. Calculate Target Dimensions in Pixels
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

            // Scale bitmap if dimensions changed
            if (targetW != originalW || targetH != originalH) {
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            // 2. Compress and scale to fit Target KB Size (if specified)
            val byteData = compressToTargetKb(bitmap, options.format, options.targetSizeKb)

            // 3. Save to App Cache / Files Dir
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cleanTitle = documentTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(25)
            val fileName = "Scanova_${cleanTitle}_$timeStamp.${options.format.extension}"

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outputFile = File(exportDir, fileName)

            FileOutputStream(outputFile).use { fos ->
                fos.write(byteData)
                fos.flush()
            }

            // 4. Save to System Public Gallery (Pictures/Scanova)
            var galleryUriString: String? = null
            if (options.saveToGallery) {
                galleryUriString = saveToPublicGallery(context, fileName, options.format.mimeType, byteData)
            }

            val finalWidth = bitmap.width
            val finalHeight = bitmap.height
            bitmap.recycle()

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
     * Smart iterative compression loop that adjusts quality and scale to keep image under target KB
     * while preserving maximum visual sharpness.
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
            var quality = 95
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

            // If quality adjustment is needed to hit target KB limit
            while (baos.toByteArray().size > maxBytes && quality > 15) {
                baos.reset()
                quality -= 8
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }

            // If still too large after quality reductions, downscale bitmap size iteratively
            var scaleFactor = 0.90f
            while (baos.toByteArray().size > maxBytes && currentBitmap.width > 200 && currentBitmap.height > 200) {
                baos.reset()
                val newW = (currentBitmap.width * scaleFactor).toInt().coerceAtLeast(100)
                val newH = (currentBitmap.height * scaleFactor).toInt().coerceAtLeast(100)
                val resized = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
                if (resized != currentBitmap && currentBitmap != initialBitmap) {
                    currentBitmap.recycle()
                }
                currentBitmap = resized
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }
        } else {
            // PNG (Lossless)
            currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)

            // If target KB is restricted, downscale dimensions
            var scaleFactor = 0.88f
            while (baos.toByteArray().size > maxBytes && currentBitmap.width > 200 && currentBitmap.height > 200) {
                baos.reset()
                val newW = (currentBitmap.width * scaleFactor).toInt().coerceAtLeast(100)
                val newH = (currentBitmap.height * scaleFactor).toInt().coerceAtLeast(100)
                val resized = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
                if (resized != currentBitmap && currentBitmap != initialBitmap) {
                    currentBitmap.recycle()
                }
                currentBitmap = resized
                currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            }
        }

        if (currentBitmap != initialBitmap) {
            currentBitmap.recycle()
        }

        return baos.toByteArray()
    }

    /**
     * Saves byte array into Android's public Gallery MediaStore so it appears in phone's Gallery app.
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
            Log.e("ImageExporter", "Error saving to MediaStore", e)
            null
        }
    }
}
