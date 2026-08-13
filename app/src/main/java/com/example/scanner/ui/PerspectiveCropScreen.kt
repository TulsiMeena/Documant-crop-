package com.example.scanner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
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

enum class AspectRatioAssist(val label: String, val ratioWToH: Float) {
    AUTO("Auto", 0f),
    A4("A4", 0.707f),
    ID_CARD("ID Card", 1.586f),
    RECEIPT("Receipt", 0.45f)
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

    var activeRatio by remember { mutableStateOf(AspectRatioAssist.AUTO) }
    var isAdjusting by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(true) }
    var processingMessage by remember { mutableStateOf("Detecting document...") }

    // Undo / Redo History Stack
    val history = remember { mutableStateListOf<DocumentQuad>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    val currentQuad: DocumentQuad? = if (history.isNotEmpty() && historyIndex in history.indices) {
        history[historyIndex]
    } else null

    var autoDetectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }

    // Helper to push new quad state to history stack
    fun pushHistory(newQuad: DocumentQuad) {
        if (historyIndex >= 0 && historyIndex < history.size && history[historyIndex] == newQuad) return
        while (history.size > historyIndex + 1) {
            history.removeAt(history.size - 1)
        }
        history.add(newQuad)
        historyIndex = history.size - 1
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

    // Nudge Selected Corner or Edge by Pixel Delta
    fun nudge(deltaXPixels: Float, deltaYPixels: Float) {
        val bmp = rawBitmap ?: return
        val quad = currentQuad ?: return

        val dxNorm = deltaXPixels / bmp.width.toFloat()
        val dyNorm = deltaYPixels / bmp.height.toFloat()

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
                (curPt.x + dxNorm).coerceIn(0.01f, 0.99f),
                (curPt.y + dyNorm).coerceIn(0.01f, 0.99f)
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
                    topLeft = PointF((quad.topLeft.x + dxNorm).coerceIn(0.01f, 0.95f), (quad.topLeft.y + dyNorm).coerceIn(0.01f, 0.95f)),
                    topRight = PointF((quad.topRight.x + dxNorm).coerceIn(0.05f, 0.99f), (quad.topRight.y + dyNorm).coerceIn(0.01f, 0.95f))
                )
                ActiveEdge.BOTTOM -> quad.copy(
                    bottomLeft = PointF((quad.bottomLeft.x + dxNorm).coerceIn(0.01f, 0.95f), (quad.bottomLeft.y + dyNorm).coerceIn(0.05f, 0.99f)),
                    bottomRight = PointF((quad.bottomRight.x + dxNorm).coerceIn(0.05f, 0.99f), (quad.bottomRight.y + dyNorm).coerceIn(0.05f, 0.99f))
                )
                ActiveEdge.LEFT -> quad.copy(
                    topLeft = PointF((quad.topLeft.x + dxNorm).coerceIn(0.01f, 0.95f), (quad.topLeft.y + dyNorm).coerceIn(0.01f, 0.95f)),
                    bottomLeft = PointF((quad.bottomLeft.x + dxNorm).coerceIn(0.01f, 0.95f), (quad.bottomLeft.y + dyNorm).coerceIn(0.05f, 0.99f))
                )
                ActiveEdge.RIGHT -> quad.copy(
                    topRight = PointF((quad.topRight.x + dxNorm).coerceIn(0.05f, 0.99f), (quad.topRight.y + dyNorm).coerceIn(0.01f, 0.95f)),
                    bottomRight = PointF((quad.bottomRight.x + dxNorm).coerceIn(0.05f, 0.99f), (quad.bottomRight.y + dyNorm).coerceIn(0.05f, 0.99f))
                )
                else -> quad
            }

            if (updated.isValidQuad()) {
                pushHistory(updated)
            }
        }
    }

    // Apply Aspect Ratio Assist
    fun applyAspectRatio(assist: AspectRatioAssist) {
        activeRatio = assist
        if (assist == AspectRatioAssist.AUTO) return
        val quad = currentQuad ?: return

        val centerX = (quad.topLeft.x + quad.topRight.x + quad.bottomRight.x + quad.bottomLeft.x) / 4f
        val centerY = (quad.topLeft.y + quad.topRight.y + quad.bottomRight.y + quad.bottomLeft.y) / 4f

        val avgH = ((quad.bottomLeft.y - quad.topLeft.y) + (quad.bottomRight.y - quad.topRight.y)) / 2f
        val targetW = (avgH * assist.ratioWToH).coerceIn(0.15f, 0.85f)
        val halfW = targetW / 2f
        val halfH = avgH / 2f

        val adjusted = DocumentQuad(
            topLeft = PointF((centerX - halfW).coerceIn(0.02f, 0.95f), (centerY - halfH).coerceIn(0.02f, 0.95f)),
            topRight = PointF((centerX + halfW).coerceIn(0.05f, 0.98f), (centerY - halfH).coerceIn(0.02f, 0.95f)),
            bottomRight = PointF((centerX + halfW).coerceIn(0.05f, 0.98f), (centerY + halfH).coerceIn(0.05f, 0.98f)),
            bottomLeft = PointF((centerX - halfW).coerceIn(0.02f, 0.95f), (centerY + halfH).coerceIn(0.05f, 0.98f))
        )

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

            // 1. Main Viewport Image with Upgraded CropOverlay
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp, bottom = 220.dp)
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
                            onQuadChanged = { newQ -> pushHistory(newQ) },
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

            // 2. Compact Top Header Bar (Back, Undo, Redo, Lock, Reset)
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
                        .background(Color.Black.copy(alpha = 0.6f))
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

                // Center Compact Toolbar: Undo | Redo | Corner Lock | Reset
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo
                    IconButton(
                        onClick = {
                            if (historyIndex > 0) historyIndex--
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
                            if (historyIndex < history.size - 1) historyIndex++
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

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // Reset Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val resetQ = autoDetectedQuad ?: BitmapDocumentDetector.getDefaultInsetQuad()
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

            // 3. Compact Bottom Control Stack (Ratio Assist, Nudge D-Pad, Actions)
            if (!isProcessing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.90f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Aspect Ratio Assist Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ratio:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        AspectRatioAssist.entries.forEach { assist ->
                            val isSelected = activeRatio == assist
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                                    )
                                    .clickable { applyAspectRatio(assist) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("ratio_${assist.name.lowercase()}")
                            ) {
                                Text(
                                    text = assist.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 4-Way Micro-Nudge D-Pad Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Corner Selection Tabs
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Selected Point:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    Pair("TL", ActiveCorner.TOP_LEFT),
                                    Pair("TR", ActiveCorner.TOP_RIGHT),
                                    Pair("BR", ActiveCorner.BOTTOM_RIGHT),
                                    Pair("BL", ActiveCorner.BOTTOM_LEFT)
                                ).forEach { (label, corner) ->
                                    val sel = selectedCorner == corner
                                    val locked = lockedCorners.contains(corner)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    locked -> Color(0xFFFF5252).copy(alpha = 0.8f)
                                                    sel -> Color(0xFF00E5D9)
                                                    else -> Color.White.copy(alpha = 0.15f)
                                                }
                                            )
                                            .clickable {
                                                selectedCorner = corner
                                                selectedEdge = ActiveEdge.NONE
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("select_corner_${label.lowercase()}")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sel || locked) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Compact Directional Nudge D-Pad (Up, Down, Left, Right)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // UP
                            NudgeButton(
                                icon = Icons.Default.ExpandLess,
                                contentDescription = "Nudge Up",
                                onClick = { nudge(0f, -1f) },
                                testTag = "nudge_up"
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // LEFT
                                NudgeButton(
                                    icon = Icons.Default.ChevronLeft,
                                    contentDescription = "Nudge Left",
                                    onClick = { nudge(-1f, 0f) },
                                    testTag = "nudge_left"
                                )

                                // DOWN
                                NudgeButton(
                                    icon = Icons.Default.ExpandMore,
                                    contentDescription = "Nudge Down",
                                    onClick = { nudge(0f, 1f) },
                                    testTag = "nudge_down"
                                )

                                // RIGHT
                                NudgeButton(
                                    icon = Icons.Default.ChevronRight,
                                    contentDescription = "Nudge Right",
                                    onClick = { nudge(1f, 0f) },
                                    testTag = "nudge_right"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Toolbar & Primary Action (Auto Detect | Rotate | Apply Crop)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Auto Detect
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    val b = rawBitmap
                                    if (b != null) {
                                        scope.launch(Dispatchers.IO) {
                                            val reDetected = BitmapDocumentDetector.detectCorners(b)
                                            withContext(Dispatchers.Main) {
                                                if (reDetected != null) {
                                                    autoDetectedQuad = reDetected
                                                    pushHistory(reDetected)
                                                } else {
                                                    Toast.makeText(context, "Document edges couldn't be detected.", Toast.LENGTH_SHORT).show()
                                                }
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
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto Detect",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Rotate 90 degrees
                        IconButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90) % 360
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .size(44.dp)
                                .testTag("crop_rotate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate",
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
                                .weight(1.3f)
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

    DisposableEffect(Unit) {
        onDispose {
            rawBitmap?.recycle()
        }
    }
}

@Composable
private fun NudgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    val scope = rememberCoroutineScope()
    var isPressing by remember { mutableStateOf(false) }

    LaunchedEffect(isPressing) {
        if (isPressing) {
            while (isPressing) {
                onClick()
                delay(50)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
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
