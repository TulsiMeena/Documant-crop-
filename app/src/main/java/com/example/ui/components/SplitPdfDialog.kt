package com.example.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun SplitPdfDialog(
    sourcePdfPath: String,
    documentTitle: String,
    onDismiss: () -> Unit,
    onSuccess: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<EditablePdfPage>() }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    var splitTitle by remember { mutableStateOf("${documentTitle}_extracted") }
    var isLoading by remember { mutableStateOf(true) }
    var isSplitting by remember { mutableStateOf(false) }

    var resultFile by remember { mutableStateOf<File?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sourcePdfPath) {
        withContext(Dispatchers.IO) {
            val loaded = PdfEditUtil.loadPagesFromPdf(context, File(sourcePdfPath))
            withContext(Dispatchers.Main) {
                pages.clear()
                pages.addAll(loaded)
                // Select page 0 by default
                if (loaded.isNotEmpty()) {
                    selectedIndices.add(0)
                }
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSplitting) onDismiss() },
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
                            text = "Split / Extract PDF",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select pages to extract into a new PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { if (!isSplitting) onDismiss() },
                        enabled = !isSplitting
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Quick Filters & Document Name
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(
                        value = splitTitle,
                        onValueChange = { splitTitle = it },
                        label = { Text("Output PDF Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedIndices.size == pages.size && pages.isNotEmpty(),
                            onClick = {
                                selectedIndices.clear()
                                selectedIndices.addAll(pages.indices)
                            },
                            label = { Text("Select All") }
                        )

                        FilterChip(
                            selected = selectedIndices.isEmpty(),
                            onClick = { selectedIndices.clear() },
                            label = { Text("Clear All") }
                        )

                        FilterChip(
                            selected = false,
                            onClick = {
                                selectedIndices.clear()
                                pages.indices.filter { (it + 1) % 2 != 0 }.forEach { selectedIndices.add(it) }
                            },
                            label = { Text("Odd Pages") }
                        )

                        FilterChip(
                            selected = false,
                            onClick = {
                                selectedIndices.clear()
                                pages.indices.filter { (it + 1) % 2 == 0 }.forEach { selectedIndices.add(it) }
                            },
                            label = { Text("Even Pages") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Page grid
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(pages) { index, item ->
                                val isSelected = selectedIndices.contains(index)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isSelected) selectedIndices.remove(index)
                                            else selectedIndices.add(index)
                                        }
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            bitmap = item.thumbnailBitmap.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                            contentScale = ContentScale.Fit
                                        )

                                        // Selection checkmark
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .align(Alignment.TopEnd),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Page number label at bottom
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(topStart = 8.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "Page ${index + 1}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
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
                            text = if (isSplitting) "Extracting..." else "Extract ${selectedIndices.size} Pages",
                            onClick = {
                                if (selectedIndices.isEmpty() || isSplitting) return@ScanovaPrimaryButton

                                isSplitting = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val sourceFile = File(sourcePdfPath)
                                        val safeName = splitTitle.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                        val outputFileName = if (safeName.endsWith(".pdf", ignoreCase = true)) safeName else "$safeName.pdf"
                                        val outDir = File(context.getExternalFilesDir(null), "SplitPdfs").apply { mkdirs() }
                                        val outFile = File(outDir, outputFileName)

                                        val sortedSelection = selectedIndices.sorted()
                                        val ok = PdfEditUtil.splitPdf(
                                            context = context,
                                            sourcePdf = sourceFile,
                                            selectedPageIndices = sortedSelection,
                                            outputFile = outFile
                                        )

                                        withContext(Dispatchers.Main) {
                                            isSplitting = false
                                            if (ok && outFile.exists() && outFile.length() > 0) {
                                                resultFile = outFile
                                                showSuccessDialog = true
                                                onSuccess(outFile)
                                            } else {
                                                Toast.makeText(context, "Failed to split PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isSplitting = false
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = selectedIndices.isNotEmpty() && !isSplitting,
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
                Text("Extracted PDF Created", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Selected pages have been saved into a new PDF:")
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
