package com.example.scanner.ui

import android.graphics.PointF
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.scanner.model.DocumentQuad
import com.example.ui.components.ScanovaOutlinedButton
import com.example.ui.components.ScanovaPrimaryButton
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CapturePreviewScreen(
    imagePath: String,
    quad: DocumentQuad?,
    onRetake: () -> Unit,
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(imagePath) {
        // Simulate quick image quality verification & quad analysis step
        delay(600)
        isProcessing = false
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("capture_preview_screen"),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(File(imagePath))
                    .crossfade(true)
                    .build()
            )

            // 1. Captured High-Res Image Display
            Image(
                painter = painter,
                contentDescription = "Captured Document",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("captured_image_view")
            )

            // 2. Detected Quad Overlay
            if (!isProcessing && quad != null) {
                DocumentOverlay(
                    quad = quad,
                    status = com.example.scanner.model.DetectionStatus.READY,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Top Header / Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("preview_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isProcessing) "Processing..." else "Document Captured",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.size(48.dp))
            }

            // 4. Center Loading Indicator during Processing
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing scan clarity...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 5. Bottom Action Controls
            if (!isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.80f))
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ScanovaOutlinedButton(
                        text = "Retake",
                        onClick = onRetake,
                        icon = Icons.Default.Refresh,
                        modifier = Modifier.weight(1f),
                        testTag = "preview_retake_button"
                    )

                    ScanovaPrimaryButton(
                        text = "Continue",
                        onClick = { onContinue(imagePath) },
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1f),
                        testTag = "preview_continue_button"
                    )
                }
            }
        }
    }
}
