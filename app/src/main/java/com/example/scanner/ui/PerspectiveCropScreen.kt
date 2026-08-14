package com.example.scanner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner.model.DocumentQuad
import com.example.scanner.processor.BitmapDocumentDetector
import com.example.scanner.processor.PerspectiveTransformer
import com.example.ui.components.ScanovaPrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CropPreset(val label: String) {
    FREE("Free / Auto"),
    FULL_PAGE("Full Image"),
    A4("A4 (Document)"),
    ID_CARD("ID Card"),
    RECEIPT("Receipt"),
    SQUARE("Square (1:1)")
}

@Composable
fun PerspectiveCropScreen(
    imagePath: String,
    initialQuad: DocumentQuad?,
    onRetake: () -> Unit,
    onContinue: (correctedImagePath: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    var selectedCorner by remember { mutableStateOf(ActiveCorner.TOP_LEFT) }
    var selectedEdge by remember { mutableStateOf(ActiveEdge.NONE) }
    val lockedCorners = remember { mutableStateListOf<ActiveCorner>() }

    var activePreset by remember { mutableStateOf(CropPreset.FREE) }
    var isAdjusting by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(true) }
    var processingMessage by remember { mutableStateOf("Detecting document...") }

    // Undo / Redo History Stack
    val history = remember { mutableStateListOf<DocumentQuad>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    // Live Quad (updates seamlessly during drag, committed on drag release)
    var liveQuad by remember { mutableStateOf<DocumentQuad?>(null) }

    val currentQuad: DocumentQuad? = liveQuad ?: if (history.isNotEmpty() && historyIndex in history.indices) {
        history[historyIndex]
    } else null

    var autoDetectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }

    // Helper to push new quad state to history stack
    fun pushHistory(newQuad: DocumentQuad) {
        if (historyIndex >= 0 && historyIndex < history.size && history[historyIndex] == newQuad) {
            liveQuad = newQuad
            return
        }
        while (history.size > historyIndex + 1) {
            history.removeAt(history.size - 1)
        }
        history.add(newQuad)
        historyIndex = history.size - 1
        liveQuad = newQuad
    }

    // 1. Load Bitmap & Initial Auto Detection
    LaunchedEffect(imagePath) {
        isProcessing = true
        processingMessage = "Detecting document..."

        withContext(Dispatchers.IO) {
            try {
                val loaded = BitmapFactory.decodeFile(imagePath)
                rawBitmap = loaded

                if (loaded != null) {
                    val detected = initialQuad ?: BitmapDocumentDetector.detectCorners(loaded)
                    val quadToUse = detected ?: BitmapDocumentDetector.getDefaultInsetQuad()
                    autoDetectedQuad = quadToUse
                    withContext(Dispatchers.Main) {
                        pushHistory(quadToUse)
                    }
                }
            } catch (e: Exception) {
                Log.e("PerspectiveCropScreen", "Error loading bitmap", e)
            } finally {
                isProcessing = false
            }
        }
    }

    // Nudge Selected Corner or Edge by Normalized Delta
    fun nudge(dxNorm: Float, dyNorm: Float) {
        val quad = currentQuad ?: return

        if (selectedCorner != ActiveCorner.NONE) {
            if (lockedCorners.contains(selectedCorner)) return

            val curPt = when (selectedCorner) {
                ActiveCorner.TOP_LEFT -> quad.topLeft
                ActiveCorner.TOP_RIGHT -> quad.topRight
                ActiveCorner.BOTTOM_RIGHT -> quad.bottomRight
                ActiveCorner.BOTTOM_LEFT -> quad.bottomLeft
                else -> PointF(0f, 0f)
            }

            val newPt = PointF(
                (curPt.x + dxNorm).coerceIn(0.005f, 0.995f),
                (curPt.y + dyNorm).coerceIn(0.005f, 0.995f)
            )

            val updated = when (selectedCorner) {
                ActiveCorner.TOP_LEFT -> quad.copy(topLeft = newPt)
                ActiveCorner.TOP_RIGHT -> quad.copy(topRight = newPt)
                ActiveCorner.BOTTOM_RIGHT -> quad.copy(bottomRight = newPt)
                ActiveCorner.BOTTOM_LEFT -> quad.copy(bottomLeft = newPt)
                else -> quad
            }

            if (updated.isValidQuad()) {
                pushHistory(updated)
            }
        } else if (selectedEdge != ActiveEdge.NONE) {
            val updated = when (selectedEdge) {
                ActiveEdge.TOP -> quad.copy(
                    topLeft = PointF(quad.topLeft.x, (quad.topLeft.y + dyNorm).coerceIn(0.005f, 0.995f)),
                    topRight = PointF(quad.topRight.x, (quad.topRight.y + dyNorm).coerceIn(0.005f, 0.995f))
                )
                ActiveEdge.BOTTOM -> quad.copy(
                    bottomLeft = PointF(quad.bottomLeft.x, (quad.bottomLeft.y + dyNorm).coerceIn(0.005f, 0.995f)),
                    bottomRight = PointF(quad.bottomRight.x, (quad.bottomRight.y + dyNorm).coerceIn(0.005f, 0.995f))
                )
                ActiveEdge.LEFT -> quad.copy(
                    topLeft = PointF((quad.topLeft.x + dxNorm).coerceIn(0.005f, 0.995f), quad.topLeft.y),
                    bottomLeft = PointF((quad.bottomLeft.x + dxNorm).coerceIn(0.005f, 0.995f), quad.bottomLeft.y)
                )
                ActiveEdge.RIGHT -> quad.copy(
                    topRight = PointF((quad.topRight.x + dxNorm).coerceIn(0.005f, 0.995f), quad.topRight.y),
                    bottomRight = PointF((quad.bottomRight.x + dxNorm).coerceIn(0.005f, 0.995f), quad.bottomRight.y)
                )
                else -> quad
            }

            if (updated.isValidQuad()) {
                pushHistory(updated)
            }
        }
    }

    // Apply Preset Option
    fun applyPreset(preset: CropPreset) {
        activePreset = preset
        val quad = currentQuad ?: DocumentQuad.fullImageQuad()

        val adjusted = when (preset) {
            CropPreset.FREE -> autoDetectedQuad ?: DocumentQuad.defaultInsetQuad()
            CropPreset.FULL_PAGE -> DocumentQuad.fullImageQuad()
            CropPreset.A4 -> DocumentQuad.a4Quad()
            CropPreset.ID_CARD -> DocumentQuad.idCardQuad()
            CropPreset.RECEIPT -> {
                val cx = 0.5f
                val cy = 0.5f
                val halfW = 0.22f
                val halfH = 0.44f
                DocumentQuad(
                    topLeft = PointF(cx - halfW, cy - halfH),
                    topRight = PointF(cx + halfW, cy - halfH),
                    bottomRight = PointF(cx + halfW, cy + halfH),
                    bottomLeft = PointF(cx - halfW, cy + halfH)
                )
            }
            CropPreset.SQUARE -> DocumentQuad.squareQuad()
        }

        if (adjusted.isValidQuad()) {
            pushHistory(adjusted)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("perspective_crop_screen"),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            val bmp = rawBitmap
            val quad = currentQuad

            // 1. Main Viewport Image with Accurate Pixel-Aligned CropOverlay
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp, bottom = 230.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Document Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!isProcessing && quad != null) {
                        CropOverlay(
                            bitmap = bmp,
                            quad = quad,
                            onQuadChanged = { newQ -> liveQuad = newQ },
                            onDragEnd = { finalQ -> pushHistory(finalQ) },
                            selectedCorner = selectedCorner,
                            onCornerSelected = { corner -> selectedCorner = corner },
                            selectedEdge = selectedEdge,
                            onEdgeSelected = { edge -> selectedEdge = edge },
                            lockedCorners = lockedCorners.toSet(),
                            isAdjusting = isAdjusting,
                            onAdjustingChanged = { adj -> isAdjusting = adj },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 2. Top Header Bar (Back, Undo, Redo, Lock, Maximize, Reset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .size(42.dp)
                        .testTag("crop_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Compact Toolbar: Undo | Redo | Corner Lock | Full | Reset
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo
                    IconButton(
                        onClick = {
                            if (historyIndex > 0) {
                                historyIndex--
                                liveQuad = history[historyIndex]
                            }
                        },
                        enabled = historyIndex > 0,
                        modifier = Modifier.size(36.dp).testTag("crop_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (historyIndex > 0) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (historyIndex < history.size - 1) {
                                historyIndex++
                                liveQuad = history[historyIndex]
                            }
                        },
                        enabled = historyIndex < history.size - 1,
                        modifier = Modifier.size(36.dp).testTag("crop_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (historyIndex < history.size - 1) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // Corner Lock Toggle
                    val isCornerLocked = lockedCorners.contains(selectedCorner)
                    IconButton(
                        onClick = {
                            if (selectedCorner != ActiveCorner.NONE) {
                                if (isCornerLocked) {
                                    lockedCorners.remove(selectedCorner)
                                } else {
                                    lockedCorners.add(selectedCorner)
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp).testTag("crop_lock_button")
                    ) {
                        Icon(
                            imageVector = if (isCornerLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock Corner",
                            tint = if (isCornerLocked) Color(0xFFFF5252) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Full Screen / Maximize Button
                    IconButton(
                        onClick = {
                            applyPreset(CropPreset.FULL_PAGE)
                        },
                        modifier = Modifier.size(36.dp).testTag("crop_full_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Page",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // Reset / Auto Inset Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val resetQ = autoDetectedQuad ?: DocumentQuad.defaultInsetQuad()
                                pushHistory(resetQ)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("crop_reset_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 3. Bottom Control Stack (Presets Carousel, 4 Corner Selectors + D-Pad Nudge, Actions)
            if (!isProcessing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Presets Carousel Row
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(CropPreset.entries) { preset ->
                            val isSelected = activePreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                                    )
                                    .clickable { applyPreset(preset) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("preset_${preset.name.lowercase()}")
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }

                    // 4-Corner Selector Tabs & Directional Nudge D-Pad
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 4 Corner Selection Options (Top-Left, Top-Right, Bottom-Right, Bottom-Left)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "4-Point Options (Select Corner):",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    Triple("TL", ActiveCorner.TOP_LEFT, "Top Left"),
                                    Triple("TR", ActiveCorner.TOP_RIGHT, "Top Right"),
                                    Triple("BR", ActiveCorner.BOTTOM_RIGHT, "Bottom Right"),
                                    Triple("BL", ActiveCorner.BOTTOM_LEFT, "Bottom Left")
                                ).forEach { (label, corner, _) ->
                                    val sel = selectedCorner == corner
                                    val locked = lockedCorners.contains(corner)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    locked -> Color(0xFFFF5252).copy(alpha = 0.85f)
                                                    sel -> Color(0xFF00E5D9)
                                                    else -> Color.White.copy(alpha = 0.18f)
                                                }
                                            )
                                            .clickable {
                                                selectedCorner = corner
                                                selectedEdge = ActiveEdge.NONE
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("select_corner_${label.lowercase()}")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sel || locked) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Directional Nudge D-Pad (Up, Down, Left, Right)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // UP
                            NudgeButton(
                                icon = Icons.Default.ExpandLess,
                                contentDescription = "Move Up",
                                onClick = { nudge(0f, -0.015f) },
                                testTag = "nudge_up"
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // LEFT
                                NudgeButton(
                                    icon = Icons.Default.ChevronLeft,
                                    contentDescription = "Move Left",
                                    onClick = { nudge(-0.015f, 0f) },
                                    testTag = "nudge_left"
                                )

                                // DOWN
                                NudgeButton(
                                    icon = Icons.Default.ExpandMore,
                                    contentDescription = "Move Down",
                                    onClick = { nudge(0f, 0.015f) },
                                    testTag = "nudge_down"
                                )

                                // RIGHT
                                NudgeButton(
                                    icon = Icons.Default.ChevronRight,
                                    contentDescription = "Move Right",
                                    onClick = { nudge(0.015f, 0f) },
                                    testTag = "nudge_right"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Toolbar & Primary Action (Auto Detect | Rotate | Apply Crop)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Auto Detect Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    val b = rawBitmap
                                    if (b != null) {
                                        scope.launch(Dispatchers.IO) {
                                            val reDetected = BitmapDocumentDetector.detectCorners(b)
                                                ?: DocumentQuad.defaultInsetQuad()
                                            withContext(Dispatchers.Main) {
                                                autoDetectedQuad = reDetected
                                                pushHistory(reDetected)
                                                Toast.makeText(context, "Document Corners Detected", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("auto_detect_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Auto Detect",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Rotate Left
                        IconButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees - 90 + 360) % 360
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .size(44.dp)
                                .testTag("crop_rotate_left_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateLeft,
                                contentDescription = "Rotate Left",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Rotate Right
                        IconButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90) % 360
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .size(44.dp)
                                .testTag("crop_rotate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate Right",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Apply Crop ✓ Primary Button
                        ScanovaPrimaryButton(
                            text = "Apply Crop",
                            onClick = {
                                isProcessing = true
                                processingMessage = "Straightening document..."
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val b = rawBitmap ?: BitmapFactory.decodeFile(imagePath)
                                        val q = currentQuad ?: DocumentQuad.fullImageQuad()

                                        if (b != null) {
                                            val finalCorrected = PerspectiveTransformer.transform(
                                                b,
                                                q,
                                                rotationDegrees
                                            )

                                            val outFile = File(
                                                context.cacheDir,
                                                "CORRECTED_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                                            )
                                            val fos = FileOutputStream(outFile)
                                            finalCorrected.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                            fos.flush()
                                            fos.close()
                                            finalCorrected.recycle()

                                            withContext(Dispatchers.Main) {
                                                onContinue(outFile.absolutePath)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                onContinue(imagePath)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PerspectiveCrop", "Error applying crop", e)
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            onContinue(imagePath)
                                        }
                                    }
                                }
                            },
                            icon = Icons.Default.Check,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("apply_crop_button")
                        )
                    }
                }
            }

            // 4. Processing Overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
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
        }
    }

    // Bitmap state lifecycle is cleanly reclaimed by GC on disposal
}

@Composable
private fun NudgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    var isPressing by remember { mutableStateOf(false) }

    LaunchedEffect(isPressing) {
        if (isPressing) {
            while (isPressing) {
                onClick()
                delay(70)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressing = true
                        tryAwaitRelease()
                        isPressing = false
                    },
                    onTap = { onClick() }
                )
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

