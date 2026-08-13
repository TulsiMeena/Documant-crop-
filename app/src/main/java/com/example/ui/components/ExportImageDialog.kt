package com.example.ui.components

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.util.ImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportImageDialog(
    sourceImagePath: String,
    documentTitle: String,
    onDismiss: () -> Unit,
    onExportSuccess: (ImageExporter.ExportResult) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf(ImageExporter.ImageFormat.JPG) }
    var selectedUnit by remember { mutableStateOf(ImageExporter.DimensionUnit.ORIGINAL) }

    // Source image dimensions
    var origWidthPx by remember { mutableIntStateOf(1080) }
    var origHeightPx by remember { mutableIntStateOf(1920) }

    var widthPxText by remember { mutableStateOf("1080") }
    var heightPxText by remember { mutableStateOf("1920") }

    var widthInchesText by remember { mutableStateOf("3.6") }
    var heightInchesText by remember { mutableStateOf("6.4") }
    var selectedDpi by remember { mutableIntStateOf(300) }

    // Target KB Preset Selection: null = Original, 30, 50, 100, 500, -1 = Custom
    var selectedKbPreset by remember { mutableStateOf<Int?>(null) }
    var customKbText by remember { mutableStateOf("100") }

    var isProcessing by remember { mutableStateOf(false) }

    // Read original image dimensions
    LaunchedEffect(sourceImagePath) {
        val file = File(sourceImagePath)
        if (file.exists()) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceImagePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                origWidthPx = opts.outWidth
                origHeightPx = opts.outHeight
                widthPxText = opts.outWidth.toString()
                heightPxText = opts.outHeight.toString()

                val wIn = String.format("%.1f", opts.outWidth.toFloat() / 300f)
                val hIn = String.format("%.1f", opts.outHeight.toFloat() / 300f)
                widthInchesText = wIn
                heightInchesText = hIn
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("export_image_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Save to Gallery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Custom dimensions & size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Format Selection (JPG / PNG)
                Text(
                    text = "File Format",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        ImageExporter.ImageFormat.JPG,
                        ImageExporter.ImageFormat.PNG
                    ).forEach { fmt ->
                        val selected = selectedFormat == fmt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedFormat = fmt }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fmt.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Dimensions & Unit Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dimensions",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ImageExporter.DimensionUnit.entries.forEach { unit ->
                        val selected = selectedUnit == unit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedUnit = unit }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when(unit) {
                                    ImageExporter.DimensionUnit.ORIGINAL -> "Original"
                                    ImageExporter.DimensionUnit.PIXELS -> "Pixels (px)"
                                    ImageExporter.DimensionUnit.INCHES -> "Inches (in)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Dimension Input Fields
                when (selectedUnit) {
                    ImageExporter.DimensionUnit.ORIGINAL -> {
                        Text(
                            text = "Original resolution: ${origWidthPx} × ${origHeightPx} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    ImageExporter.DimensionUnit.PIXELS -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = widthPxText,
                                onValueChange = { widthPxText = it.filter { c -> c.isDigit() } },
                                label = { Text("Width (px)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Text("×", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = heightPxText,
                                onValueChange = { heightPxText = it.filter { c -> c.isDigit() } },
                                label = { Text("Height (px)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    ImageExporter.DimensionUnit.INCHES -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = widthInchesText,
                                onValueChange = { widthInchesText = it },
                                label = { Text("Width (in)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Text("×", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = heightInchesText,
                                onValueChange = { heightInchesText = it },
                                label = { Text("Height (in)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "DPI:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(150, 300, 600).forEach { dpiVal ->
                                val selDpi = selectedDpi == dpiVal
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selDpi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { selectedDpi = dpiVal }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$dpiVal DPI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selDpi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Target File Size Compression (30 KB, 50 KB, 100 KB, 500 KB, Custom)
                Text(
                    text = "Target File Size (KB)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("Max Quality", null),
                            Pair("30 KB", 30),
                            Pair("50 KB", 50)
                        ).forEach { (lbl, kbVal) ->
                            val selected = selectedKbPreset == kbVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedKbPreset = kbVal }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lbl,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("100 KB", 100),
                            Pair("500 KB", 500),
                            Pair("Custom", -1)
                        ).forEach { (lbl, kbVal) ->
                            val selected = selectedKbPreset == kbVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedKbPreset = kbVal }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lbl,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (selectedKbPreset == -1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customKbText,
                            onValueChange = { customKbText = it.filter { c -> c.isDigit() } },
                            label = { Text("Custom Target KB") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save to Gallery Action Button
                ScanovaPrimaryButton(
                    text = if (isProcessing) "Saving Image..." else "Save to Gallery",
                    onClick = {
                        if (isProcessing) return@ScanovaPrimaryButton
                        isProcessing = true

                        scope.launch(Dispatchers.IO) {
                            val targetKb = when (selectedKbPreset) {
                                null -> null
                                -1 -> customKbText.toIntOrNull()?.coerceAtLeast(10)
                                else -> selectedKbPreset
                            }

                            val customW = widthPxText.toIntOrNull()
                            val customH = heightPxText.toIntOrNull()

                            val customWin = widthInchesText.toFloatOrNull()
                            val customHin = heightInchesText.toFloatOrNull()

                            val options = ImageExporter.ExportOptions(
                                format = selectedFormat,
                                dimensionUnit = selectedUnit,
                                customWidthPx = customW,
                                customHeightPx = customH,
                                customWidthInches = customWin,
                                customHeightInches = customHin,
                                dpi = selectedDpi,
                                targetSizeKb = targetKb,
                                saveToGallery = true
                            )

                            val result = ImageExporter.exportAndSave(
                                context = context,
                                sourceImagePath = sourceImagePath,
                                documentTitle = documentTitle,
                                options = options
                            )

                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                if (result != null) {
                                    val sizeKb = result.fileSizeBytes / 1024
                                    Toast.makeText(
                                        context,
                                        "Saved to Gallery! (${result.widthPx}x${result.heightPx}px, ${sizeKb} KB)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onExportSuccess(result)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to save image.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    icon = if (!isProcessing) Icons.Default.Download else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_save_button")
                )
            }
        }
    }
}
