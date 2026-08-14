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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    val handleTouchRadiusPx = with(density) { 44.dp.toPx() }
    val edgeTouchRadiusPx = with(density) { 28.dp.toPx() }

    // Use rememberUpdatedState so pointerInput gesture detection is never cancelled on recompositions
    val currentQuad by rememberUpdatedState(quad)
    val currentOnQuadChanged by rememberUpdatedState(onQuadChanged)
    val currentSelectedCorner by rememberUpdatedState(selectedCorner)
    val currentOnCornerSelected by rememberUpdatedState(onCornerSelected)
    val currentSelectedEdge by rememberUpdatedState(selectedEdge)
    val currentOnEdgeSelected by rememberUpdatedState(onEdgeSelected)
    val currentLockedCorners by rememberUpdatedState(lockedCorners)
    val currentOnAdjustingChanged by rememberUpdatedState(onAdjustingChanged)

    var activeDragCorner by remember { mutableStateOf(ActiveCorner.NONE) }
    var activeDragEdge by remember { mutableStateOf(ActiveEdge.NONE) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("crop_overlay")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touchOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectDragGestures

                        // Exact letterbox computation to match ContentScale.Fit of the Bitmap
                        val bmpW = bitmap?.width?.toFloat() ?: w
                        val bmpH = bitmap?.height?.toFloat() ?: h
                        val scale = minOf(w / bmpW, h / bmpH)
                        val drawnW = bmpW * scale
                        val drawnH = bmpH * scale
                        val offsetX = (w - drawnW) / 2f
                        val offsetY = (h - drawnH) / 2f

                        val q = currentQuad
                        val tl = Offset(offsetX + q.topLeft.x * drawnW, offsetY + q.topLeft.y * drawnH)
                        val tr = Offset(offsetX + q.topRight.x * drawnW, offsetY + q.topRight.y * drawnH)
                        val br = Offset(offsetX + q.bottomRight.x * drawnW, offsetY + q.bottomRight.y * drawnH)
                        val bl = Offset(offsetX + q.bottomLeft.x * drawnW, offsetY + q.bottomLeft.y * drawnH)

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
                            activeDragCorner = corner
                            activeDragEdge = ActiveEdge.NONE
                            currentOnCornerSelected(corner)
                            currentOnEdgeSelected(ActiveEdge.NONE)
                            currentOnAdjustingChanged(true)
                        } else {
                            // Check distance to edges
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
                                activeDragEdge = edge
                                activeDragCorner = ActiveCorner.NONE
                                currentOnEdgeSelected(edge)
                                currentOnCornerSelected(ActiveCorner.NONE)
                                currentOnAdjustingChanged(true)
                            } else {
                                // Default to closest corner if user taps somewhere nearby
                                val corner = when (minCornerDistance) {
                                    dTL -> ActiveCorner.TOP_LEFT
                                    dTR -> ActiveCorner.TOP_RIGHT
                                    dBR -> ActiveCorner.BOTTOM_RIGHT
                                    else -> ActiveCorner.BOTTOM_LEFT
                                }
                                activeDragCorner = corner
                                activeDragEdge = ActiveEdge.NONE
                                currentOnCornerSelected(corner)
                                currentOnEdgeSelected(ActiveEdge.NONE)
                                currentOnAdjustingChanged(true)
                            }
                        }
                    },
                    onDragEnd = {
                        activeDragCorner = ActiveCorner.NONE
                        activeDragEdge = ActiveEdge.NONE
                        currentOnAdjustingChanged(false)
                    },
                    onDragCancel = {
                        activeDragCorner = ActiveCorner.NONE
                        activeDragEdge = ActiveEdge.NONE
                        currentOnAdjustingChanged(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectDragGestures

                        val bmpW = bitmap?.width?.toFloat() ?: w
                        val bmpH = bitmap?.height?.toFloat() ?: h
                        val scale = minOf(w / bmpW, h / bmpH)
                        val drawnW = bmpW * scale
                        val drawnH = bmpH * scale
                        val offsetX = (w - drawnW) / 2f
                        val offsetY = (h - drawnH) / 2f

                        val targetCorner = if (activeDragCorner != ActiveCorner.NONE) activeDragCorner else currentSelectedCorner
                        val targetEdge = if (activeDragEdge != ActiveEdge.NONE) activeDragEdge else currentSelectedEdge
                        val q = currentQuad

                        if (targetCorner != ActiveCorner.NONE) {
                            if (currentLockedCorners.contains(targetCorner)) return@detectDragGestures

                            // Compute new normalized position based on finger position or drag delta
                            val newNormX = ((change.position.x - offsetX) / drawnW).coerceIn(0.01f, 0.99f)
                            val newNormY = ((change.position.y - offsetY) / drawnH).coerceIn(0.01f, 0.99f)
                            val newPt = PointF(newNormX, newNormY)

                            val updatedQuad = when (targetCorner) {
                                ActiveCorner.TOP_LEFT -> {
                                    q.copy(
                                        topLeft = PointF(
                                            newPt.x.coerceAtMost(q.topRight.x - 0.04f),
                                            newPt.y.coerceAtMost(q.bottomLeft.y - 0.04f)
                                        )
                                    )
                                }
                                ActiveCorner.TOP_RIGHT -> {
                                    q.copy(
                                        topRight = PointF(
                                            newPt.x.coerceAtLeast(q.topLeft.x + 0.04f),
                                            newPt.y.coerceAtMost(q.bottomRight.y - 0.04f)
                                        )
                                    )
                                }
                                ActiveCorner.BOTTOM_RIGHT -> {
                                    q.copy(
                                        bottomRight = PointF(
                                            newPt.x.coerceAtLeast(q.bottomLeft.x + 0.04f),
                                            newPt.y.coerceAtLeast(q.topRight.y + 0.04f)
                                        )
                                    )
                                }
                                ActiveCorner.BOTTOM_LEFT -> {
                                    q.copy(
                                        bottomLeft = PointF(
                                            newPt.x.coerceAtMost(q.bottomRight.x - 0.04f),
                                            newPt.y.coerceAtLeast(q.topLeft.y + 0.04f)
                                        )
                                    )
                                }
                                else -> q
                            }

                            if (updatedQuad != q && updatedQuad.isValidQuad()) {
                                currentOnQuadChanged(updatedQuad)
                            }
                        } else if (targetEdge != ActiveEdge.NONE) {
                            val dx = dragAmount.x / drawnW
                            val dy = dragAmount.y / drawnH

                            val updatedQuad = when (targetEdge) {
                                ActiveEdge.TOP -> {
                                    if (currentLockedCorners.contains(ActiveCorner.TOP_LEFT) || currentLockedCorners.contains(ActiveCorner.TOP_RIGHT)) q
                                    else {
                                        val newTL = PointF(q.topLeft.x, (q.topLeft.y + dy).coerceIn(0.01f, q.bottomLeft.y - 0.04f))
                                        val newTR = PointF(q.topRight.x, (q.topRight.y + dy).coerceIn(0.01f, q.bottomRight.y - 0.04f))
                                        q.copy(topLeft = newTL, topRight = newTR)
                                    }
                                }
                                ActiveEdge.BOTTOM -> {
                                    if (currentLockedCorners.contains(ActiveCorner.BOTTOM_LEFT) || currentLockedCorners.contains(ActiveCorner.BOTTOM_RIGHT)) q
                                    else {
                                        val newBL = PointF(q.bottomLeft.x, (q.bottomLeft.y + dy).coerceIn(q.topLeft.y + 0.04f, 0.99f))
                                        val newBR = PointF(q.bottomRight.x, (q.bottomRight.y + dy).coerceIn(q.topRight.y + 0.04f, 0.99f))
                                        q.copy(bottomLeft = newBL, bottomRight = newBR)
                                    }
                                }
                                ActiveEdge.LEFT -> {
                                    if (currentLockedCorners.contains(ActiveCorner.TOP_LEFT) || currentLockedCorners.contains(ActiveCorner.BOTTOM_LEFT)) q
                                    else {
                                        val newTL = PointF((q.topLeft.x + dx).coerceIn(0.01f, q.topRight.x - 0.04f), q.topLeft.y)
                                        val newBL = PointF((q.bottomLeft.x + dx).coerceIn(0.01f, q.bottomRight.x - 0.04f), q.bottomLeft.y)
                                        q.copy(topLeft = newTL, bottomLeft = newBL)
                                    }
                                }
                                ActiveEdge.RIGHT -> {
                                    if (currentLockedCorners.contains(ActiveCorner.TOP_RIGHT) || currentLockedCorners.contains(ActiveCorner.BOTTOM_RIGHT)) q
                                    else {
                                        val newTR = PointF((q.topRight.x + dx).coerceIn(q.topLeft.x + 0.04f, 0.99f), q.topRight.y)
                                        val newBR = PointF((q.bottomRight.x + dx).coerceIn(q.bottomLeft.x + 0.04f, 0.99f), q.bottomRight.y)
                                        q.copy(topRight = newTR, bottomRight = newBR)
                                    }
                                }
                                else -> q
                            }

                            if (updatedQuad != q && updatedQuad.isValidQuad()) {
                                currentOnQuadChanged(updatedQuad)
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val bmpW = bitmap?.width?.toFloat() ?: w
            val bmpH = bitmap?.height?.toFloat() ?: h
            val scale = minOf(w / bmpW, h / bmpH)
            val drawnW = bmpW * scale
            val drawnH = bmpH * scale
            val offsetX = (w - drawnW) / 2f
            val offsetY = (h - drawnH) / 2f

            val tl = Offset(offsetX + quad.topLeft.x * drawnW, offsetY + quad.topLeft.y * drawnH)
            val tr = Offset(offsetX + quad.topRight.x * drawnW, offsetY + quad.topRight.y * drawnH)
            val br = Offset(offsetX + quad.bottomRight.x * drawnW, offsetY + quad.bottomRight.y * drawnH)
            val bl = Offset(offsetX + quad.bottomLeft.x * drawnW, offsetY + quad.bottomLeft.y * drawnH)

            // 1. Dark Overlay Scrim Outside Quad
            val fullPath = Path().apply {
                addRect(Rect(0f, 0f, w, h))
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
                color = Color.Black.copy(alpha = 0.45f)
            )

            // Subtle highlight inside document quad
            drawPath(
                path = quadPath,
                color = Color(0xFF00E5D9).copy(alpha = 0.08f)
            )

            // 2. Perspective Grid Lines (Rule of Thirds)
            val gridSteps = 3
            for (i in 1 until gridSteps) {
                val t = i.toFloat() / gridSteps

                // Vertical grid lines
                val topP = Offset(tl.x + t * (tr.x - tl.x), tl.y + t * (tr.y - tl.y))
                val botP = Offset(bl.x + t * (br.x - bl.x), bl.y + t * (br.y - bl.y))
                drawLine(
                    color = Color(0xFF00E5D9).copy(alpha = 0.35f),
                    start = topP,
                    end = botP,
                    strokeWidth = 1.2.dp.toPx()
                )

                // Horizontal grid lines
                val leftP = Offset(tl.x + t * (bl.x - tl.x), tl.y + t * (bl.y - tl.y))
                val rightP = Offset(tr.x + t * (br.x - tr.x), tr.y + t * (br.y - tr.y))
                drawLine(
                    color = Color(0xFF00E5D9).copy(alpha = 0.35f),
                    start = leftP,
                    end = rightP,
                    strokeWidth = 1.2.dp.toPx()
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

            // 4. Draw Corner Handles (Large, easy to touch, clear visual indicators)
            val corners = listOf(
                Pair(ActiveCorner.TOP_LEFT, tl),
                Pair(ActiveCorner.TOP_RIGHT, tr),
                Pair(ActiveCorner.BOTTOM_RIGHT, br),
                Pair(ActiveCorner.BOTTOM_LEFT, bl)
            )

            corners.forEach { (cornerType, offset) ->
                val isSelected = selectedCorner == cornerType || activeDragCorner == cornerType
                val isLocked = lockedCorners.contains(cornerType)

                val outerRadius = if (isSelected) 20.dp.toPx() else 16.dp.toPx()
                val innerRadius = if (isSelected) 9.dp.toPx() else 7.dp.toPx()
                val handleColor = when {
                    isLocked -> Color(0xFFFF5252)
                    isSelected -> Color(0xFF00E5D9)
                    else -> Color.White
                }

                // Outer Shadow/Touch Ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.50f),
                    radius = outerRadius + 4.dp.toPx(),
                    center = offset
                )

                // Main Handle Ring
                drawCircle(
                    color = handleColor,
                    radius = outerRadius,
                    center = offset,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Semi-transparent background inside ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.3f),
                    radius = outerRadius - 1.5.dp.toPx(),
                    center = offset
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

            // 5. Magnifier Loupe Rendering
            val activeCornerForLoupe = when {
                activeDragCorner != ActiveCorner.NONE -> activeDragCorner
                selectedCorner != ActiveCorner.NONE -> selectedCorner
                else -> ActiveCorner.NONE
            }

            if ((isAdjusting || activeDragCorner != ActiveCorner.NONE) && activeCornerForLoupe != ActiveCorner.NONE && bitmap != null) {
                val cornerPt = when (activeCornerForLoupe) {
                    ActiveCorner.TOP_LEFT -> quad.topLeft
                    ActiveCorner.TOP_RIGHT -> quad.topRight
                    ActiveCorner.BOTTOM_RIGHT -> quad.bottomRight
                    ActiveCorner.BOTTOM_LEFT -> quad.bottomLeft
                    else -> PointF(0f, 0f)
                }

                val cornerOffset = Offset(offsetX + cornerPt.x * drawnW, offsetY + cornerPt.y * drawnH)

                val loupeRadiusPx = 52.dp.toPx()
                val loupeCenter = if (cornerOffset.y < 140.dp.toPx()) {
                    Offset(cornerOffset.x.coerceIn(loupeRadiusPx + 16.dp.toPx(), w - loupeRadiusPx - 16.dp.toPx()), cornerOffset.y + 120.dp.toPx())
                } else {
                    Offset(cornerOffset.x.coerceIn(loupeRadiusPx + 16.dp.toPx(), w - loupeRadiusPx - 16.dp.toPx()), cornerOffset.y - 120.dp.toPx())
                }

                // Target Line connecting corner point to loupe center
                drawLine(
                    color = Color(0xFF00E5D9).copy(alpha = 0.75f),
                    start = cornerOffset,
                    end = loupeCenter,
                    strokeWidth = 2.dp.toPx()
                )

                // Outer Ring of Loupe
                drawCircle(
                    color = Color.Black.copy(alpha = 0.6f),
                    radius = loupeRadiusPx + 4.dp.toPx(),
                    center = loupeCenter
                )

                // Magnified Bitmap Viewport Clip
                val loupeClipPath = Path().apply {
                    addOval(Rect(loupeCenter.x - loupeRadiusPx, loupeCenter.y - loupeRadiusPx, loupeCenter.x + loupeRadiusPx, loupeCenter.y + loupeRadiusPx))
                }

                clipPath(loupeClipPath) {
                    val zoomFactor = 2.5f
                    val sampleSrcWidth = (drawnW / zoomFactor).toInt().coerceAtLeast(10)
                    val sampleSrcHeight = (drawnH / zoomFactor).toInt().coerceAtLeast(10)

                    val bmpPixelX = (cornerPt.x * bitmap.width).toInt()
                    val bmpPixelY = (cornerPt.y * bitmap.height).toInt()

                    val bmpCropW = (bitmap.width / zoomFactor).toInt().coerceIn(20, bitmap.width)
                    val bmpCropH = (bitmap.height / zoomFactor).toInt().coerceIn(20, bitmap.height)

                    val srcLeft = (bmpPixelX - bmpCropW / 2).coerceIn(0, max(0, bitmap.width - bmpCropW))
                    val srcTop = (bmpPixelY - bmpCropH / 2).coerceIn(0, max(0, bitmap.height - bmpCropH))

                    try {
                        val composeBitmap = bitmap.asImageBitmap()
                        drawImage(
                            image = composeBitmap,
                            srcOffset = IntOffset(srcLeft, srcTop),
                            srcSize = IntSize(bmpCropW, bmpCropH),
                            dstOffset = IntOffset((loupeCenter.x - loupeRadiusPx).toInt(), (loupeCenter.y - loupeRadiusPx).toInt()),
                            dstSize = IntSize((loupeRadiusPx * 2).toInt(), (loupeRadiusPx * 2).toInt())
                        )
                    } catch (e: Exception) {
                        // fallback solid color
                        drawCircle(color = Color.DarkGray, radius = loupeRadiusPx, center = loupeCenter)
                    }

                    // Loupe Crosshair Target
                    drawLine(
                        color = Color(0xFF00E5D9),
                        start = Offset(loupeCenter.x - 14.dp.toPx(), loupeCenter.y),
                        end = Offset(loupeCenter.x + 14.dp.toPx(), loupeCenter.y),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF00E5D9),
                        start = Offset(loupeCenter.x, loupeCenter.y - 14.dp.toPx()),
                        end = Offset(loupeCenter.x, loupeCenter.y + 14.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Loupe Border Ring
                drawCircle(
                    color = Color(0xFF00E5D9),
                    radius = loupeRadiusPx,
                    center = loupeCenter,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Double {
    val abX = b.x - a.x
    val abY = b.y - a.y
    val abLenSq = abX * abX + abY * abY
    if (abLenSq == 0f) return hypot((p.x - a.x).toDouble(), (p.y - a.y).toDouble())

    val t = (((p.x - a.x) * abX + (p.y - a.y) * abY) / abLenSq).coerceIn(0f, 1f)
    val projX = a.x + t * abX
    val projY = a.y + t * abY
    return hypot((p.x - projX).toDouble(), (p.y - projY).toDouble())
}
