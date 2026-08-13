package com.example.scanner.ui

import android.graphics.PointF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanner.model.DetectionStatus
import com.example.scanner.model.DocumentQuad
import com.example.ui.theme.ScanlineCyan

@Composable
fun DocumentOverlay(
    quad: DocumentQuad?,
    status: DetectionStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_beam")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("document_overlay")
    ) {
        // 1. Draw Document Boundary & Corner Handles Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (quad != null) {
                val tl = PointF(quad.topLeft.x * w, quad.topLeft.y * h)
                val tr = PointF(quad.topRight.x * w, quad.topRight.y * h)
                val br = PointF(quad.bottomRight.x * w, quad.bottomRight.y * h)
                val bl = PointF(quad.bottomLeft.x * w, quad.bottomLeft.y * h)

                // Fill translucent overlay inside quad
                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }

                val fillAlpha = when (status) {
                    DetectionStatus.READY -> 0.25f
                    DetectionStatus.STEADY -> 0.18f
                    else -> 0.10f
                }

                val strokeColor = when (status) {
                    DetectionStatus.READY -> Color(0xFF00E5D9) // Glowing cyan
                    DetectionStatus.STEADY -> Color(0xFF38BDF8) // Soft cyan
                    else -> Color.White
                }

                drawPath(path = path, color = strokeColor.copy(alpha = fillAlpha))

                // Boundary Line Stroke
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Animated Scan Beam across detected quad
                val beamYTop = tl.y + (bl.y - tl.y) * scanProgress
                val beamYTopR = tr.y + (br.y - tr.y) * scanProgress
                val beamXLeft = tl.x + (bl.x - tl.x) * scanProgress
                val beamXRight = tr.x + (br.x - tr.x) * scanProgress

                drawLine(
                    color = Color(0xFF00E5D9),
                    start = Offset(beamXLeft, beamYTop),
                    end = Offset(beamXRight, beamYTopR),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Corner Markers
                val corners = listOf(tl, tr, br, bl)
                corners.forEach { corner ->
                    // Outer glowing ring
                    drawCircle(
                        color = strokeColor.copy(alpha = 0.4f),
                        radius = 12.dp.toPx(),
                        center = Offset(corner.x, corner.y)
                    )
                    // Inner solid dot
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(corner.x, corner.y)
                    )
                }
            } else {
                // When looking for document, draw subtle guide viewport corners
                val guideMarginX = w * 0.10f
                val guideMarginY = h * 0.18f
                val guideW = w - (guideMarginX * 2)
                val guideH = h - (guideMarginY * 2)
                val cornerLength = 32.dp.toPx()
                val guideColor = Color.White.copy(alpha = 0.35f)
                val strokeW = 2.dp.toPx()

                // Top Left
                drawLine(guideColor, Offset(guideMarginX, guideMarginY), Offset(guideMarginX + cornerLength, guideMarginY), strokeW)
                drawLine(guideColor, Offset(guideMarginX, guideMarginY), Offset(guideMarginX, guideMarginY + cornerLength), strokeW)

                // Top Right
                drawLine(guideColor, Offset(guideMarginX + guideW, guideMarginY), Offset(guideMarginX + guideW - cornerLength, guideMarginY), strokeW)
                drawLine(guideColor, Offset(guideMarginX + guideW, guideMarginY), Offset(guideMarginX + guideW, guideMarginY + cornerLength), strokeW)

                // Bottom Right
                drawLine(guideColor, Offset(guideMarginX + guideW, guideMarginY + guideH), Offset(guideMarginX + guideW - cornerLength, guideMarginY + guideH), strokeW)
                drawLine(guideColor, Offset(guideMarginX + guideW, guideMarginY + guideH), Offset(guideMarginX + guideW, guideMarginY + guideH - cornerLength), strokeW)

                // Bottom Left
                drawLine(guideColor, Offset(guideMarginX, guideMarginY + guideH), Offset(guideMarginX + cornerLength, guideMarginY + guideH), strokeW)
                drawLine(guideColor, Offset(guideMarginX, guideMarginY + guideH), Offset(guideMarginX, guideMarginY + guideH - cornerLength), strokeW)
            }
        }

        // 2. Status Pill Tag
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("status_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = when (status) {
                    DetectionStatus.READY -> Color(0xFF00E5D9)
                    DetectionStatus.STEADY -> Color(0xFF38BDF8)
                    DetectionStatus.DETECTED -> Color(0xFFFFC107)
                    DetectionStatus.LOOKING -> Color.White.copy(alpha = 0.6f)
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
