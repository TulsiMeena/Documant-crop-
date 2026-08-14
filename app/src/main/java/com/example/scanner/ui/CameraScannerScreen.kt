package com.example.scanner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scanner.detector.DocumentDetector
import com.example.scanner.detector.QuadSmoother
import com.example.scanner.model.DetectionStatus
import com.example.scanner.model.DocumentQuad
import com.example.scanner.processor.BitmapDocumentDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var showGrid by remember { mutableStateOf(false) }
    var scanMode by remember { mutableStateOf(ScanMode.DOCUMENT) }

    var detectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var detectionStatus by remember { mutableStateOf(DetectionStatus.LOOKING) }
    var isCapturing by remember { mutableStateOf(false) }
    var showFlashEffect by remember { mutableStateOf(false) }
    var batchCount by remember { mutableIntStateOf(0) }

    // Tilt Level Sensor States
    var tiltPitch by remember { mutableFloatStateOf(0f) }
    var tiltRoll by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()
    val detector = remember { DocumentDetector() }
    val smoother = remember { QuadSmoother() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Register Accelerometer for Spirit Level
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    tiltRoll = x
                    tiltPitch = y
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
            analysisExecutor.shutdown()
        }
    }

    // Gallery Picker Contract
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val cachedFile = copyUriToCache(context, uri)
                if (cachedFile != null) {
                    val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                    val quad = if (bitmap != null) {
                        val detected = BitmapDocumentDetector.detectCorners(bitmap)
                        bitmap.recycle()
                        detected
                    } else null
                    onImageCaptured(cachedFile.absolutePath, quad)
                }
            }
        }
    }

    // Auto-capture Trigger logic
    var steadyCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(detectionStatus, isAutoCaptureEnabled, isCapturing) {
        if (isAutoCaptureEnabled && !isCapturing) {
            if (detectionStatus == DetectionStatus.READY) {
                steadyCount++
                if (steadyCount >= 3) {
                    triggerCapture(
                        context = context,
                        imageCapture = imageCapture,
                        detectedQuad = detectedQuad,
                        isCapturing = isCapturing,
                        onCaptureStart = {
                            isCapturing = true
                            showFlashEffect = true
                        },
                        onCaptureSuccess = { path, quad ->
                            isCapturing = false
                            if (scanMode == ScanMode.BATCH) {
                                batchCount++
                                Toast.makeText(context, "Page $batchCount captured!", Toast.LENGTH_SHORT).show()
                            } else {
                                onImageCaptured(path, quad)
                            }
                        },
                        onCaptureError = {
                            isCapturing = false
                            steadyCount = 0
                        }
                    )
                }
            } else {
                steadyCount = 0
            }
        }
    }

    // Flash animation reset
    LaunchedEffect(showFlashEffect) {
        if (showFlashEffect) {
            delay(120)
            showFlashEffect = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_scanner_screen"),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. CameraX Preview View
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
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        imageCapture = capture

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val rawQuad = detector.detectDocument(imageProxy)
                            val (smoothedQuad, status) = smoother.process(rawQuad)
                            detectedQuad = smoothedQuad
                            detectionStatus = status
                        }

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                imageAnalysis
                            )
                            camera = boundCamera
                            hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
                        } catch (e: Exception) {
                            Log.e("CameraScannerScreen", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Realtime Document Overlay with Spirit Level and Guides
            DocumentOverlay(
                quad = detectedQuad,
                status = detectionStatus,
                scanMode = scanMode,
                showGrid = showGrid,
                tiltPitch = tiltPitch,
                tiltRoll = tiltRoll,
                modifier = Modifier.fillMaxSize()
            )

            // 3. Capture Screen Flash Effect
            AnimatedVisibility(
                visible = showFlashEffect,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
            }

            // 4. Top Action Bar: Back, Grid, Auto, Flash, Lens Flip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.60f))
                        .size(42.dp)
                        .testTag("camera_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Header Control Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Grid Toggle Button
                    IconButton(
                        onClick = { showGrid = !showGrid },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (showGrid) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                else Color.Black.copy(alpha = 0.60f)
                            )
                            .size(40.dp)
                            .testTag("camera_grid_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Grid",
                            tint = if (showGrid) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Auto Capture Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isAutoCaptureEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                else Color.Black.copy(alpha = 0.60f)
                            )
                            .clickable { isAutoCaptureEnabled = !isAutoCaptureEnabled }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("auto_capture_toggle")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Auto",
                                tint = if (isAutoCaptureEnabled) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAutoCaptureEnabled) "Auto ON" else "Auto OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isAutoCaptureEnabled) Color.Black else Color.White
                            )
                        }
                    }

                    // Flash Toggle Button
                    if (hasFlashUnit) {
                        IconButton(
                            onClick = {
                                isFlashOn = !isFlashOn
                                camera?.cameraControl?.enableTorch(isFlashOn)
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isFlashOn) Color(0xFFFFD700).copy(alpha = 0.85f)
                                    else Color.Black.copy(alpha = 0.60f)
                                )
                                .size(40.dp)
                                .testTag("flash_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash Toggle",
                                tint = if (isFlashOn) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Flip Lens Button
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f))
                            .size(40.dp)
                            .testTag("flip_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 5. Zoom Floating Pill (1x / 2x)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .clickable {
                        zoomRatio = if (zoomRatio <= 1.2f) 2f else 1f
                        camera?.cameraControl?.setZoomRatio(zoomRatio)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("camera_zoom_toggle")
            ) {
                Text(
                    text = "${zoomRatio.toInt()}x",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 6. Bottom Scanning Control Stack (Modes Carousel + Shutter + Gallery Shortcut)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(top = 10.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Switcher Tabs (Document | ID Card | Batch | Book)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(ScanMode.entries) { mode ->
                        val isSelected = scanMode == mode
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                                )
                                .clickable { scanMode = mode }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("scan_mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }

                // Shutter Row: Gallery | Big Shutter Button | Batch Finish (if Batch mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery Picker
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .size(52.dp)
                            .testTag("gallery_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Import from Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Main Camera Shutter Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCapturing) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                            .clickable(enabled = !isCapturing) {
                                triggerCapture(
                                    context = context,
                                    imageCapture = imageCapture,
                                    detectedQuad = detectedQuad,
                                    isCapturing = isCapturing,
                                    onCaptureStart = {
                                        isCapturing = true
                                        showFlashEffect = true
                                    },
                                    onCaptureSuccess = { path, quad ->
                                        isCapturing = false
                                        if (scanMode == ScanMode.BATCH) {
                                            batchCount++
                                            Toast.makeText(context, "Page $batchCount captured!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onImageCaptured(path, quad)
                                        }
                                    },
                                    onCaptureError = {
                                        isCapturing = false
                                    }
                                )
                            }
                            .testTag("camera_shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    // Batch Done Button or Spacer
                    if (scanMode == ScanMode.BATCH && batchCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    onBackClick()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("batch_done_button")
                        ) {
                            Text(
                                text = "Done ($batchCount)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(52.dp))
                    }
                }
            }
        }
    }
}

private fun triggerCapture(
    context: Context,
    imageCapture: ImageCapture?,
    detectedQuad: DocumentQuad?,
    isCapturing: Boolean,
    onCaptureStart: () -> Unit,
    onCaptureSuccess: (imagePath: String, quad: DocumentQuad?) -> Unit,
    onCaptureError: () -> Unit
) {
    if (imageCapture == null || isCapturing) return

    onCaptureStart()

    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFile = File(context.cacheDir, "SCAN_$timeStamp.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onCaptureSuccess(photoFile.absolutePath, detectedQuad)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraScannerScreen", "Photo capture failed: ${exc.message}", exc)
                onCaptureError()
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
