package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ScanlineCyan

/**
 * Illustration 1: Clean paper document resting inside a camera scan frame.
 */
@Composable
fun OnboardingIllustration1(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = ScanlineCyan
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp, 190.dp)) {
                val w = size.width
                val h = size.height

                // Camera Scanner Frame Corners
                val fL = w * 0.10f
                val fT = h * 0.10f
                val fR = w * 0.90f
                val fB = h * 0.90f
                val cLen = w * 0.16f
                val strokeC = 3.5.dp.toPx()

                // TL
                drawPath(
                    Path().apply {
                        moveTo(fL, fT + cLen)
                        lineTo(fL, fT)
                        lineTo(fL + cLen, fT)
                    },
                    color = accentColor,
                    style = Stroke(width = strokeC, cap = StrokeCap.Round)
                )
                // TR
                drawPath(
                    Path().apply {
                        moveTo(fR - cLen, fT)
                        lineTo(fR, fT)
                        lineTo(fR, fT + cLen)
                    },
                    color = accentColor,
                    style = Stroke(width = strokeC, cap = StrokeCap.Round)
                )
                // BR
                drawPath(
                    Path().apply {
                        moveTo(fR, fB - cLen)
                        lineTo(fR, fB)
                        lineTo(fR - cLen, fB)
                    },
                    color = accentColor,
                    style = Stroke(width = strokeC, cap = StrokeCap.Round)
                )
                // BL
                drawPath(
                    Path().apply {
                        moveTo(fL + cLen, fB)
                        lineTo(fL, fB)
                        lineTo(fL, fB - cLen)
                    },
                    color = accentColor,
                    style = Stroke(width = strokeC, cap = StrokeCap.Round)
                )

                // Central White Paper Sheet
                val dL = w * 0.24f
                val dT = h * 0.20f
                val dR = w * 0.76f
                val dB = h * 0.80f
                val fold = w * 0.12f

                val paperPath = Path().apply {
                    moveTo(dL, dT)
                    lineTo(dR - fold, dT)
                    lineTo(dR, dT + fold)
                    lineTo(dR, dB)
                    lineTo(dL, dB)
                    close()
                }
                drawPath(paperPath, color = Color.White)
                drawPath(paperPath, color = primaryColor.copy(alpha = 0.3f), style = Stroke(width = 1.5.dp.toPx()))

                // Fold Corner
                drawPath(
                    Path().apply {
                        moveTo(dR - fold, dT)
                        lineTo(dR - fold, dT + fold)
                        lineTo(dR, dT + fold)
                    },
                    color = primaryColor.copy(alpha = 0.2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Simulated Content Lines
                val lineL = dL + (dR - dL) * 0.18f
                val lineR = dR - (dR - dL) * 0.18f
                val lineStroke = 2.5.dp.toPx()

                drawLine(primaryColor.copy(alpha = 0.7f), Offset(lineL, dT + (dB - dT) * 0.35f), Offset(lineR, dT + (dB - dT) * 0.35f), strokeWidth = lineStroke, cap = StrokeCap.Round)
                drawLine(primaryColor.copy(alpha = 0.5f), Offset(lineL, dT + (dB - dT) * 0.50f), Offset(lineR, dT + (dB - dT) * 0.50f), strokeWidth = lineStroke, cap = StrokeCap.Round)
                drawLine(primaryColor.copy(alpha = 0.5f), Offset(lineL, dT + (dB - dT) * 0.65f), Offset(lineL + (lineR - lineL) * 0.6f, dT + (dB - dT) * 0.65f), strokeWidth = lineStroke, cap = StrokeCap.Round)

                // Scanline Beam
                drawLine(
                    color = accentColor,
                    start = Offset(fL - 5.dp.toPx(), h * 0.52f),
                    end = Offset(fR + 5.dp.toPx(), h * 0.52f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Illustration 2: Tilted quadrilateral paper transforming into aligned rectangular document with corner nodes.
 */
@Composable
fun OnboardingIllustration2(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = ScanlineCyan
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp, 190.dp)) {
                val w = size.width
                val h = size.height

                // Left: Tilted Angled Quad Document
                val tTL = Offset(w * 0.08f, h * 0.30f)
                val tTR = Offset(w * 0.38f, h * 0.20f)
                val tBR = Offset(w * 0.42f, h * 0.78f)
                val tBL = Offset(w * 0.12f, h * 0.85f)

                val tiltedQuad = Path().apply {
                    moveTo(tTL.x, tTL.y)
                    lineTo(tTR.x, tTR.y)
                    lineTo(tBR.x, tBR.y)
                    lineTo(tBL.x, tBL.y)
                    close()
                }
                drawPath(tiltedQuad, color = Color.White.copy(alpha = 0.8f))
                drawPath(tiltedQuad, color = Color.Gray.copy(alpha = 0.4f), style = Stroke(width = 1.5.dp.toPx()))

                // Corner Handles on tilted doc (cyan points)
                val radius = 4.dp.toPx()
                drawCircle(accentColor, radius, tTL)
                drawCircle(accentColor, radius, tTR)
                drawCircle(accentColor, radius, tBR)
                drawCircle(accentColor, radius, tBL)

                // Center Transformation Arrow
                val arrowY = h * 0.50f
                drawLine(
                    color = primaryColor,
                    start = Offset(w * 0.46f, arrowY),
                    end = Offset(w * 0.56f, arrowY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val head = Path().apply {
                    moveTo(w * 0.53f, arrowY - 5.dp.toPx())
                    lineTo(w * 0.57f, arrowY)
                    lineTo(w * 0.53f, arrowY + 5.dp.toPx())
                }
                drawPath(head, color = primaryColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                // Right: Perfectly Straight Aligned Sheet
                val aL = w * 0.62f
                val aT = h * 0.20f
                val aR = w * 0.92f
                val aB = h * 0.80f

                val alignedPath = Path().apply {
                    moveTo(aL, aT)
                    lineTo(aR, aT)
                    lineTo(aR, aB)
                    lineTo(aL, aB)
                    close()
                }
                drawPath(alignedPath, color = Color.White)
                drawPath(alignedPath, color = primaryColor, style = Stroke(width = 2.dp.toPx()))

                // Alignment Checkmark badge
                drawCircle(
                    color = primaryColor,
                    radius = 12.dp.toPx(),
                    center = Offset(aR, aT)
                )
            }

            // Overlay Checkmark icon inside badge
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 12.dp, top = 22.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Illustration 3: Before/After split view with filter chips (Auto, B&W, Grayscale, Color).
 */
@Composable
fun OnboardingIllustration3(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = ScanlineCyan
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Document Enhance Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(135.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Left half (Original photo - slightly yellowed/shadowy)
                    drawRoundRect(
                        color = Color(0xFFE8E2CF),
                        size = Size(w * 0.5f, h),
                        cornerRadius = CornerRadius(0f)
                    )

                    // Left content lines (shadowy)
                    drawLine(Color(0xFF8C857B), Offset(w * 0.08f, h * 0.3f), Offset(w * 0.42f, h * 0.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color(0xFF8C857B), Offset(w * 0.08f, h * 0.5f), Offset(w * 0.42f, h * 0.5f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color(0xFF8C857B), Offset(w * 0.08f, h * 0.7f), Offset(w * 0.32f, h * 0.7f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

                    // Right half (Clean enhanced scan)
                    drawLine(Color(0xFF006A6D), Offset(w * 0.58f, h * 0.3f), Offset(w * 0.92f, h * 0.3f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color(0xFF006A6D), Offset(w * 0.58f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color(0xFF006A6D), Offset(w * 0.58f, h * 0.7f), Offset(w * 0.82f, h * 0.7f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)

                    // Divider line
                    drawLine(accentColor, Offset(w * 0.50f, 0f), Offset(w * 0.50f, h), strokeWidth = 3.dp.toPx())
                }

                // Center Sparkle Badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Filter Pills Row (Auto, B&W, Grayscale, Color)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterPillChip(label = "Auto", isSelected = true)
                FilterPillChip(label = "B&W", isSelected = false)
                FilterPillChip(label = "Grayscale", isSelected = false)
                FilterPillChip(label = "Color", isSelected = false)
            }
        }
    }
}

@Composable
private fun FilterPillChip(
    label: String,
    isSelected: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Illustration 4: Multiple document pages organizing into a PDF binder with privacy shield.
 */
@Composable
fun OnboardingIllustration4(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = ScanlineCyan
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp, 180.dp)) {
                val w = size.width
                val h = size.height

                // Page 3 (Backmost)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.7f),
                    topLeft = Offset(w * 0.10f, h * 0.15f),
                    size = Size(w * 0.42f, h * 0.65f),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )

                // Page 2 (Middle)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.16f, h * 0.20f),
                    size = Size(w * 0.42f, h * 0.65f),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )

                // Page 1 (Frontmost Main Page)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.22f, h * 0.25f),
                    size = Size(w * 0.42f, h * 0.65f),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )

                // Content lines on Front Page
                val pL = w * 0.28f
                val pR = w * 0.58f
                drawLine(primaryColor, Offset(pL, h * 0.38f), Offset(pR, h * 0.38f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(primaryColor, Offset(pL, h * 0.50f), Offset(pR, h * 0.50f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(primaryColor, Offset(pL, h * 0.62f), Offset(pL + (pR - pL) * 0.6f, h * 0.62f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

                // Arrow into PDF badge
                val aY = h * 0.55f
                drawLine(
                    color = primaryColor,
                    start = Offset(w * 0.66f, aY),
                    end = Offset(w * 0.74f, aY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // PDF Badge on right
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Lock Shield Badge at bottom
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private",
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Local Privacy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
