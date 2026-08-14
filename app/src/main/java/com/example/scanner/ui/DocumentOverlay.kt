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

enum class ScanMode(val label: String) {
    DOCUMENT("Document"),
    ID_CARD("ID Card"),
    BATCH("Batch Mode"),
    BOOK("Book / Spread")
}

@Composable
fun DocumentOverlay(
    quad: DocumentQuad?,
    status: DetectionStatus,
    scanMode: ScanMode = ScanMode.DOCUMENT,
    showGrid: Boolean = false,
    tiltPitch: Float = 0f,
    tiltRoll: Float = 0f,
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Grid Lines (if enabled)
            if (showGrid) {
                val gridColor = Color.White.copy(alpha = 0.22f)
                val gridStroke = 1.dp.toPx()
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), gridStroke)
                drawLine(gridColor, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), gridStroke)
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), gridStroke)
                drawLine(gridColor, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), gridStroke)
            }

            // 2. Specific Mode Framing Guides
            when (scanMode) {
                ScanMode.ID_CARD -> {
                    // ID Card bounding guide (standard CR80 1.586 aspect ratio)
                    val cardW = w * 0.84f
                    val cardH = cardW / 1.586f
                    val left = (w - cardW) / 2f
                    val top = (h - cardH) / 2f - 30.dp.toPx()

                    drawRoundRect(
                        color = Color(0xFF00E5D9).copy(alpha = 0.35f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(cardW, cardH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                ScanMode.BOOK -> {
                    // Dual page center spine divider
                    val centerX = w / 2f
                    val topY = h * 0.15f
                    val bottomY = h * 0.80f
                    drawLine(
                        color = Color(0xFF00E5D9).copy(alpha = 0.5f),
                        start = Offset(centerX, topY),
                        end = Offset(centerX, bottomY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                else -> Unit
            }

            // 3. Draw Realtime Document Detection Quad
            if (quad != null) {
                val tl = PointF(quad.topLeft.x * w, quad.topLeft.y * h)
                val tr = PointF(quad.topRight.x * w, quad.topRight.y * h)
                val br = PointF(quad.bottomRight.x * w, quad.bottomRight.y * h)
                val bl = PointF(quad.bottomLeft.x * w, quad.bottomLeft.y * h)

                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }

                val fillAlpha = when (status) {
                    DetectionStatus.READY -> 0.28f
                    DetectionStatus.STEADY -> 0.20f
                    else -> 0.12f
                }

                val strokeColor = when (status) {
                    DetectionStatus.READY -> Color(0xFF00E5D9) // Glowing cyan
                    DetectionStatus.STEADY -> Color(0xFF38BDF8) // Soft blue
                    else -> Color.White
                }

                drawPath(path = path, color = strokeColor.copy(alpha = fillAlpha))

                // Boundary Line Stroke
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Animated Scan Beam across detected quad
                val beamYTop = tl.y + (bl.y - tl.y) * scanProgress
                val beamYTopR = tr.y + (br.y - tr.y) * scanProgress
                val beamXLeft = tl.x + (bl.x - tl.x) * scanProgress
                val beamXRight = tr.x + (br.x - tr.x) * scanProgress

                drawLine(
                    color = Color(0xFF00E5D9),
                    start = Offset(beamXLeft, beamYTop),
                    end = Offset(beamXRight, beamYTopR),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Corner Markers
                val corners = listOf(tl, tr, br, bl)
                corners.forEach { corner ->
                    // Outer glowing ring
                    drawCircle(
                        color = strokeColor.copy(alpha = 0.45f),
                        radius = 14.dp.toPx(),
                        center = Offset(corner.x, corner.y)
                    )
                    // Inner solid dot
                    drawCircle(
                        color = Color.White,
                        radius = 6.5.dp.toPx(),
                        center = Offset(corner.x, corner.y)
                    )
                }
            } else if (scanMode == ScanMode.DOCUMENT || scanMode == ScanMode.BATCH) {
                // Subtle guide viewport corners
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

            // 4. Spirit Bubble Level Indicator (Center Crosshair)
            val isLeveled = kotlin.math.abs(tiltPitch) < 4f && kotlin.math.abs(tiltRoll) < 4f
            val bubbleCenter = Offset(
                x = (w / 2f + (tiltRoll * 4.dp.toPx())).coerceIn(w / 2f - 24.dp.toPx(), w / 2f + 24.dp.toPx()),
                y = (h * 0.38f + (tiltPitch * 4.dp.toPx())).coerceIn(h * 0.38f - 24.dp.toPx(), h * 0.38f + 24.dp.toPx())
            )

            // Outer spirit target circle
            drawCircle(
                color = if (isLeveled) Color(0xFF00E5D9).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.25f),
                radius = 20.dp.toPx(),
                center = Offset(w / 2f, h * 0.38f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Moving spirit bubble dot
            drawCircle(
                color = if (isLeveled) Color(0xFF00E5D9) else Color.White.copy(alpha = 0.6f),
                radius = 5.dp.toPx(),
                center = bubbleCenter
            )
        }

        // Status Pill Tag
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.70f))
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
                    text = when (scanMode) {
                        ScanMode.ID_CARD -> "Align ID Card in Frame"
                        ScanMode.BOOK -> "Align Center Spine"
                        else -> status.label
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

