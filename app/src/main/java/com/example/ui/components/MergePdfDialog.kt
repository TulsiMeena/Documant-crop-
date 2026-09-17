package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.util.IntentUtils
import com.example.util.PdfCompressor
import com.example.util.PdfEditUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class MergeFileItem(
    val file: File,
    val displayName: String,
    val sizeBytes: Long
)

@Composable
fun MergePdfDialog(
    initialDocuments: List<ScannedDocumentEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSuccess: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mergeList = remember {
        mutableStateListOf<MergeFileItem>().apply {
            initialDocuments.forEach { doc ->
                val f = File(doc.pdfPath)
                if (f.exists()) {
                    add(MergeFileItem(f, doc.title, f.length()))
                }
            }
        }
    }

    var mergedTitle by remember { mutableStateOf("Merged_Document") }
    var isMerging by remember { mutableStateOf(false) }

    var resultFile by remember { mutableStateOf<File?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // File picker for extra PDFs from phone
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        var fileName = "Document.pdf"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIdx >= 0) {
                                fileName = cursor.getString(nameIdx)
                            }
                        }

                        val tempFile = File(context.cacheDir, "import_merge_${System.currentTimeMillis()}_$fileName")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { out -> input.copyTo(out) }
                        }

                        if (tempFile.exists() && tempFile.length() > 0) {
                            withContext(Dispatchers.Main) {
                                mergeList.add(MergeFileItem(tempFile, fileName, tempFile.length()))
                            }
                        }
                    } catch (e: Exception) {
                        // ignore failed file
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isMerging) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
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
                            text = "Merge PDFs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Combine multiple PDF documents into one",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { if (!isMerging) onDismiss() },
                        enabled = !isMerging
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Output File Name
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(
                        value = mergedTitle,
                        onValueChange = { mergedTitle = it },
                        label = { Text("Merged File Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Files list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (mergeList.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No PDFs selected to merge",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add PDFs from Phone")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = mergeList,
                                key = { _, item -> item.file.absolutePath }
                            ) { index, item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Index badge
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = PdfCompressor.formatFileSize(item.sizeBytes),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Move Up
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val tmp = mergeList.removeAt(index)
                                                    mergeList.add(index - 1, tmp)
                                                }
                                            },
                                            enabled = index > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Move Up",
                                                tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.LightGray
                                            )
                                        }

                                        // Move Down
                                        IconButton(
                                            onClick = {
                                                if (index < mergeList.size - 1) {
                                                    val tmp = mergeList.removeAt(index)
                                                    mergeList.add(index + 1, tmp)
                                                }
                                            },
                                            enabled = index < mergeList.size - 1
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Move Down",
                                                tint = if (index < mergeList.size - 1) MaterialTheme.colorScheme.primary else Color.LightGray
                                            )
                                        }

                                        // Delete
                                        IconButton(
                                            onClick = { mergeList.removeAt(index) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            // Add More Button
                            item {
                                FilledTonalButton(
                                    onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add More PDFs from Phone")
                                }
                            }
                        }
                    }
                }

                // Footer
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
                            text = if (isMerging) "Merging..." else "Merge ${mergeList.size} PDFs",
                            onClick = {
                                if (mergeList.size < 2 || isMerging) return@ScanovaPrimaryButton

                                isMerging = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val safeName = mergedTitle.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                        val outputFileName = if (safeName.endsWith(".pdf", ignoreCase = true)) safeName else "$safeName.pdf"
                                        val outDir = File(context.getExternalFilesDir(null), "MergedPdfs").apply { mkdirs() }
                                        val outFile = File(outDir, outputFileName)

                                        val ok = PdfEditUtil.mergePdfs(
                                            context = context,
                                            sourcePdfs = mergeList.map { it.file },
                                            outputFile = outFile
                                        )

                                        withContext(Dispatchers.Main) {
                                            isMerging = false
                                            if (ok && outFile.exists() && outFile.length() > 0) {
                                                resultFile = outFile
                                                showSuccessDialog = true
                                                onSuccess(outFile)
                                            } else {
                                                Toast.makeText(context, "Failed to merge PDFs", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isMerging = false
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = mergeList.size >= 2 && !isMerging,
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }
    }

    if (showSuccessDialog && resultFile != null) {
        val file = resultFile!!
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onDismiss()
            },
            title = {
                Text("PDFs Merged Successfully", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Your merged PDF is ready:")
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
                        IntentUtils.sharePdf(context, file, file.nameWithoutExtension)
                        showSuccessDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Share")
                }
            }
        )
    }
}
