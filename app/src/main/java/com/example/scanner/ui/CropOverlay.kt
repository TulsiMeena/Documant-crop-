package com.example.scanner.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanner.model.DocumentQuad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ActiveCorner { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }
enum class ActiveEdge { NONE, TOP, BOTTOM, LEFT, RIGHT }

@Composable
fun CropOverlay(
    bitmap: Bitmap?,
    quad: DocumentQuad,
    onQuadChanged: (DocumentQuad) -> Unit,
    selectedCorner: ActiveCorner,
    onCornerSelected: (ActiveCorner) -> Unit,
    selectedEdge: ActiveEdge,
    onEdgeSelected: (ActiveEdge) -> Unit,
    lockedCorners: Set<ActiveCorner>,
    isAdjusting: Boolean,
    onAdjustingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleTouchRadiusPx = with(density) { 38.dp.toPx() }
    val edgeTouchRadiusPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("crop_overlay")
            .pointerInput(quad, lockedCorners) {
                detectDragGestures(
                    onDragStart = { touchOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectDragGestures

                        val tl = Offset(quad.topLeft.x * w, quad.topLeft.y * h)
                        val tr = Offset(quad.topRight.x * w, quad.topRight.y * h)
                        val br = Offset(quad.bottomRight.x * w, quad.bottomRight.y * h)
                        val bl = Offset(quad.bottomLeft.x * w, quad.bottomLeft.y * h)

                        val dTL = hypot((touchOffset.x - tl.x).toDouble(), (touchOffset.y - tl.y).toDouble())
                        val dTR = hypot((touchOffset.x - tr.x).toDouble(), (touchOffset.y - tr.y).toDouble())
                        val dBR = hypot((touchOffset.x - br.x).toDouble(), (touchOffset.y - br.y).toDouble())
                        val dBL = hypot((touchOffset.x - bl.x).toDouble(), (touchOffset.y - bl.y).toDouble())

                        val minCornerDistance = minOf(dTL, dTR, dBR, dBL)

                        if (minCornerDistance <= handleTouchRadiusPx) {
                            val corner = when (minCornerDistance) {
                                dTL -> ActiveCorner.TOP_LEFT
                                dTR -> ActiveCorner.TOP_RIGHT
                                dBR -> ActiveCorner.BOTTOM_RIGHT
                                else -> ActiveCorner.BOTTOM_LEFT
                            }
                            onCornerSelected(corner)
                            onEdgeSelected(ActiveEdge.NONE)
                            onAdjustingChanged(true)
                        } else {
                            // Distance to edge segments
                            val dTop = distanceToSegment(touchOffset, tl, tr)
                            val dBottom = distanceToSegment(touchOffset, bl, br)
                            val dLeft = distanceToSegment(touchOffset, tl, bl)
                            val dRight = distanceToSegment(touchOffset, tr, br)

                            val minEdgeDistance = minOf(dTop, dBottom, dLeft, dRight)
                            if (minEdgeDistance <= edgeTouchRadiusPx) {
                                val edge = when (minEdgeDistance) {
                                    dTop -> ActiveEdge.TOP
                                    dBottom -> ActiveEdge.BOTTOM
                                    dLeft -> ActiveEdge.LEFT
                                    else -> ActiveEdge.RIGHT
                                }
                                onEdgeSelected(edge)
                                onCornerSelected(ActiveCorner.NONE)
                                onAdjustingChanged(true)
                            }
                        }
                    },
                    onDragEnd = {
                        onAdjustingChanged(false)
                    },
                    onDragCancel = {
                        onAdjustingChanged(false)
                    },
                    onDrag = { change, dragAmount ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectDragGestures

                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h

                        if (selectedCorner != ActiveCorner.NONE) {
                            if (lockedCorners.contains(selectedCorner)) return@detectDragGestures

                            val curPt = when (selectedCorner) {
                                ActiveCorner.TOP_LEFT -> quad.topLeft
                                ActiveCorner.TOP_RIGHT -> quad.topRight
                                ActiveCorner.BOTTOM_RIGHT -> quad.bottomRight
                                ActiveCorner.BOTTOM_LEFT -> quad.bottomLeft
                                else -> PointF(0f, 0f)
                            }

                            var targetX = (curPt.x + dx).coerceIn(0.01f, 0.99f)
                            var targetY = (curPt.y + dy).coerceIn(0.01f, 0.99f)

                            // Magnetic snap check against image boundary / default edges
                            val snapDist = 0.018f
                            if (abs(targetX - 0.08f) < snapDist) targetX = 0.08f
                            if (abs(targetX - 0.92f) < snapDist) targetX = 0.92f
                            if (abs(targetY - 0.08f) < snapDist) targetY = 0.08f
                            if (abs(targetY - 0.92f) < snapDist) targetY = 0.92f

                            val newPt = PointF(targetX, targetY)
                            val updatedQuad = when (selectedCorner) {
                                ActiveCorner.TOP_LEFT -> {
                                    if (newPt.x < quad.topRight.x - 0.05f && newPt.y < quad.bottomLeft.y - 0.05f) {
                                        quad.copy(topLeft = newPt)
                                    } else quad
                                }
                                ActiveCorner.TOP_RIGHT -> {
                                    if (newPt.x > quad.topLeft.x + 0.05f && newPt.y < quad.bottomRight.y - 0.05f) {
                                        quad.copy(topRight = newPt)
                                    } else quad
                                }
                                ActiveCorner.BOTTOM_RIGHT -> {
                                    if (newPt.x > quad.bottomLeft.x + 0.05f && newPt.y > quad.topRight.y + 0.05f) {
                                        quad.copy(bottomRight = newPt)
                                    } else quad
                                }
                                ActiveCorner.BOTTOM_LEFT -> {
                                    if (newPt.x < quad.bottomRight.x - 0.05f && newPt.y > quad.topLeft.y + 0.05f) {
                                        quad.copy(bottomLeft = newPt)
                                    } else quad
                                }
                                else -> quad
                            }

                            if (updatedQuad != quad && updatedQuad.isValidQuad()) {
                                onQuadChanged(updatedQuad)
                            }
                        } else if (selectedEdge != ActiveEdge.NONE) {
                            val updatedQuad = when (selectedEdge) {
                                ActiveEdge.TOP -> {
                                    if (lockedCorners.contains(ActiveCorner.TOP_LEFT) || lockedCorners.contains(ActiveCorner.TOP_RIGHT)) quad
                                    else {
                                        val newTL = PointF((quad.topLeft.x + dx).coerceIn(0.01f, quad.topRight.x - 0.05f), (quad.topLeft.y + dy).coerceIn(0.01f, quad.bottomLeft.y - 0.05f))
                                        val newTR = PointF((quad.topRight.x + dx).coerceIn(quad.topLeft.x + 0.05f, 0.99f), (quad.topRight.y + dy).coerceIn(0.01f, quad.bottomRight.y - 0.05f))
                                        quad.copy(topLeft = newTL, topRight = newTR)
                                    }
                                }
                                ActiveEdge.BOTTOM -> {
                                    if (lockedCorners.contains(ActiveCorner.BOTTOM_LEFT) || lockedCorners.contains(ActiveCorner.BOTTOM_RIGHT)) quad
                                    else {
                                        val newBL = PointF((quad.bottomLeft.x + dx).coerceIn(0.01f, quad.bottomRight.x - 0.05f), (quad.bottomLeft.y + dy).coerceIn(quad.topLeft.y + 0.05f, 0.99f))
                                        val newBR = PointF((quad.bottomRight.x + dx).coerceIn(quad.bottomLeft.x + 0.05f, 0.99f), (quad.bottomRight.y + dy).coerceIn(quad.topRight.y + 0.05f, 0.99f))
                                        quad.copy(bottomLeft = newBL, bottomRight = newBR)
                                    }
                                }
                                ActiveEdge.LEFT -> {
                                    if (lockedCorners.contains(ActiveCorner.TOP_LEFT) || lockedCorners.contains(ActiveCorner.BOTTOM_LEFT)) quad
                                    else {
                                        val newTL = PointF((quad.topLeft.x + dx).coerceIn(0.01f, quad.topRight.x - 0.05f), (quad.topLeft.y + dy).coerceIn(0.01f, quad.bottomLeft.y - 0.05f))
                                        val newBL = PointF((quad.bottomLeft.x + dx).coerceIn(0.01f, quad.bottomRight.x - 0.05f), (quad.bottomLeft.y + dy).coerceIn(quad.topLeft.y + 0.05f, 0.99f))
                                        quad.copy(topLeft = newTL, bottomLeft = newBL)
                                    }
                                }
                                ActiveEdge.RIGHT -> {
                                    if (lockedCorners.contains(ActiveCorner.TOP_RIGHT) || lockedCorners.contains(ActiveCorner.BOTTOM_RIGHT)) quad
                                    else {
                                        val newTR = PointF((quad.topRight.x + dx).coerceIn(quad.topLeft.x + 0.05f, 0.99f), (quad.topRight.y + dy).coerceIn(0.01f, quad.bottomRight.y - 0.05f))
                                        val newBR = PointF((quad.bottomRight.x + dx).coerceIn(quad.bottomLeft.x + 0.05f, 0.99f), (quad.bottomRight.y + dy).coerceIn(quad.topRight.y + 0.05f, 0.99f))
                                        quad.copy(topRight = newTR, bottomRight = newBR)
                                    }
                                }
                                else -> quad
                            }

                            if (updatedQuad != quad && updatedQuad.isValidQuad()) {
                                onQuadChanged(updatedQuad)
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val tl = Offset(quad.topLeft.x * w, quad.topLeft.y * h)
            val tr = Offset(quad.topRight.x * w, quad.topRight.y * h)
            val br = Offset(quad.bottomRight.x * w, quad.bottomRight.y * h)
            val bl = Offset(quad.bottomLeft.x * w, quad.bottomLeft.y * h)

            // 1. Dark Overlay Mask Outside Quad
            val fullPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
            }

            val quadPath = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            drawPath(
                path = fullPath,
                color = Color.Black.copy(alpha = 0.50f)
            )

            // Clear quad interior tint
            drawPath(
                path = quadPath,
                color = Color(0xFF00E5D9).copy(alpha = 0.08f)
            )

            // 2. Perspective Grid Lines (Bilinear Interpolation)
            val gridSteps = 4
            for (i in 1 until gridSteps) {
                val t = i.toFloat() / gridSteps

                // Vertical perspective line from top edge to bottom edge
                val topP = Offset(tl.x + t * (tr.x - tl.x), tl.y + t * (tr.y - tl.y))
                val botP = Offset(bl.x + t * (br.x - bl.x), bl.y + t * (br.y - bl.y))
                drawLine(
                    color = Color(0xFF00E5D9).copy(alpha = 0.22f),
                    start = topP,
                    end = botP,
                    strokeWidth = 1.dp.toPx()
                )

                // Horizontal perspective line from left edge to right edge
                val leftP = Offset(tl.x + t * (bl.x - tl.x), tl.y + t * (bl.y - tl.y))
                val rightP = Offset(tr.x + t * (br.x - tr.x), tr.y + t * (br.y - tr.y))
                drawLine(
                    color = Color(0xFF00E5D9).copy(alpha = 0.22f),
                    start = leftP,
                    end = rightP,
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 3. Draw Quad Boundary Outline
            drawPath(
                path = quadPath,
                color = Color(0xFF00E5D9),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Highlight Active Selected Edge
            if (selectedEdge != ActiveEdge.NONE) {
                val (startEdge, endEdge) = when (selectedEdge) {
                    ActiveEdge.TOP -> Pair(tl, tr)
                    ActiveEdge.BOTTOM -> Pair(bl, br)
                    ActiveEdge.LEFT -> Pair(tl, bl)
                    ActiveEdge.RIGHT -> Pair(tr, br)
                    else -> Pair(Offset.Zero, Offset.Zero)
                }
                drawLine(
                    color = Color(0xFFFFD700),
                    start = startEdge,
                    end = endEdge,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Draw Corner Handles
            val corners = listOf(
                Pair(ActiveCorner.TOP_LEFT, tl),
                Pair(ActiveCorner.TOP_RIGHT, tr),
                Pair(ActiveCorner.BOTTOM_RIGHT, br),
                Pair(ActiveCorner.BOTTOM_LEFT, bl)
            )

            corners.forEach { (cornerType, offset) ->
                val isSelected = selectedCorner == cornerType
                val isLocked = lockedCorners.contains(cornerType)

                val outerRadius = if (isSelected) 18.dp.toPx() else 14.dp.toPx()
                val innerRadius = if (isSelected) 8.dp.toPx() else 6.dp.toPx()
                val handleColor = when {
                    isLocked -> Color(0xFFFF5252)
                    isSelected -> Color(0xFF00E5D9)
                    else -> Color.White
                }

                // Outer Shadow/Touch Ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    radius = outerRadius + 4.dp.toPx(),
                    center = offset
                )

                drawCircle(
                    color = handleColor,
                    radius = outerRadius,
                    center = offset,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Inner Solid Dot
                drawCircle(
                    color = handleColor,
                    radius = innerRadius,
                    center = offset
                )

                // Lock Indicator Dot if locked
                if (isLocked) {
                    drawCircle(
                        color = Color(0xFFFF5252),
                        radius = 4.dp.toPx(),
                        center = Offset(offset.x + outerRadius, offset.y - outerRadius)
                    )
                }
            }

            // 5. 2.5x Magnifier Loupe Rendering Real Bitmap Pixels
            val activeCornerForLoupe = if (selectedCorner != ActiveCorner.NONE) selectedCorner else ActiveCorner.NONE
            if ((isAdjusting || selectedCorner != ActiveCorner.NONE) && activeCornerForLoupe != ActiveCorner.NONE && bitmap != null) {
                val cornerPt = when (activeCornerForLoupe) {
                    ActiveCorner.TOP_LEFT -> quad.topLeft
                    ActiveCorner.TOP_RIGHT -> quad.topRight
                    ActiveCorner.BOTTOM_RIGHT -> quad.bottomRight
                    ActiveCorner.BOTTOM_LEFT -> quad.bottomLeft
                    else -> PointF(0f, 0f)
                }

                val cornerOffset = Offset(cornerPt.x * w, cornerPt.y * h)

                // Loupe Placement: position above finger or below if near top edge
                val loupeRadiusPx = 48.dp.toPx()
                val loupeCenter = if (cornerOffset.y < 130.dp.toPx()) {
                    Offset(cornerOffset.x.coerceIn(loupeRadiusPx + 16.dp.toPx(), w - loupeRadiusPx - 16.dp.toPx()), cornerOffset.y + 110.dp.toPx())
                } else {
                    Offset(cornerOffset.x.coerceIn(loupeRadiusPx + 16.dp.toPx(), w - loupeRadiusPx - 16.dp.toPx()), cornerOffset.y - 110.dp.toPx())
                }

                // Target Line connecting corner point to loupe center
                drawLine(
                    color = Color(0xFF00E5D9).copy(alpha = 0.7f),
                    start = cornerOffset,
                    end = loupeCenter,
                    strokeWidth = 2.dp.toPx()
                )

                // Extract real magnified bitmap region
                val bmpW = bitmap.width
                val bmpH = bitmap.height
                val centerBmpX = (cornerPt.x * bmpW).toInt().coerceIn(0, bmpW - 1)
                val centerBmpY = (cornerPt.y * bmpH).toInt().coerceIn(0, bmpH - 1)

                val cropRadius = 36 // 72x72 px source box
                val cropLeft = (centerBmpX - cropRadius).coerceIn(0, max(0, bmpW - 1))
                val cropTop = (centerBmpY - cropRadius).coerceIn(0, max(0, bmpH - 1))
                val cropRight = (centerBmpX + cropRadius).coerceIn(cropLeft + 1, bmpW)
                val cropBottom = (centerBmpY + cropRadius).coerceIn(cropTop + 1, bmpH)

                val srcRect = IntOffset(cropLeft, cropTop)
                val srcSize = IntSize(cropRight - cropLeft, cropBottom - cropTop)

                if (srcSize.width > 0 && srcSize.height > 0) {
                    val loupePath = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                loupeCenter.x - loupeRadiusPx,
                                loupeCenter.y - loupeRadiusPx,
                                loupeCenter.x + loupeRadiusPx,
                                loupeCenter.y + loupeRadiusPx
                            )
                        )
                    }

                    // Draw circular clipped loupe image
                    clipPath(loupePath) {
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            srcOffset = srcRect,
                            srcSize = srcSize,
                            dstOffset = IntOffset(
                                (loupeCenter.x - loupeRadiusPx).toInt(),
                                (loupeCenter.y - loupeRadiusPx).toInt()
                            ),
                            dstSize = IntSize((loupeRadiusPx * 2).toInt(), (loupeRadiusPx * 2).toInt())
                        )
                    }

                    // Loupe Outer Ring Border
                    drawCircle(
                        color = Color(0xFF00E5D9),
                        radius = loupeRadiusPx,
                        center = loupeCenter,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Center Crosshair (+) inside Loupe
                    val crossLength = 10.dp.toPx()
                    drawLine(
                        color = Color(0xFF00E5D9),
                        start = Offset(loupeCenter.x - crossLength, loupeCenter.y),
                        end = Offset(loupeCenter.x + crossLength, loupeCenter.y),
                        strokeWidth = 1.8.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF00E5D9),
                        start = Offset(loupeCenter.x, loupeCenter.y - crossLength),
                        end = Offset(loupeCenter.x, loupeCenter.y + crossLength),
                        strokeWidth = 1.8.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = loupeCenter
                    )
                }
            }
        }
    }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abX = b.x - a.x
    val abY = b.y - a.y
    val apX = p.x - a.x
    val apY = p.y - a.y

    val abLenSq = abX * abX + abY * abY
    if (abLenSq == 0f) return hypot((p.x - a.x).toDouble(), (p.y - a.y).toDouble()).toFloat()

    var t = (apX * abX + apY * abY) / abLenSq
    t = t.coerceIn(0f, 1f)

    val projX = a.x + t * abX
    val projY = a.y + t * abY

    return hypot((p.x - projX).toDouble(), (p.y - projY).toDouble()).toFloat()
}
