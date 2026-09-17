package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.IntentUtils
import com.example.util.SignatureOptions
import com.example.util.SignaturePlacement
import com.example.util.WatermarkOptions
import com.example.util.WatermarkPosition
import com.example.util.WatermarkSignatureUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkSignatureDialog(
    sourcePdfPath: String,
    documentTitle: String,
    onDismiss: () -> Unit,
    onSuccess: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Watermark, 1: Signature

    // Watermark State
    var enableWatermark by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkPos by remember { mutableStateOf(WatermarkPosition.CENTER_DIAGONAL) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.25f) }
    var watermarkColor by remember { mutableIntStateOf(AndroidColor.GRAY) }

    // Signature State
    var enableSignature by remember { mutableStateOf(false) }
    val signaturePaths = remember { mutableStateListOf<List<Offset>>() }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var signatureInkColor by remember { mutableIntStateOf(AndroidColor.parseColor("#0D47A1")) } // Navy Blue
    var signaturePlacement by remember { mutableStateOf(SignaturePlacement.BOTTOM_RIGHT) }
    var signatureAllPages by remember { mutableStateOf(true) }

    var isProcessing by remember { mutableStateOf(false) }
    var processedFile by remember { mutableStateOf<File?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val presetTexts = listOf("CONFIDENTIAL", "APPROVED", "ORIGINAL", "COPY", "FOR VERIFICATION ONLY", "DRAFT")

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxSize(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Watermark & Signature",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = documentTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { if (!isProcessing) onDismiss() },
                        enabled = !isProcessing
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FormatColorText,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watermark", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Digital Signature", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                // Content body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedTab == 0) {
                        // Watermark Settings
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Watermark Text",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = {
                                    watermarkText = it
                                    enableWatermark = it.isNotBlank()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter watermark text") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Preset Chips
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presetTexts.forEach { preset ->
                                    FilterChip(
                                        selected = watermarkText == preset,
                                        onClick = {
                                            watermarkText = preset
                                            enableWatermark = true
                                        },
                                        label = { Text(preset) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Position & Angle",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = watermarkPos == WatermarkPosition.CENTER_DIAGONAL,
                                    onClick = { watermarkPos = WatermarkPosition.CENTER_DIAGONAL },
                                    label = { Text("Diagonal") }
                                )
                                FilterChip(
                                    selected = watermarkPos == WatermarkPosition.CENTER_HORIZONTAL,
                                    onClick = { watermarkPos = WatermarkPosition.CENTER_HORIZONTAL },
                                    label = { Text("Horizontal") }
                                )
                                FilterChip(
                                    selected = watermarkPos == WatermarkPosition.TOP_BANNER,
                                    onClick = { watermarkPos = WatermarkPosition.TOP_BANNER },
                                    label = { Text("Top Banner") }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Opacity Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Opacity",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${(watermarkOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = watermarkOpacity,
                                onValueChange = { watermarkOpacity = it },
                                valueRange = 0.1f..0.8f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Color selection
                            Text(
                                text = "Watermark Color",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val colors = listOf(
                                    Pair(AndroidColor.GRAY, "Gray"),
                                    Pair(AndroidColor.RED, "Red"),
                                    Pair(AndroidColor.BLUE, "Blue"),
                                    Pair(AndroidColor.parseColor("#2E7D32"), "Green"),
                                    Pair(AndroidColor.BLACK, "Black")
                                )
                                colors.forEach { (colorVal, name) ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorVal))
                                            .border(
                                                width = if (watermarkColor == colorVal) 3.dp else 1.dp,
                                                color = if (watermarkColor == colorVal) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable { watermarkColor = colorVal },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (watermarkColor == colorVal) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = name,
                                                tint = if (colorVal == AndroidColor.BLACK) Color.White else Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Digital Signature Pad
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Draw Your Signature",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                TextButton(
                                    onClick = {
                                        signaturePaths.clear()
                                        currentPath = emptyList()
                                        enableSignature = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Pad")
                                }
                            }

                            // Interactive Signature Drawing Area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPath = listOf(offset)
                                                enableSignature = true
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentPath = currentPath + change.position
                                            },
                                            onDragEnd = {
                                                if (currentPath.isNotEmpty()) {
                                                    signaturePaths.add(currentPath)
                                                    currentPath = emptyList()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val drawInk = Color(signatureInkColor)
                                    // Draw completed paths
                                    signaturePaths.forEach { points ->
                                        if (points.size > 1) {
                                            val path = Path().apply {
                                                moveTo(points.first().x, points.first().y)
                                                for (i in 1 until points.size) {
                                                    lineTo(points[i].x, points[i].y)
                                                }
                                            }
                                            drawPath(
                                                path = path,
                                                color = drawInk,
                                                style = Stroke(
                                                    width = 4.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }

                                    // Draw current path in progress
                                    if (currentPath.size > 1) {
                                        val path = Path().apply {
                                            moveTo(currentPath.first().x, currentPath.first().y)
                                            for (i in 1 until currentPath.size) {
                                                lineTo(currentPath[i].x, currentPath[i].y)
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = drawInk,
                                            style = Stroke(
                                                width = 4.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }

                                if (signaturePaths.isEmpty() && currentPath.isEmpty()) {
                                    Text(
                                        text = "Sign here with your finger",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.LightGray,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            // Ink Color Selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Ink Color:", style = MaterialTheme.typography.bodyMedium)
                                FilterChip(
                                    selected = signatureInkColor == AndroidColor.parseColor("#0D47A1"),
                                    onClick = { signatureInkColor = AndroidColor.parseColor("#0D47A1") },
                                    label = { Text("Navy Blue") }
                                )
                                FilterChip(
                                    selected = signatureInkColor == AndroidColor.BLACK,
                                    onClick = { signatureInkColor = AndroidColor.BLACK },
                                    label = { Text("Black") }
                                )
                            }

                            // Placement Selection
                            Text(
                                text = "Signature Placement",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = signaturePlacement == SignaturePlacement.BOTTOM_RIGHT,
                                    onClick = { signaturePlacement = SignaturePlacement.BOTTOM_RIGHT },
                                    label = { Text("Bottom Right") }
                                )
                                FilterChip(
                                    selected = signaturePlacement == SignaturePlacement.BOTTOM_LEFT,
                                    onClick = { signaturePlacement = SignaturePlacement.BOTTOM_LEFT },
                                    label = { Text("Bottom Left") }
                                )
                                FilterChip(
                                    selected = signaturePlacement == SignaturePlacement.BOTTOM_CENTER,
                                    onClick = { signaturePlacement = SignaturePlacement.BOTTOM_CENTER },
                                    label = { Text("Bottom Center") }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = signatureAllPages,
                                    onClick = { signatureAllPages = true },
                                    label = { Text("All Pages") }
                                )
                                FilterChip(
                                    selected = !signatureAllPages,
                                    onClick = { signatureAllPages = false },
                                    label = { Text("Last Page Only") }
                                )
                            }
                        }
                    }
                }

                // Action Footer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ScanovaOutlinedButton(
                            text = "Cancel",
                            onClick = { onDismiss() },
                            modifier = Modifier.weight(1f)
                        )

                        ScanovaPrimaryButton(
                            text = if (isProcessing) "Applying..." else "Apply & Save PDF",
                            onClick = {
                                if (isProcessing) return@ScanovaPrimaryButton

                                isProcessing = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val sourceFile = File(sourcePdfPath)
                                        val outputFileName = "${sourceFile.nameWithoutExtension}_stamped.pdf"
                                        val downloadsDir = File(context.getExternalFilesDir(null), "StampedPdfs").apply { mkdirs() }
                                        val outputFile = File(downloadsDir, outputFileName)

                                        val watermarkOpts = if (enableWatermark && watermarkText.isNotBlank()) {
                                            WatermarkOptions(
                                                text = watermarkText.trim(),
                                                position = watermarkPos,
                                                opacity = watermarkOpacity,
                                                color = watermarkColor
                                            )
                                        } else null

                                        // Render signature paths to Bitmap if present
                                        val signatureOpts = if (enableSignature && signaturePaths.isNotEmpty()) {
                                            val bmpW = 400
                                            val bmpH = 200
                                            val sigBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                            val canvas = Canvas(sigBitmap)
                                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                color = signatureInkColor
                                                strokeWidth = 6f
                                                style = Paint.Style.STROKE
                                                strokeCap = Paint.Cap.ROUND
                                                strokeJoin = Paint.Join.ROUND
                                            }

                                            // Scale paths to bitmap
                                            for (points in signaturePaths) {
                                                if (points.size > 1) {
                                                    val path = AndroidPath()
                                                    path.moveTo(points.first().x, points.first().y)
                                                    for (pt in points.drop(1)) {
                                                        path.lineTo(pt.x, pt.y)
                                                    }
                                                    canvas.drawPath(path, paint)
                                                }
                                            }

                                            SignatureOptions(
                                                signatureBitmap = sigBitmap,
                                                placement = signaturePlacement,
                                                applyToAllPages = signatureAllPages,
                                                targetPageIndex = if (signatureAllPages) 0 else -1
                                            )
                                        } else null

                                        val success = WatermarkSignatureUtil.applyWatermarkAndSignature(
                                            context = context,
                                            sourcePdf = sourceFile,
                                            outputPdf = outputFile,
                                            watermark = watermarkOpts,
                                            signature = signatureOpts
                                        )

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            if (success) {
                                                processedFile = outputFile
                                                showSuccessDialog = true
                                                onSuccess(outputFile)
                                            } else {
                                                Toast.makeText(context, "Failed to apply watermark/signature", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing && ((enableWatermark && watermarkText.isNotBlank()) || (enableSignature && signaturePaths.isNotEmpty())),
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }
    }

    // Success Dialog
    if (showSuccessDialog && processedFile != null) {
        val file = processedFile!!
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onDismiss()
            },
            title = {
                Text("Watermark & Signature Applied!", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Your stamped document is ready:")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        IntentUtils.openPdf(context, file)
                        showSuccessDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Open PDF")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        IntentUtils.sharePdf(context, file, documentTitle)
                        showSuccessDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Share PDF")
                }
            }
        )
    }
}
