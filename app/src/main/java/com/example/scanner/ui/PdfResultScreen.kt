package com.example.scanner.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.ui.components.ExportImageDialog
import com.example.ui.components.ScanovaOutlinedButton
import com.example.ui.components.ScanovaPrimaryButton
import com.example.util.IntentUtils
import java.io.File

@Composable
fun PdfResultScreen(
    document: ScannedDocumentEntity,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }

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

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_result_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Success Header Badge & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PDF Created Successfully!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Saved locally on your device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Document Preview & Details Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail Viewport
                    Box(
                        modifier = Modifier
                            .size(90.dp, 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnailBmp != null) {
                            Image(
                                bitmap = thumbnailBmp.asImageBitmap(),
                                contentDescription = document.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Size: $fileSizeFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Primary Action Buttons (Open PDF | Share | Save to Gallery | Done)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScanovaOutlinedButton(
                        text = "Open PDF",
                        onClick = {
                            IntentUtils.openPdf(context, File(document.pdfPath))
                        },
                        icon = Icons.Default.OpenInNew,
                        modifier = Modifier.weight(1f),
                        testTag = "pdf_result_open_button"
                    )

                    ScanovaOutlinedButton(
                        text = "Share",
                        onClick = {
                            IntentUtils.sharePdf(context, File(document.pdfPath), document.title)
                        },
                        icon = Icons.Default.Share,
                        modifier = Modifier.weight(1f),
                        testTag = "pdf_result_share_button"
                    )
                }

                ScanovaOutlinedButton(
                    text = "Save Image to Gallery (JPG/PNG)",
                    onClick = { showExportDialog = true },
                    icon = Icons.Default.Download,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pdf_result_export_image_button"
                )

                ScanovaPrimaryButton(
                    text = "Done",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pdf_result_done_button"
                )
            }
        }
    }

    if (showExportDialog) {
        ExportImageDialog(
            sourceImagePath = if (File(document.thumbnailPath).exists()) document.thumbnailPath else document.pdfPath,
            documentTitle = document.title,
            onDismiss = { showExportDialog = false }
        )
    }
}
