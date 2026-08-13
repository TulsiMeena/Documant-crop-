package com.example.scanner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scanner.detector.DocumentDetector
import com.example.scanner.detector.QuadSmoother
import com.example.scanner.model.DetectionStatus
import com.example.scanner.model.DocumentQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun CameraScannerScreen(
    onBackClick: () -> Unit,
    onImageCaptured: (imagePath: String, quad: DocumentQuad?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission state check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    if (!hasCameraPermission) {
        CameraPermissionScreen(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onCancel = onBackClick,
            modifier = modifier
        )
        return
    }

    // Camera & Detection States
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(true) }
    var isAutoCaptureEnabled by remember { mutableStateOf(true) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    var detectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var detectionStatus by remember { mutableStateOf(DetectionStatus.LOOKING) }
    var isCapturing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val detector = remember { DocumentDetector() }
    val smoother = remember { QuadSmoother() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Gallery Picker Contract
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val cachedFile = copyUriToCache(context, uri)
                if (cachedFile != null) {
                    onImageCaptured(cachedFile.absolutePath, null)
                }
            }
        }
    }

    // Auto Capture Trigger Effect
    LaunchedEffect(detectionStatus, isAutoCaptureEnabled, isCapturing) {
        if (isAutoCaptureEnabled && detectionStatus == DetectionStatus.READY && !isCapturing) {
            isCapturing = true
            takePhoto(
                context = context,
                imageCapture = imageCapture,
                onSuccess = { path ->
                    onImageCaptured(path, detectedQuad)
                },
                onError = {
                    isCapturing = false
                }
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_scanner_screen"),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Fullscreen Camera Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            cameraProvider.unbindAll()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()

                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().also { analyzer ->
                                    analyzer.setAnalyzer(analysisExecutor) { image ->
                                        val rawQuad = detector.detectDocument(image)
                                        val (smoothQuad, status) = smoother.process(rawQuad)

                                        // Update state on Main Thread
                                        scope.launch(Dispatchers.Main) {
                                            detectedQuad = smoothQuad
                                            detectionStatus = status
                                        }
                                    }
                                }

                            val selector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                capture,
                                analysis
                            )

                            camera = cam
                            imageCapture = capture
                            hasFlashUnit = cam.cameraInfo.hasFlashUnit()

                        } catch (e: Exception) {
                            Log.e("CameraScanner", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Real-time Document Detection Overlay Canvas
            DocumentOverlay(
                quad = detectedQuad,
                status = detectionStatus,
                modifier = Modifier.fillMaxSize()
            )

            // 3. Top Control Bar (Back | Flash | Auto-Capture Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("scanner_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Auto Capture Mode Toggle Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isAutoCaptureEnabled) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.5f)
                        )
                        .clickable { isAutoCaptureEnabled = !isAutoCaptureEnabled }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("auto_capture_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isAutoCaptureEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAutoCaptureEnabled) "Auto Scan ON" else "Auto Scan OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isAutoCaptureEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                        )
                    }
                }

                // Flash Button
                IconButton(
                    onClick = {
                        if (hasFlashUnit && camera != null) {
                            val newFlashState = !isFlashOn
                            camera?.cameraControl?.enableTorch(newFlashState)
                            isFlashOn = newFlashState
                        }
                    },
                    enabled = hasFlashUnit,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isFlashOn) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                        .testTag("scanner_flash_button")
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flash",
                        tint = if (hasFlashUnit) Color.White else Color.Gray
                    )
                }
            }

            // 4. Bottom Control Bar (Gallery | Large Capture Button | Camera Flip)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(vertical = 28.dp, horizontal = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small Gallery Import Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("scanner_gallery_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Large Shutter Capture Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (detectionStatus == DetectionStatus.READY) Color(0xFF00E5D9) else Color.White
                            )
                            .clickable(enabled = !isCapturing) {
                                isCapturing = true
                                takePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    onSuccess = { path ->
                                        onImageCaptured(path, detectedQuad)
                                    },
                                    onError = {
                                        isCapturing = false
                                    }
                                )
                            }
                            .testTag("scanner_capture_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    // Optional Camera Switch Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            }
                            .testTag("scanner_flip_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onSuccess: (String) -> Unit,
    onError: (Exception) -> Unit
) {
    if (imageCapture == null) {
        onError(IllegalStateException("Image capture is not bound"))
        return
    }

    val photoFile = File(
        context.cacheDir,
        "SCAN_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSuccess(photoFile.absolutePath)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraScanner", "Photo capture failed: ${exc.message}", exc)
                onError(exc)
            }
        }
    )
}

private suspend fun copyUriToCache(context: Context, uri: Uri): File? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val outputFile = File(
                context.cacheDir,
                "GALLERY_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            )
            val outputStream = FileOutputStream(outputFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            outputFile
        } catch (e: Exception) {
            Log.e("CameraScanner", "Failed to copy gallery image to cache", e)
            null
        }
    }
}
