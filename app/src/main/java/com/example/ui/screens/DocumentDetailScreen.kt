package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.ui.components.ExportImageDialog
import com.example.ui.components.ScanovaOutlinedButton
import com.example.ui.components.ScanovaPrimaryButton
import com.example.ui.components.ScanovaTopBar
import com.example.ui.viewmodel.DocumentListViewModel
import com.example.util.IntentUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentDetailScreen(
    document: ScannedDocumentEntity,
    viewModel: DocumentListViewModel,
    onBack: () -> Unit,
    onOpenOcr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(document.title) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFileMissingDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val pdfFile = remember(document.pdfPath) { File(document.pdfPath) }
    val thumbnailBmp = remember(document.thumbnailPath) {
        try {
            BitmapFactory.decodeFile(document.thumbnailPath)
        } catch (e: Exception) {
            null
        }
    }

    val fileSizeFormatted = remember(document.fileSizeBytes) {
        val bytes = document.fileSizeBytes
        when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }

    fun openPdfFile() {
        if (!pdfFile.exists()) {
            showFileMissingDialog = true
        } else {
            IntentUtils.openPdf(context, pdfFile)
        }
    }

    fun sharePdfFile() {
        if (!pdfFile.exists()) {
            showFileMissingDialog = true
        } else {
            IntentUtils.sharePdf(context, pdfFile, document.title)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("document_detail_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScanovaTopBar(
                title = "Document Details",
                subtitle = document.title,
                onBackClick = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Document Large Thumbnail Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.04f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailBmp != null) {
                                Image(
                                    bitmap = thumbnailBmp.asImageBitmap(),
                                    contentDescription = document.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title & Metadata Section
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MetadataBadge(label = "Pages", value = "${document.pageCount}")
                        MetadataBadge(label = "Size", value = fileSizeFormatted)
                        MetadataBadge(
                            label = "Created",
                            value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(document.createdAt))
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // OCR Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Extracted Text (OCR)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                TextButton(onClick = onOpenOcr) {
                                    Text(
                                        text = if (document.ocrText.isNotBlank()) "View Text" else "Extract Text",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (document.ocrText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = document.ocrText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Primary Action: Open PDF
                    ScanovaPrimaryButton(
                        text = "Open PDF",
                        onClick = { openPdfFile() },
                        icon = Icons.Default.OpenInNew,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "detail_open_pdf_button"
                    )

                    // Save Image to Gallery Action
                    ScanovaOutlinedButton(
                        text = "Save Image to Gallery (Custom Size & px/in)",
                        onClick = { showExportDialog = true },
                        icon = Icons.Default.Download,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "detail_export_image_button"
                    )

                    // Secondary Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ScanovaOutlinedButton(
                            text = "Share",
                            onClick = { sharePdfFile() },
                            icon = Icons.Default.Share,
                            modifier = Modifier.weight(1f),
                            testTag = "detail_share_button"
                        )

                        ScanovaOutlinedButton(
                            text = "Rename",
                            onClick = {
                                renameInput = document.title
                                showRenameDialog = true
                            },
                            icon = Icons.Default.Edit,
                            modifier = Modifier.weight(1f),
                            testTag = "detail_rename_button"
                        )

                        ScanovaOutlinedButton(
                            text = "Delete",
                            onClick = { showDeleteDialog = true },
                            icon = Icons.Default.Delete,
                            modifier = Modifier.weight(1f),
                            testTag = "detail_delete_button"
                        )
                    }
                }
            }
        }
    }

    // Export Image Dialog (Custom dimensions, format JPG/PNG, compression KB limit)
    if (showExportDialog) {
        ExportImageDialog(
            sourceImagePath = if (File(document.pdfPath).exists()) document.pdfPath else document.thumbnailPath,
            documentTitle = document.title,
            onDismiss = { showExportDialog = false }
        )
    }

    // File Unavailable Dialog (Storage Audit requirement)
    if (showFileMissingDialog) {
        AlertDialog(
            onDismissRequest = { showFileMissingDialog = false },
            title = {
                Text(text = "File Unavailable", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "The PDF file could not be found on your device's storage. It may have been moved or deleted externally.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFileMissingDialog = false
                        viewModel.deleteDocument(document)
                        onBack()
                    }
                ) {
                    Text(text = "Remove Record", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFileMissingDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(text = "Rename Document", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                ScanovaPrimaryButton(
                    text = "Save",
                    onClick = {
                        showRenameDialog = false
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.renameDocument(document, trimmed)
                        }
                    }
                )
            },
            dismissButton = {
                ScanovaOutlinedButton(text = "Cancel", onClick = { showRenameDialog = false })
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "Delete Document?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Are you sure you want to delete '${document.title}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDocument(document)
                        onBack()
                    }
                ) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

@Composable
private fun MetadataBadge(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
