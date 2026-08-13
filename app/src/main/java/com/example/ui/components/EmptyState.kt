package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp)
            .testTag("empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyDocumentGraphic(
            modifier = Modifier.size(110.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (actionButton != null) {
            Spacer(modifier = Modifier.height(24.dp))
            actionButton()
        }
    }
}

@Composable
fun EmptyDocumentGraphic(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(110.dp)) {
            val w = size.width
            val h = size.height

            // Background subtle sheet shadow
            val backPath = Path().apply {
                moveTo(w * 0.35f, h * 0.15f)
                lineTo(w * 0.80f, h * 0.15f)
                lineTo(w * 0.80f, h * 0.82f)
                lineTo(w * 0.35f, h * 0.82f)
                close()
            }
            drawPath(backPath, color = color.copy(alpha = 0.08f))

            // Main sheet
            val docLeft = w * 0.22f
            val docTop = h * 0.20f
            val docRight = w * 0.72f
            val docBottom = h * 0.88f
            val fold = w * 0.14f

            val docPath = Path().apply {
                moveTo(docLeft, docTop)
                lineTo(docRight - fold, docTop)
                lineTo(docRight, docTop + fold)
                lineTo(docRight, docBottom)
                lineTo(docLeft, docBottom)
                close()
            }
            drawPath(docPath, color = color.copy(alpha = 0.05f))
            drawPath(docPath, color = color.copy(alpha = 0.4f), style = Stroke(width = 3.dp.toPx()))

            // Fold corner
            val foldPath = Path().apply {
                moveTo(docRight - fold, docTop)
                lineTo(docRight - fold, docTop + fold)
                lineTo(docRight, docTop + fold)
            }
            drawPath(foldPath, color = color.copy(alpha = 0.4f), style = Stroke(width = 2.5.dp.toPx()))

            // Subtle dotted/dashed lines
            val lX1 = docLeft + (docRight - docLeft) * 0.22f
            val lX2 = docRight - (docRight - docLeft) * 0.22f
            val strokeW = 2.dp.toPx()

            drawLine(color.copy(alpha = 0.3f), Offset(lX1, h * 0.42f), Offset(lX2, h * 0.42f), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(color.copy(alpha = 0.3f), Offset(lX1, h * 0.54f), Offset(lX2, h * 0.54f), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(color.copy(alpha = 0.3f), Offset(lX1, h * 0.66f), Offset(lX1 + (lX2 - lX1) * 0.5f, h * 0.66f), strokeWidth = strokeW, cap = StrokeCap.Round)

            // Scanning corner accents
            val cLen = 14.dp.toPx()
            val strokeC = 2.5.dp.toPx()

            // Top-left scanner corner
            val tl = Path().apply {
                moveTo(w * 0.12f, h * 0.12f + cLen)
                lineTo(w * 0.12f, h * 0.12f)
                lineTo(w * 0.12f + cLen, h * 0.12f)
            }
            drawPath(tl, color = color, style = Stroke(width = strokeC, cap = StrokeCap.Round))

            // Bottom-right scanner corner
            val br = Path().apply {
                moveTo(w * 0.88f, h * 0.92f - cLen)
                lineTo(w * 0.88f, h * 0.92f)
                lineTo(w * 0.88f - cLen, h * 0.92f)
            }
            drawPath(br, color = color, style = Stroke(width = strokeC, cap = StrokeCap.Round))
        }
    }
}
