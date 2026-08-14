package com.example.scanner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner.model.AdjustParams
import com.example.scanner.model.EnhanceMode
import com.example.scanner.processor.DocumentEnhancer
import com.example.ui.components.ExportImageDialog
import com.example.ui.components.ScanovaOutlinedButton
import com.example.ui.components.ScanovaPrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun DocumentEnhanceScreen(
    imagePath: String,
    onBack: () -> Unit,
    onContinue: (enhancedImagePath: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Full resolution source bitmap (Perspective-corrected from Part 3)
    var fullResSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Downscaled preview bitmap (~800px max) for responsive live slider feedback
    var previewSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Rendered enhanced preview bitmap
    var renderedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // State parameters
    var selectedMode by remember { mutableStateOf(EnhanceMode.AUTO) }
    var adjustParams by remember { mutableStateOf(AdjustParams()) }

    var isProcessing by remember { mutableStateOf(true) }
    var processingMessage by remember { mutableStateOf("Loading image...") }
    var enhancementError by remember { mutableStateOf(false) }

    // Controls UI state
    var showAdjustPanel by remember { mutableStateOf(false) }
    var isHoldingToCompare by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // 1. Initial Image Loading & Downscaled Preview Creation
    LaunchedEffect(imagePath) {
        isProcessing = true
        enhancementError = false
        processingMessage = "Preparing document..."

        withContext(Dispatchers.IO) {
            try {
                val fullBmp = BitmapFactory.decodeFile(imagePath)
                if (fullBmp != null) {
                    fullResSourceBitmap = fullBmp

                    // Downsample to max 800px dimension for high performance live preview
                    val targetDim = 800
                    val maxDim = max(fullBmp.width, fullBmp.height)
                    val scale = if (maxDim > targetDim) targetDim.toFloat() / maxDim else 1.0f

                    val previewW = max(100, (fullBmp.width * scale).toInt())
                    val previewH = max(100, (fullBmp.height * scale).toInt())

                    val scaledBmp = Bitmap.createScaledBitmap(fullBmp, previewW, previewH, true)
                    previewSourceBitmap = scaledBmp

                    // Run initial AUTO enhancement on preview bitmap
                    processingMessage = "Applying Auto enhancement..."
                    val enhancedPreview = DocumentEnhancer.enhance(
                        scaledBmp,
                        EnhanceMode.AUTO,
                        AdjustParams()
                    )
                    renderedPreviewBitmap = enhancedPreview
                } else {
                    enhancementError = true
                }
            } catch (e: Exception) {
                Log.e("DocumentEnhanceScreen", "Failed loading image", e)
                enhancementError = true
            } finally {
                isProcessing = false
            }
        }
    }

    // 2. Reactive Preview Processing on Mode or Slider Adjustment Change
    val updatePreview: () -> Unit = {
        val srcBmp = previewSourceBitmap
        val mode = selectedMode
        val params = adjustParams

        if (srcBmp != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val result = DocumentEnhancer.enhance(srcBmp, mode, params)
                    withContext(Dispatchers.Main) {
                        val oldBitmap = renderedPreviewBitmap
                        renderedPreviewBitmap = result
                        // Let GC manage bitmap lifecycle safely without breaking Compose rendering pipeline
                    }
                } catch (e: Exception) {
                    Log.e("DocumentEnhanceScreen", "Error updating preview", e)
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("document_enhance_screen"),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Main Document Image Viewport
            val currentRendered = if (isHoldingToCompare) previewSourceBitmap else renderedPreviewBitmap

            if (currentRendered != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp, bottom = if (showAdjustPanel) 290.dp else 220.dp, start = 12.dp, end = 12.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHoldingToCompare = true
                                    tryAwaitRelease()
                                    isHoldingToCompare = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = currentRendered.asImageBitmap(),
                        contentDescription = "Enhanced Document Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("enhanced_preview_image")
                    )

                    // Press and Hold indicator tag
                    if (isHoldingToCompare) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Original (Part 3 Output)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("enhance_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Enhance Document",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Export Image / Save to Gallery Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { showExportDialog = true }
                            .padding(10.dp)
                            .testTag("export_image_top_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save to Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Before / After Comparison Quick Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isHoldingToCompare) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                            )
                            .clickable { isHoldingToCompare = !isHoldingToCompare }
                            .padding(10.dp)
                            .testTag("compare_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Compare Original vs Enhanced",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Export Image Dialog (Custom dimensions px/in, target file size 30/50/100/500 KB, format JPG/PNG)
            if (showExportDialog) {
                ExportImageDialog(
                    sourceImagePath = imagePath,
                    documentTitle = "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}",
                    onDismiss = { showExportDialog = false }
                )
            }

            // Loading Progress Overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = processingMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Error Banner Fallback
            if (enhancementError && !isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp, start = 20.dp, end = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                        .border(1.dp, Color(0xFFD32F2F), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .testTag("enhancement_error_banner")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Couldn't enhance this image.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ScanovaOutlinedButton(
                                text = "Try Again",
                                onClick = {
                                    enhancementError = false
                                    updatePreview()
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "error_try_again_button"
                            )
                            ScanovaPrimaryButton(
                                text = "Use Original",
                                onClick = {
                                    val src = fullResSourceBitmap
                                    if (src != null) {
                                        onContinue(imagePath)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "error_use_original_button"
                            )
                        }
                    }
                }
            }

            // Bottom Controls Section
            if (!isProcessing && !enhancementError) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF121212))
                ) {
                    // 1. Expandable Manual Slider Adjust Panel
                    AnimatedVisibility(
                        visible = showAdjustPanel,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E))
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .testTag("adjust_sliders_panel")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MANUAL ADJUSTMENTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable {
                                            adjustParams = AdjustParams()
                                            updatePreview()
                                        }
                                        .testTag("adjust_reset_button")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Brightness Slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Brightness",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.width(80.dp)
                                )
                                Slider(
                                    value = adjustParams.brightness,
                                    onValueChange = { newValue ->
                                        adjustParams = adjustParams.copy(brightness = newValue)
                                        updatePreview()
                                    },
                                    valueRange = -0.8f..0.8f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("brightness_slider")
                                )
                            }

                            // Contrast Slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Contrast",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.width(80.dp)
                                )
                                Slider(
                                    value = adjustParams.contrast,
                                    onValueChange = { newValue ->
                                        adjustParams = adjustParams.copy(contrast = newValue)
                                        updatePreview()
                                    },
                                    valueRange = -0.5f..0.8f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("contrast_slider")
                                )
                            }

                            // Sharpness Slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Sharpness",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.width(80.dp)
                                )
                                Slider(
                                    value = adjustParams.sharpness,
                                    onValueChange = { newValue ->
                                        adjustParams = adjustParams.copy(sharpness = newValue)
                                        updatePreview()
                                    },
                                    valueRange = 0.0f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sharpness_slider")
                                )
                            }
                        }
                    }

                    // 2. Mode Action Bar (Adjust Toggle & Reset Default)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Adjust Toggle Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (showAdjustPanel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { showAdjustPanel = !showAdjustPanel }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("adjust_toggle_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Adjust",
                                tint = if (showAdjustPanel) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Adjust",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showAdjustPanel) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Reset Button for currently selected mode
                        Row(
                            modifier = Modifier
                                .clickable {
                                    adjustParams = AdjustParams()
                                    updatePreview()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("reset_filter_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Settings",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // 3. Compact Primary Filter Mode Options (Original | Auto | Color | Grayscale | B&W)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EnhanceMode.values().forEach { mode ->
                            val isSelected = selectedMode == mode
                            val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF262626)
                            val textColor = if (isSelected) Color.Black else Color.White

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bg)
                                    .clickable {
                                        if (selectedMode != mode) {
                                            selectedMode = mode
                                            updatePreview()
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("filter_mode_${mode.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Primary Bottom Action Bar (Retake | Continue with Full-Res Processing)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ScanovaOutlinedButton(
                            text = "Back",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            testTag = "enhance_bottom_back_button"
                        )

                        ScanovaPrimaryButton(
                            text = "Save Scan",
                            onClick = {
                                isProcessing = true
                                processingMessage = "Saving high-resolution scan..."

                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val fullBmp = fullResSourceBitmap ?: BitmapFactory.decodeFile(imagePath)
                                        if (fullBmp != null) {
                                            val enhancedFullRes = DocumentEnhancer.enhance(
                                                fullBmp,
                                                selectedMode,
                                                adjustParams
                                            )

                                            val outFile = File(
                                                context.cacheDir,
                                                "ENHANCED_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                                            )
                                            val fos = FileOutputStream(outFile)
                                            enhancedFullRes.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                            fos.flush()
                                            fos.close()
                                            if (fullBmp != fullResSourceBitmap) {
                                                fullBmp.recycle()
                                            }
                                            enhancedFullRes.recycle()

                                            withContext(Dispatchers.Main) {
                                                onContinue(outFile.absolutePath)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                onContinue(imagePath)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("DocumentEnhanceScreen", "Error rendering full res output", e)
                                        withContext(Dispatchers.Main) {
                                            onContinue(imagePath)
                                        }
                                    }
                                }
                            },
                            icon = Icons.Default.Check,
                            modifier = Modifier.weight(1f),
                            testTag = "enhance_bottom_continue_button"
                        )
                    }
                }
            }
        }
    }

    // Bitmap state lifecycle is cleanly reclaimed by GC on disposal
}
