package com.example.scanner.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.scanner.DocumentSessionViewModel
import com.example.scanner.model.ScannedPage
import com.example.ui.components.ExportImageDialog
import com.example.ui.components.ScanovaOutlinedButton
import com.example.ui.components.ScanovaPrimaryButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSessionScreen(
    viewModel: DocumentSessionViewModel,
    onBack: () -> Unit,
    onAddPageCamera: () -> Unit,
    onAddPageGallery: () -> Unit,
    onPdfCreated: (ScannedDocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()

    var showAddPageBottomSheet by remember { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<ScannedPage?>(null) }
    var pageToPreview by remember { mutableStateOf<ScannedPage?>(null) }

    var showPdfConfigDialog by remember { mutableStateOf(false) }
    var pdfTitleInput by remember { mutableStateOf("") }
    var selectedPageSize by remember { mutableStateOf("Auto") }
    var pdfErrorMessage by remember { mutableStateOf<String?>(null) }
    var pageToExport by remember { mutableStateOf<ScannedPage?>(null) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("document_session_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("session_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pages",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${pages.size} ${if (pages.size == 1) "page" else "pages"} scanned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Add Page Compact Button in Header
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { showAddPageBottomSheet = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("header_add_page_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Page",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Page",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Page Thumbnails Grid
                if (pages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No pages added.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pages_grid")
                    ) {
                        itemsIndexed(
                            items = pages,
                            key = { _, item -> item.id }
                        ) { index, page ->
                            PageGridCard(
                                page = page,
                                index = index,
                                totalCount = pages.size,
                                onClick = { pageToPreview = page },
                                onDelete = {
                                    if (pages.size == 1) {
                                        pageToDelete = page
                                    } else {
                                        viewModel.deletePage(page.id)
                                    }
                                },
                                onMoveUp = { viewModel.movePageUp(index) },
                                onMoveDown = { viewModel.movePageDown(index) }
                            )
                        }
                    }
                }

                // Bottom Primary Actions Bar (Add Page | Create PDF)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ScanovaOutlinedButton(
                            text = "Add Page",
                            onClick = { showAddPageBottomSheet = true },
                            icon = Icons.Default.Add,
                            modifier = Modifier.weight(1f),
                            testTag = "bottom_add_page_button"
                        )

                        ScanovaPrimaryButton(
                            text = "Create PDF",
                            onClick = {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                pdfTitleInput = "Scan_$timestamp"
                                showPdfConfigDialog = true
                            },
                            icon = Icons.Default.PictureAsPdf,
                            enabled = pages.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            testTag = "bottom_create_pdf_button"
                        )
                    }
                }
            }

            // Progress Overlay for PDF Generation
            if (isGeneratingPdf) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(28.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Creating PDF...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Packaging ${pages.size} pages into high-quality PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 1. ADD PAGE BOTTOM SHEET
    if (showAddPageBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddPageBottomSheet = false },
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
                    text = "Add Another Page",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scan with Camera Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            showAddPageBottomSheet = false
                            onAddPageCamera()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Scan with Camera",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Capture a new page using live camera",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Import from Gallery Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            showAddPageBottomSheet = false
                            onAddPageGallery()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Gallery",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Import from Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select an existing photo from device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 2. DELETE LAST PAGE CONFIRMATION DIALOG
    if (pageToDelete != null) {
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = {
                Text(
                    text = "Delete this page?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = "This is the only page in your document session. Deleting it will cancel the scan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = pageToDelete
                        pageToDelete = null
                        if (p != null) {
                            viewModel.deletePage(p.id)
                            onBack()
                        }
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
                TextButton(onClick = { pageToDelete = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    // 3. PAGE LARGE PREVIEW DIALOG
    if (pageToPreview != null) {
        val page = pageToPreview!!
        Dialog(onDismissRequest = { pageToPreview = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page ${page.pageOrder}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { pageToPreview = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val bmp = remember(page.enhancedImagePath, page.rotationDegrees) {
                        try {
                            BitmapFactory.decodeFile(page.enhancedImagePath)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Page Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.rotatePage(page.id)
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.RotateRight,
                                    contentDescription = "Rotate",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Rotate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                pageToExport = page
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save to Gallery",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Save HD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                pageToPreview = null
                                if (pages.size == 1) {
                                    pageToDelete = page
                                } else {
                                    viewModel.deletePage(page.id)
                                }
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. CREATE PDF CONFIG DIALOG
    if (showPdfConfigDialog) {
        AlertDialog(
            onDismissRequest = { showPdfConfigDialog = false },
            title = {
                Text(
                    text = "Create Document PDF",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.colorScheme.onSurface.run { MaterialTheme.typography.titleLarge }
                )
            },
            text = {
                Column {
                    Text(
                        text = "Document Name",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = pdfTitleInput,
                        onValueChange = { pdfTitleInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Page Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Auto", "A4", "Letter").forEach { sizeOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { selectedPageSize = sizeOption }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedPageSize == sizeOption,
                                    onClick = { selectedPageSize = sizeOption },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = sizeOption,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedPageSize == sizeOption) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ScanovaPrimaryButton(
                    text = "Generate PDF",
                    onClick = {
                        showPdfConfigDialog = false
                        viewModel.createPdf(
                            context = context,
                            title = pdfTitleInput,
                            pageSizeOption = selectedPageSize,
                            onSuccess = { createdDoc ->
                                onPdfCreated(createdDoc)
                            },
                            onError = { err ->
                                pdfErrorMessage = err
                            }
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    testTag = "pdf_confirm_generate_button"
                )
            },
            dismissButton = {
                ScanovaOutlinedButton(
                    text = "Cancel",
                    onClick = { showPdfConfigDialog = false }
                )
            }
        )
    }

    // 5. ERROR DIALOG IF PDF CREATION FAILS
    if (pdfErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { pdfErrorMessage = null },
            title = {
                Text(text = "Couldn't create PDF.", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = pdfErrorMessage ?: "An unexpected error occurred while generating PDF.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pdfErrorMessage = null
                        showPdfConfigDialog = true
                    }
                ) {
                    Text(text = "Try Again", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pdfErrorMessage = null }) {
                    Text(text = "Back")
                }
            }
        )
    }

    if (pageToExport != null) {
        val page = pageToExport!!
        ExportImageDialog(
            sourceImagePath = page.enhancedImagePath,
            documentTitle = "Document_Page_${page.pageOrder}",
            onDismiss = { pageToExport = null }
        )
    }
}

@Composable
private fun PageGridCard(
    page: ScannedPage,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val bitmap = remember(page.enhancedImagePath, page.rotationDegrees) {
        try {
            BitmapFactory.decodeFile(page.enhancedImagePath)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column {
            // Page Image Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page ${page.pageOrder}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Delete Page Button (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Page",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Page Order Tag (Top Left)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Page ${page.pageOrder}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // Reorder Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Left/Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Right/Down",
                            tint = if (index < totalCount - 1) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = "${index + 1} of $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
