package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ScanlineCyan

@Composable
fun ScanovaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    animateScan: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = ScanlineCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_animation")
    val scanProgress by if (animateScan) {
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scan_line"
        )
    } else {
        androidx.compose.runtime.mutableFloatStateOf(0.5f)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val width = this.size.width
            val height = this.size.height

            val padding = width * 0.12f
            val cornerLength = width * 0.20f
            val strokeWidth = width * 0.045f

            val frameLeft = padding
            val frameTop = padding
            val frameRight = width - padding
            val frameBottom = height - padding

            // 1. Draw Scanning Frame Corners
            // Top-Left
            val topLeftPath = Path().apply {
                moveTo(frameLeft, frameTop + cornerLength)
                lineTo(frameLeft, frameTop)
                lineTo(frameLeft + cornerLength, frameTop)
            }
            drawPath(topLeftPath, color = accentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

            // Top-Right
            val topRightPath = Path().apply {
                moveTo(frameRight - cornerLength, frameTop)
                lineTo(frameRight, frameTop)
                lineTo(frameRight, frameTop + cornerLength)
            }
            drawPath(topRightPath, color = accentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

            // Bottom-Right
            val bottomRightPath = Path().apply {
                moveTo(frameRight, frameBottom - cornerLength)
                lineTo(frameRight, frameBottom)
                lineTo(frameRight - cornerLength, frameBottom)
            }
            drawPath(bottomRightPath, color = accentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

            // Bottom-Left
            val bottomLeftPath = Path().apply {
                moveTo(frameLeft + cornerLength, frameBottom)
                lineTo(frameLeft, frameBottom)
                lineTo(frameLeft, frameBottom - cornerLength)
            }
            drawPath(bottomLeftPath, color = accentColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

            // 2. Draw Center Document Page Outline
            val docLeft = width * 0.28f
            val docTop = height * 0.22f
            val docRight = width * 0.72f
            val docBottom = height * 0.78f
            val foldSize = width * 0.12f

            val docPath = Path().apply {
                moveTo(docLeft, docTop)
                lineTo(docRight - foldSize, docTop)
                lineTo(docRight, docTop + foldSize)
                lineTo(docRight, docBottom)
                lineTo(docLeft, docBottom)
                close()
            }
            drawPath(docPath, color = primaryColor.copy(alpha = 0.15f))
            drawPath(docPath, color = primaryColor, style = Stroke(width = strokeWidth * 0.7f))

            // Document fold line
            val foldPath = Path().apply {
                moveTo(docRight - foldSize, docTop)
                lineTo(docRight - foldSize, docTop + foldSize)
                lineTo(docRight, docTop + foldSize)
            }
            drawPath(foldPath, color = primaryColor, style = Stroke(width = strokeWidth * 0.6f))

            // Document text line placeholders
            val lineLeft = docLeft + (docRight - docLeft) * 0.20f
            val lineRight = docRight - (docRight - docLeft) * 0.20f
            val line1Y = docTop + (docBottom - docTop) * 0.38f
            val line2Y = docTop + (docBottom - docTop) * 0.52f
            val line3Y = docTop + (docBottom - docTop) * 0.66f

            drawLine(primaryColor, Offset(lineLeft, line1Y), Offset(lineRight, line1Y), strokeWidth = strokeWidth * 0.4f, cap = StrokeCap.Round)
            drawLine(primaryColor, Offset(lineLeft, line2Y), Offset(lineRight, line2Y), strokeWidth = strokeWidth * 0.4f, cap = StrokeCap.Round)
            drawLine(primaryColor, Offset(lineLeft, line3Y), Offset(lineLeft + (lineRight - lineLeft) * 0.6f, line3Y), strokeWidth = strokeWidth * 0.4f, cap = StrokeCap.Round)

            // 3. Draw Scanning Beam Light
            if (animateScan) {
                val scanY = height * scanProgress
                drawLine(
                    color = accentColor,
                    start = Offset(frameLeft - width * 0.05f, scanY),
                    end = Offset(frameRight + width * 0.05f, scanY),
                    strokeWidth = strokeWidth * 0.8f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
