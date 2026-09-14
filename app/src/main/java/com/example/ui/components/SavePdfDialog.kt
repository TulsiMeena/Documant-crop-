package com.example.ui.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.util.IntentUtils
import com.example.util.PdfCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PdfTargetSizePreset(val label: String, val kb: Int?, val description: String) {
    ORIGINAL("Original", null, "Full quality, original uncompressed resolution."),
    TWO_MB("2 MB", 2048, "High quality for legal docs, submissions & portfolios."),
    ONE_MB("1 MB", 1024, "Standard size for email attachments & corporate portals."),
    FIVE_HUNDRED_KB("500 KB", 500, "Ideal for government exams, job portals & admission forms."),
    CUSTOM("Custom", -1, "Enter your exact custom target size limit.")
}

@Composable
fun SavePdfDialog(
    sourcePdfPath: String,
    documentTitle: String,
    onDismiss: () -> Unit,
    onSavedSuccessfully: (PdfCompressor.CompressResult) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var titleInput by remember { mutableStateOf(documentTitle) }
    var selectedPreset by remember { mutableStateOf(PdfTargetSizePreset.FIVE_HUNDRED_KB) }

    var customSizeInput by remember { mutableStateOf("300") }
    var customUnitIsMb by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Compressing & Saving PDF...") }
    var savedResult by remember { mutableStateOf<PdfCompressor.CompressResult?>(null) }

    val sourceFile = remember(sourcePdfPath) { File(sourcePdfPath) }
    val originalSizeBytes = if (sourceFile.exists()) sourceFile.length() else 0L

    // Target KB calculation
    val effectiveTargetKb: Int? = when (selectedPreset) {
        PdfTargetSizePreset.ORIGINAL -> null
        PdfTargetSizePreset.TWO_MB -> 2048
        PdfTargetSizePreset.ONE_MB -> 1024
        PdfTargetSizePreset.FIVE_HUNDRED_KB -> 500
        PdfTargetSizePreset.CUSTOM -> {
            val num = customSizeInput.trim().toFloatOrNull() ?: 500f
            if (customUnitIsMb) {
                (num * 1024).toInt().coerceAtLeast(50)
            } else {
                num.toInt().coerceAtLeast(50)
            }
        }
    }

    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_pdf_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Save PDF to Phone",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Choose size limit & save to Downloads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!isProcessing) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (savedResult != null) {
                    // Success View
                    val res = savedResult!!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "PDF Saved to Phone!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "File size: ${PdfCompressor.formatFileSize(res.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = res.displayPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ScanovaOutlinedButton(
                            text = "Share",
                            onClick = {
                                res.file?.let { file ->
                                    IntentUtils.sharePdf(context, file, titleInput)
                                }
                            },
                            icon = Icons.Default.Share,
                            modifier = Modifier.weight(1f),
                            testTag = "save_pdf_share_after_save"
                        )

                        if (res.file != null) {
                            ScanovaPrimaryButton(
                                text = "Open PDF",
                                onClick = {
                                    IntentUtils.openPdf(context, res.file)
                                    onDismiss()
                                },
                                icon = Icons.Default.OpenInNew,
                                modifier = Modifier.weight(1f),
                                testTag = "save_pdf_open_after_save"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ScanovaOutlinedButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "save_pdf_done_button"
                    )

                } else {
                    // Document Title Field
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("File Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_pdf_title_input"),
                        singleLine = true,
                        enabled = !isProcessing
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Current File Size Info Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current PDF Size:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = PdfCompressor.formatFileSize(originalSizeBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Target Size Label
                    Text(
                        text = "Choose Target PDF Size",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Size Preset Grid
                    val presets = listOf(
                        PdfTargetSizePreset.FIVE_HUNDRED_KB,
                        PdfTargetSizePreset.ONE_MB,
                        PdfTargetSizePreset.TWO_MB,
                        PdfTargetSizePreset.ORIGINAL,
                        PdfTargetSizePreset.CUSTOM
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            val isSelected = selectedPreset == preset
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable(enabled = !isProcessing) { selectedPreset = preset }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = preset.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Custom Size Input row
                    if (selectedPreset == PdfTargetSizePreset.CUSTOM) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customSizeInput,
                                onValueChange = { customSizeInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("Max Target Limit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_pdf_custom_size_input"),
                                singleLine = true,
                                enabled = !isProcessing
                            )

                            // Unit toggle (KB / MB)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!customUnitIsMb) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { customUnitIsMb = false }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "KB",
                                        color = if (!customUnitIsMb) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (customUnitIsMb) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { customUnitIsMb = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "MB",
                                        color = if (customUnitIsMb) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isProcessing) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = processingMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Primary Actions: Save to Phone | Share
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScanovaPrimaryButton(
                                text = "Save to Phone (Downloads)",
                                onClick = {
                                    if (!sourceFile.exists()) {
                                        Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
                                        return@ScanovaPrimaryButton
                                    }

                                    isProcessing = true
                                    processingMessage = "Compressing & saving PDF to Downloads..."

                                    scope.launch(Dispatchers.IO) {
                                        val result = PdfCompressor.savePdfToPhone(
                                            context = context,
                                            sourcePdf = sourceFile,
                                            documentTitle = titleInput,
                                            targetMaxKb = effectiveTargetKb
                                        )

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            if (result.success) {
                                                savedResult = result
                                                Toast.makeText(
                                                    context,
                                                    "PDF saved to Downloads (${PdfCompressor.formatFileSize(result.fileSizeBytes)})",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                onSavedSuccessfully(result)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    result.errorMessage ?: "Failed to save PDF",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                icon = Icons.Default.Download,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "save_pdf_confirm_button"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ScanovaOutlinedButton(
                                    text = "Share PDF",
                                    onClick = {
                                        if (!sourceFile.exists()) {
                                            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
                                            return@ScanovaOutlinedButton
                                        }

                                        isProcessing = true
                                        processingMessage = "Preparing PDF to share..."

                                        scope.launch(Dispatchers.IO) {
                                            val compressed = PdfCompressor.compressPdf(
                                                context = context,
                                                sourcePdf = sourceFile,
                                                targetMaxKb = effectiveTargetKb
                                            )

                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                IntentUtils.sharePdf(context, compressed, titleInput)
                                                onDismiss()
                                            }
                                        }
                                    },
                                    icon = Icons.Default.Share,
                                    modifier = Modifier.weight(1f),
                                    testTag = "save_pdf_share_button"
                                )

                                ScanovaOutlinedButton(
                                    text = "Cancel",
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    testTag = "save_pdf_cancel_button"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
