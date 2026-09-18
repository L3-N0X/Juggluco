package tk.glucodata.ui.graph

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.TimeRange
import tk.glucodata.ui.theme.LocalClinicalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun GlucoseGraph(
    readings: List<GlucosePoint>,
    logs: List<LogRecord> = emptyList(),
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    unit: GlucoseUnit = GlucoseUnit.MG_DL,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    onPointInspected: (GlucosePoint?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current
    val gridColor = clinicalColors.graphGrid
    val targetShade = clinicalColors.targetRangeShade
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    var inspectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var touchX by remember { mutableStateOf<Float?>(null) }

    val now = System.currentTimeMillis()
    val startTime = now - timeRange.durationMillis

    // Filter points in window
    val visiblePoints = remember(readings, startTime, now) {
        readings.filter { it.timestamp in (startTime - 5 * 60 * 1000L)..now }
    }

    // Determine Y range
    val minY = 40f
    val highestReading = visiblePoints.maxOfOrNull { it.valueMgDl } ?: 200f
    val maxY = max(260f, (highestReading + 30f))

    val boxModifier = if (modifier == Modifier) {
        Modifier.fillMaxWidth().height(280.dp)
    } else {
        modifier
    }

    Box(
        modifier = boxModifier
            .background(surfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visiblePoints, startTime, now) {
                    detectTapGestures(
                        onPress = { offset ->
                            val pt = findClosestPoint(offset.x, size.width - 90f, 20f, startTime, now, visiblePoints)
                            inspectedPoint = pt
                            touchX = offset.x
                            onPointInspected(pt)
                            tryAwaitRelease()
                            inspectedPoint = null
                            touchX = null
                            onPointInspected(null)
                        }
                    )
                }
                .pointerInput(visiblePoints, startTime, now) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val pt = findClosestPoint(offset.x, size.width - 90f, 20f, startTime, now, visiblePoints)
                            inspectedPoint = pt
                            touchX = offset.x
                            onPointInspected(pt)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pt = findClosestPoint(change.position.x, size.width - 90f, 20f, startTime, now, visiblePoints)
                            inspectedPoint = pt
                            touchX = change.position.x
                            onPointInspected(pt)
                        },
                        onDragEnd = {
                            inspectedPoint = null
                            touchX = null
                            onPointInspected(null)
                        },
                        onDragCancel = {
                            inspectedPoint = null
                            touchX = null
                            onPointInspected(null)
                        }
                    )
                }
        ) {
            val paddingLeft = 20f
            val paddingRight = 95f
            val paddingTop = 25f
            val paddingBottom = 45f

            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            fun timeToX(time: Long): Float {
                val fraction = ((time - startTime).toFloat() / (now - startTime).toFloat()).coerceIn(0f, 1f)
                return paddingLeft + (fraction * chartWidth)
            }

            fun valueToY(value: Float): Float {
                val fraction = ((value - minY) / (maxY - minY)).coerceIn(0f, 1f)
                return paddingTop + chartHeight - (fraction * chartHeight)
            }

            // 1. Draw Target Range Band
            val targetTopY = valueToY(targetHigh)
            val targetBottomY = valueToY(targetLow)
            drawRect(
                color = targetShade,
                topLeft = Offset(paddingLeft, targetTopY),
                size = Size(chartWidth, targetBottomY - targetTopY)
            )

            // 2. Draw Target Boundary Lines (Dashed)
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            drawLine(
                color = clinicalColors.inRange.copy(alpha = 0.6f),
                start = Offset(paddingLeft, targetTopY),
                end = Offset(paddingLeft + chartWidth, targetTopY),
                strokeWidth = 1.5f,
                pathEffect = dashedEffect
            )
            drawLine(
                color = clinicalColors.inRange.copy(alpha = 0.6f),
                start = Offset(paddingLeft, targetBottomY),
                end = Offset(paddingLeft + chartWidth, targetBottomY),
                strokeWidth = 1.5f,
                pathEffect = dashedEffect
            )

            // 3. Draw Horizontal Grid Lines & Y-Axis Labels
            val yStepMgDl = if (unit == GlucoseUnit.MMOL_L) 36f else 50f // ~2 mmol/L or 50 mg/dL
            var gridVal = minY + (yStepMgDl - (minY % yStepMgDl))
            val textPaint = Paint().apply {
                color = textSecondary.toArgb()
                textSize = 10.sp.toPx()
                isAntiAlias = true
            }

            while (gridVal < maxY) {
                val yPos = valueToY(gridVal)
                drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, yPos),
                    end = Offset(paddingLeft + chartWidth, yPos),
                    strokeWidth = 1f
                )

                // Label on the right
                val labelText = unit.format(gridVal)
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    paddingLeft + chartWidth + 12f,
                    yPos + 4.sp.toPx(),
                    textPaint
                )
                gridVal += yStepMgDl
            }

            // Draw Target Labels on Y-axis
            val targetHighPaint = Paint().apply {
                color = clinicalColors.inRange.toArgb()
                textSize = 10.sp.toPx()
                isAntiAlias = true
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${unit.format(targetHigh)} High",
                paddingLeft + chartWidth + 12f,
                targetTopY + 4.sp.toPx(),
                targetHighPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${unit.format(targetLow)} Low",
                paddingLeft + chartWidth + 12f,
                targetBottomY + 4.sp.toPx(),
                targetHighPaint
            )

            // 4. Draw Vertical Time Grid & X-Axis Labels
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val stepHours = when (timeRange) {
                TimeRange.THREE_HOURS -> 1
                TimeRange.SIX_HOURS -> 1
                TimeRange.TWELVE_HOURS -> 2
                TimeRange.TWENTY_FOUR_HOURS -> 4
                TimeRange.SEVEN_DAYS -> 24
            }
            val stepMillis = stepHours * 3600 * 1000L
            val firstTick = ((startTime / stepMillis) + 1) * stepMillis

            var tickTime = firstTick
            while (tickTime < now) {
                val xPos = timeToX(tickTime)
                drawLine(
                    color = gridColor,
                    start = Offset(xPos, paddingTop),
                    end = Offset(xPos, paddingTop + chartHeight),
                    strokeWidth = 1f
                )

                val timeStr = timeFormat.format(Date(tickTime))
                drawContext.canvas.nativeCanvas.drawText(
                    timeStr,
                    xPos - 18f,
                    paddingTop + chartHeight + 20f,
                    textPaint
                )
                tickTime += stepMillis
            }

            // 5. Draw Glucose Curve & Area Fill
            if (visiblePoints.size >= 2) {
                // Group points into continuous segments (break if gap > 20 min)
                val segments = ArrayList<List<GlucosePoint>>()
                var currentSegment = ArrayList<GlucosePoint>()
                for (i in visiblePoints.indices) {
                    val pt = visiblePoints[i]
                    if (currentSegment.isNotEmpty()) {
                        val prev = currentSegment.last()
                        if (pt.timestamp - prev.timestamp > 20 * 60 * 1000L) {
                            segments.add(currentSegment)
                            currentSegment = ArrayList()
                        }
                    }
                    currentSegment.add(pt)
                }
                if (currentSegment.isNotEmpty()) segments.add(currentSegment)

                for (seg in segments) {
                    if (seg.size < 2) continue

                    // Build line path & area path
                    val linePath = Path()
                    val areaPath = Path()

                    val startX = timeToX(seg.first().timestamp)
                    val startY = valueToY(seg.first().valueMgDl)
                    linePath.moveTo(startX, startY)
                    areaPath.moveTo(startX, paddingTop + chartHeight)
                    areaPath.lineTo(startX, startY)

                    for (i in 1 until seg.size) {
                        val prev = seg[i - 1]
                        val curr = seg[i]
                        val x0 = timeToX(prev.timestamp)
                        val y0 = valueToY(prev.valueMgDl)
                        val x1 = timeToX(curr.timestamp)
                        val y1 = valueToY(curr.valueMgDl)

                        // Smooth bezier control points
                        val midX = (x0 + x1) / 2f
                        linePath.cubicTo(midX, y0, midX, y1, x1, y1)
                        areaPath.cubicTo(midX, y0, midX, y1, x1, y1)
                    }

                    val lastX = timeToX(seg.last().timestamp)
                    areaPath.lineTo(lastX, paddingTop + chartHeight)
                    areaPath.close()

                    // Subtle soft gradient fill under curve
                    val areaGradient = Brush.verticalGradient(
                        colors = listOf(
                            clinicalColors.inRange.copy(alpha = 0.22f),
                            clinicalColors.inRange.copy(alpha = 0.02f)
                        ),
                        startY = paddingTop,
                        endY = paddingTop + chartHeight
                    )
                    drawPath(path = areaPath, brush = areaGradient)

                    // Draw the smooth curve line
                    drawPath(
                        path = linePath,
                        color = clinicalColors.inRange,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )

                    // Draw point dots along the curve with clinical color coding
                    for (pt in seg) {
                        val px = timeToX(pt.timestamp)
                        val py = valueToY(pt.valueMgDl)
                        val ptColor = when (pt.status) {
                            GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
                            GlucoseStatus.LOW -> clinicalColors.low
                            GlucoseStatus.IN_RANGE -> clinicalColors.inRange
                            GlucoseStatus.HIGH -> clinicalColors.high
                            GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
                        }

                        // Surface ring spacer + data dot
                        drawCircle(color = surfaceColor, radius = 5.5f, center = Offset(px, py))
                        drawCircle(color = ptColor, radius = 3.5f, center = Offset(px, py))
                    }
                }
            } else if (visiblePoints.size == 1) {
                val pt = visiblePoints[0]
                val px = timeToX(pt.timestamp)
                val py = valueToY(pt.valueMgDl)
                drawCircle(color = clinicalColors.inRange, radius = 6f, center = Offset(px, py))
            }

            // 6. Draw Log Markers (Insulin, Carbs, Blood Test) along the bottom
            val visibleLogs = logs.filter { it.timestamp in startTime..now }
            for (log in visibleLogs) {
                val lx = timeToX(log.timestamp)
                val ly = paddingTop + chartHeight + 2f
                val markerColor = when (log.type) {
                    LogType.RAPID_INSULIN -> Color(0xFF3B82F6) // Blue
                    LogType.BASAL_INSULIN -> Color(0xFF6366F1) // Indigo
                    LogType.CARBS, LogType.MEAL -> Color(0xFFF59E0B) // Amber/Food
                    LogType.BLOOD_GLUCOSE -> Color(0xFFEC4899) // Pink
                    LogType.NOTE -> Color(0xFF8B5CF6) // Purple
                }

                drawCircle(color = markerColor, radius = 5f, center = Offset(lx, ly))
                drawCircle(color = surfaceColor, radius = 2f, center = Offset(lx, ly))
            }

            // 7. Interactive Crosshair & Inspection Point
            val selected = inspectedPoint
            if (selected != null) {
                val sx = timeToX(selected.timestamp)
                val sy = valueToY(selected.valueMgDl)

                // Vertical scrub line
                drawLine(
                    color = textPrimary.copy(alpha = 0.5f),
                    start = Offset(sx, paddingTop),
                    end = Offset(sx, paddingTop + chartHeight),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )

                // Outer glow ring
                val statusColor = when (selected.status) {
                    GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
                    GlucoseStatus.LOW -> clinicalColors.low
                    GlucoseStatus.IN_RANGE -> clinicalColors.inRange
                    GlucoseStatus.HIGH -> clinicalColors.high
                    GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
                }
                drawCircle(color = statusColor.copy(alpha = 0.35f), radius = 12f, center = Offset(sx, sy))
                drawCircle(color = surfaceColor, radius = 7f, center = Offset(sx, sy))
                drawCircle(color = statusColor, radius = 5f, center = Offset(sx, sy))
            }
        }

        // 8. Floating Inspection Card (When scrubbing)
        val selected = inspectedPoint
        if (selected != null) {
            val statusColor = when (selected.status) {
                GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
                GlucoseStatus.LOW -> clinicalColors.low
                GlucoseStatus.IN_RANGE -> clinicalColors.inRange
                GlucoseStatus.HIGH -> clinicalColors.high
                GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            ) {
                RowInspection(selected = selected, unit = unit, statusColor = statusColor)
            }
        }
    }
}

@Composable
private fun RowInspection(
    selected: GlucosePoint,
    unit: GlucoseUnit,
    statusColor: Color
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = timeFormat.format(Date(selected.timestamp)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "  •  ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "${selected.formatted(unit)} ${unit.label}",
            style = MaterialTheme.typography.titleMedium,
            color = statusColor
        )
        Text(
            text = "  •  ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = selected.status.label,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
    }
}

private fun findClosestPoint(
    touchX: Float,
    chartWidth: Float,
    paddingLeft: Float,
    startTime: Long,
    now: Long,
    points: List<GlucosePoint>
): GlucosePoint? {
    if (points.isEmpty()) return null
    val fraction = ((touchX - paddingLeft) / chartWidth).coerceIn(0f, 1f)
    val targetTime = startTime + (fraction * (now - startTime)).toLong()

    return points.minByOrNull { abs(it.timestamp - targetTime) }
}
