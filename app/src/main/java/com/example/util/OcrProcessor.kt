package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object OcrProcessor {

    private const val TAG = "OcrProcessor"

    suspend fun extractTextFromImage(context: Context, imageFile: File): Result<String> {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    if (!imageFile.exists()) {
                        continuation.resume(Result.failure(IllegalArgumentException("Image file does not exist")))
                        return@suspendCancellableCoroutine
                    }

                    val inputImage = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text.trim()
                            Log.d(TAG, "OCR Success: ${text.length} characters extracted")
                            continuation.resume(Result.success(text))
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "OCR Processing failed", e)
                            continuation.resume(Result.failure(e))
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "OCR Exception", e)
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }
}
