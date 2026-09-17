package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.EditablePdfPage
import com.example.util.IntentUtils
import com.example.util.PdfEditUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun EditPdfDialog(
    sourcePdfPath: String,
    documentTitle: String,
    onDismiss: () -> Unit,
    onPdfUpdated: (File, Int, Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<EditablePdfPage>() }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }

    // Pick image to add as page
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val cacheFile = File(context.cacheDir, "added_page_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(cacheFile).use { out ->
                        inputStream?.copyTo(out)
                    }
                    val bmp = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            pages.add(
                                EditablePdfPage(
                                    originalPageIndex = -1,
                                    rotationDegrees = 0,
                                    thumbnailBitmap = bmp,
                                    sourceImagePath = cacheFile.absolutePath
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(sourcePdfPath) {
        withContext(Dispatchers.IO) {
            val loaded = PdfEditUtil.loadPagesFromPdf(context, File(sourcePdfPath))
            withContext(Dispatchers.Main) {
                pages.clear()
                pages.addAll(loaded)
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
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
                            text = "Edit PDF Pages",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pages.size} pages • Reorder, rotate or delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        enabled = !isSaving
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Page list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (pages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No pages in document", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = pages,
                                key = { _, item -> item.id }
                            ) { index, item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Page Index Badge
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Thumbnail with rotation applied
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp, 96.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = item.thumbnailBitmap.asImageBitmap(),
                                                contentDescription = "Page ${index + 1}",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .rotate(item.rotationDegrees.toFloat()),
                                                contentScale = ContentScale.Fit
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Controls: Move Up, Move Down, Rotate, Delete
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                // Move Up
                                                IconButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            val tmp = pages.removeAt(index)
                                                            pages.add(index - 1, tmp)
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
                                                        if (index < pages.size - 1) {
                                                            val tmp = pages.removeAt(index)
                                                            pages.add(index + 1, tmp)
                                                        }
                                                    },
                                                    enabled = index < pages.size - 1
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDownward,
                                                        contentDescription = "Move Down",
                                                        tint = if (index < pages.size - 1) MaterialTheme.colorScheme.primary else Color.LightGray
                                                    )
                                                }

                                                // Rotate 90
                                                IconButton(
                                                    onClick = {
                                                        val currentRot = item.rotationDegrees
                                                        val updated = item.copy(rotationDegrees = (currentRot + 90) % 360)
                                                        pages[index] = updated
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.RotateRight,
                                                        contentDescription = "Rotate 90°",
                                                        tint = MaterialTheme.colorScheme.secondary
                                                    )
                                                }

                                                // Delete Page
                                                IconButton(
                                                    onClick = {
                                                        if (pages.size > 1) {
                                                            pages.removeAt(index)
                                                        } else {
                                                            Toast.makeText(context, "Cannot delete only remaining page", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    enabled = pages.size > 1
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Page",
                                                        tint = if (pages.size > 1) MaterialTheme.colorScheme.error else Color.LightGray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Add page button
                            item {
                                FilledTonalButton(
                                    onClick = {
                                        pickMediaLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Page from Photos")
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
                            text = if (isSaving) "Saving..." else "Save Changes",
                            onClick = {
                                if (pages.isEmpty() || isSaving) return@ScanovaPrimaryButton

                                isSaving = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val sourceFile = File(sourcePdfPath)
                                        val tempOut = File(context.cacheDir, "edited_${System.currentTimeMillis()}.pdf")
                                        val ok = PdfEditUtil.rebuildPdf(
                                            context = context,
                                            sourcePdf = sourceFile,
                                            pages = pages.toList(),
                                            outputFile = tempOut
                                        )

                                        if (ok && tempOut.exists() && tempOut.length() > 0) {
                                            // Overwrite original file
                                            tempOut.copyTo(sourceFile, overwrite = true)
                                            tempOut.delete()

                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                                savedFile = sourceFile
                                                showSuccessDialog = true
                                                onPdfUpdated(sourceFile, pages.size, sourceFile.length())
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                                Toast.makeText(context, "Failed to rebuild PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isSaving = false
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving && pages.isNotEmpty(),
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }
    }

    if (showSuccessDialog && savedFile != null) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onDismiss()
            },
            title = {
                Text("PDF Updated Successfully", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Changes to \"$documentTitle\" have been saved. Total pages: ${pages.size}.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        IntentUtils.openPdf(context, savedFile!!)
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
                        showSuccessDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}
