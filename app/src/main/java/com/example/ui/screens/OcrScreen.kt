package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.ui.components.EmptyState
import com.example.ui.components.ScanovaOutlinedButton
import com.example.ui.components.ScanovaPrimaryButton
import com.example.ui.components.ScanovaTopBar
import com.example.ui.viewmodel.DocumentListViewModel
import com.example.util.OcrProcessor
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OcrScreen(
    document: ScannedDocumentEntity,
    viewModel: DocumentListViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf(document.ocrText) }
    var hasError by remember { mutableStateOf(false) }

    fun runOcr() {
        scope.launch {
            isProcessing = true
            hasError = false
            try {
                val imageFile = File(document.thumbnailPath)
                val result = OcrProcessor.extractTextFromImage(context, imageFile)
                result.fold(
                    onSuccess = { text ->
                        extractedText = text
                        if (text.isNotBlank()) {
                            viewModel.saveOcrText(document, text)
                        }
                    },
                    onFailure = {
                        hasError = true
                        extractedText = ""
                    }
                )
            } catch (e: Exception) {
                hasError = true
                extractedText = ""
            } finally {
                isProcessing = false
            }
        }
    }

    LaunchedEffect(document.id) {
        if (extractedText.isBlank()) {
            runOcr()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("ocr_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScanovaTopBar(
                title = "Extracted Text",
                subtitle = document.title,
                onBackClick = onDone
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting text locally...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (extractedText.isBlank()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            EmptyState(
                                title = "No readable text found.",
                                subtitle = "Make sure the document contains clear printed text.",
                                actionButton = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ScanovaOutlinedButton(
                                            text = "Try Again",
                                            onClick = { runOcr() },
                                            icon = Icons.Default.Refresh,
                                            testTag = "ocr_try_again_button"
                                        )

                                        ScanovaPrimaryButton(
                                            text = "Done",
                                            onClick = onDone,
                                            testTag = "ocr_error_done_button"
                                        )
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Readable Scrollable Text Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "RECOGNIZED TEXT",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = extractedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("ocr_text_content")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Actions: Copy, Share, Done
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ScanovaOutlinedButton(
                                text = "Copy",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Extracted Text", extractedText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                icon = Icons.Default.ContentCopy,
                                modifier = Modifier.weight(1f),
                                testTag = "ocr_copy_button"
                            )

                            ScanovaOutlinedButton(
                                text = "Share",
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, extractedText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Extracted Text")
                                    context.startActivity(shareIntent)
                                },
                                icon = Icons.Default.Share,
                                modifier = Modifier.weight(1f),
                                testTag = "ocr_share_button"
                            )
                        }

                        ScanovaPrimaryButton(
                            text = "Done",
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "ocr_done_button"
                        )
                    }
                }
            }
        }
    }
}
