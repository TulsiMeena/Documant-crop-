package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.ui.components.EmptyState
import com.example.ui.components.ScanovaOutlinedButton
import com.example.ui.components.ScanovaPrimaryButton
import com.example.ui.components.ScanovaTopBar
import com.example.ui.viewmodel.DocumentListViewModel
import com.example.util.IntentUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    viewModel: DocumentListViewModel,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDocumentClick: (ScannedDocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val documents by viewModel.filteredDocuments.collectAsState()

    var selectedDocForMenu by remember { mutableStateOf<ScannedDocumentEntity?>(null) }
    var selectedDocForRename by remember { mutableStateOf<ScannedDocumentEntity?>(null) }
    var renameTitleInput by remember { mutableStateOf("") }
    var selectedDocForDelete by remember { mutableStateOf<ScannedDocumentEntity?>(null) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("documents_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            ScanovaTopBar(
                title = "Documents",
                subtitle = "Manage & view saved scans",
                onSettingsClick = onSettingsClick
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = {
                        Text(
                            text = "Search documents...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("documents_search_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (documents.isEmpty()) {
                    EmptyState(
                        title = if (searchQuery.isBlank()) "No documents yet" else "No documents found.",
                        subtitle = if (searchQuery.isBlank()) "Scan your first document to get started." else "Try a different search query.",
                        actionButton = {
                            if (searchQuery.isBlank()) {
                                ScanovaPrimaryButton(
                                    text = "Scan Document",
                                    onClick = onScanClick,
                                    icon = Icons.Default.CameraAlt,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    testTag = "documents_empty_scan_button"
                                )
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("documents_list")
                    ) {
                        items(
                            items = documents,
                            key = { it.id }
                        ) { doc ->
                            DocumentListItem(
                                document = doc,
                                onClick = { onDocumentClick(doc) },
                                onMenuClick = { selectedDocForMenu = doc }
                            )
                        }
                    }
                }
            }
        }
    }

    // DOCUMENT OPTIONS BOTTOM SHEET
    if (selectedDocForMenu != null) {
        val doc = selectedDocForMenu!!
        ModalBottomSheet(
            onDismissRequest = { selectedDocForMenu = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"} • ${formatDate(doc.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Open PDF Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedDocForMenu = null
                            IntentUtils.openPdf(context, File(doc.pdfPath))
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Open PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Share PDF Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedDocForMenu = null
                            IntentUtils.sharePdf(context, File(doc.pdfPath), doc.title)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Share",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Rename Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedDocForMenu = null
                            renameTitleInput = doc.title
                            selectedDocForRename = doc
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Rename",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Delete Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedDocForMenu = null
                            selectedDocForDelete = doc
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // RENAME DIALOG
    if (selectedDocForRename != null) {
        val doc = selectedDocForRename!!
        AlertDialog(
            onDismissRequest = { selectedDocForRename = null },
            title = {
                Text(text = "Rename Document", fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = renameTitleInput,
                    onValueChange = { renameTitleInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                ScanovaPrimaryButton(
                    text = "Save",
                    onClick = {
                        val newTitle = renameTitleInput.trim()
                        selectedDocForRename = null
                        if (newTitle.isNotBlank()) {
                            viewModel.renameDocument(doc, newTitle)
                        }
                    }
                )
            },
            dismissButton = {
                ScanovaOutlinedButton(
                    text = "Cancel",
                    onClick = { selectedDocForRename = null }
                )
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    if (selectedDocForDelete != null) {
        val doc = selectedDocForDelete!!
        AlertDialog(
            onDismissRequest = { selectedDocForDelete = null },
            title = {
                Text(text = "Delete document?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Are you sure you want to delete '${doc.title}'? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDocForDelete = null
                        viewModel.deleteDocument(doc)
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDocForDelete = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

@Composable
fun DocumentListItem(
    document: ScannedDocumentEntity,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val thumbBmp = remember(document.thumbnailPath) {
        try {
            BitmapFactory.decodeFile(document.thumbnailPath)
        } catch (e: Exception) {
            null
        }
    }

    val fileSizeStr = remember(document.fileSizeBytes) {
        val bytes = document.fileSizeBytes
        when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(56.dp, 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBmp != null) {
                    Image(
                        bitmap = thumbBmp.asImageBitmap(),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"} • $fileSizeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDate(document.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}
