package tk.glucodata.ui.graph

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.TimeRange
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.ui.theme.LocalClinicalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun GlucoseGraph(
    readings: List<GlucosePoint>,
    logs: List<LogRecord> = emptyList(),
    timeRange: TimeRange = TimeRange.SIX_HOURS,
    unit: GlucoseUnit = GlucoseUnit.MG_DL,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    displayConfig: DisplayConfig = DisplayConfig(),
    onPointInspected: (GlucosePoint?) -> Unit = {},
    onLogEntryClicked: (LogRecord) -> Unit = {},
    onWindowChanged: (startTime: Long, endTime: Long, isPast: Boolean) -> Unit = { _, _, _ -> },
    onNavigateDays: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current
    val gridColor = clinicalColors.graphGrid
    val targetShade = clinicalColors.targetRangeShade
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    var inspectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var inspectedPreviousPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var zoomMultiplier by remember { mutableFloatStateOf(1.0f) }
    var scrollOffsetMillis by remember { mutableLongStateOf(0L) }

    // Reset zoom and pan when user selects a different time range pill
    LaunchedEffect(timeRange) {
        zoomMultiplier = 1.0f
        scrollOffsetMillis = 0L
        inspectedPoint = null
        inspectedPreviousPoint = null
        onPointInspected(null)
    }

    val now = System.currentTimeMillis()
    val windowDurationMillis = (timeRange.durationMillis / zoomMultiplier).toLong().coerceIn(1_800_000L, 14 * 86_400_000L)
    val windowEndTime = now - scrollOffsetMillis
    val windowStartTime = windowEndTime - windowDurationMillis

    LaunchedEffect(windowStartTime, windowEndTime, scrollOffsetMillis) {
        onWindowChanged(windowStartTime, windowEndTime, scrollOffsetMillis > 60_000L)
    }

    // Filter points in the active window (with a slight margin for continuous curves)
    val visiblePoints = remember(readings, windowStartTime, windowEndTime, displayConfig) {
        readings.filter { pt ->
            pt.timestamp in (windowStartTime - 15 * 60 * 1000L)..(windowEndTime + 10 * 60 * 1000L) &&
                when {
                    pt.isScan -> displayConfig.showScans || (pt.isCalibrated && displayConfig.showCalibratedScans)
                    pt.isHistory -> displayConfig.showHistory || (pt.isCalibrated && displayConfig.showCalibratedHistory)
                    else -> displayConfig.showStream || (pt.isCalibrated && displayConfig.showCalibratedStream)
                }
        }
    }

    // Determine dynamic Y range
    val minY = 40f
    val highestReading = visiblePoints.maxOfOrNull { it.valueMgDl } ?: 200f
    val maxY = max(260f, (highestReading + 30f))

    val boxModifier = if (modifier == Modifier) {
        Modifier.fillMaxWidth().height(290.dp)
    } else {
        modifier
    }

    Box(
        modifier = boxModifier
            .background(surfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        if (readings.isEmpty()) {
            // Friendly empty state when no sensor data is available
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.status_in_range),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No readings loaded. Connect or scan sensor to display curve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(visiblePoints, windowStartTime, windowEndTime, windowDurationMillis) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val paddingLeft = 16f
                            val paddingRight = 72f
                            val chartWidth = (size.width - paddingLeft - paddingRight).coerceAtLeast(10f)

                            var totalDragX = 0f
                            var isMultiTouch = false
                            var hasPanned = false

                            val initialPt = findClosestPoint(
                                down.position.x,
                                chartWidth,
                                paddingLeft,
                                windowStartTime,
                                windowEndTime,
                                visiblePoints
                            )
                            inspectedPoint = initialPt
                            inspectedPreviousPoint = findPreviousPoint(initialPt, visiblePoints)
                            onPointInspected(initialPt)

                            do {
                                val event = awaitPointerEvent()
                                val count = event.changes.size

                                if (count >= 2) {
                                    isMultiTouch = true
                                    hasPanned = true
                                    val p1 = event.changes[0]
                                    val p2 = event.changes[1]

                                    val prevDist = distance(p1.previousPosition, p2.previousPosition)
                                    val curDist = distance(p1.position, p2.position)

                                    if (prevDist > 8f && curDist > 8f) {
                                        val zoomChange = curDist / prevDist
                                        zoomMultiplier = (zoomMultiplier * zoomChange).coerceIn(0.25f, 5.0f)
                                    }

                                    val avgPanX = ((p1.position.x - p1.previousPosition.x) + (p2.position.x - p2.previousPosition.x)) / 2f
                                    val timeDelta = (-(avgPanX / chartWidth) * windowDurationMillis).toLong()
                                    scrollOffsetMillis = (scrollOffsetMillis + timeDelta).coerceAtLeast(0L)

                                    p1.consume()
                                    p2.consume()
                                } else if (!isMultiTouch && count == 1) {
                                    val change = event.changes[0]
                                    val dx = change.position.x - change.previousPosition.x
                                    totalDragX += abs(dx)

                                    if (totalDragX > 16f) {
                                        hasPanned = true
                                        // 1-finger horizontal pan like the classic Juggluco curve
                                        val timeDelta = (-(dx / chartWidth) * windowDurationMillis).toLong()
                                        scrollOffsetMillis = (scrollOffsetMillis + timeDelta).coerceAtLeast(0L)
                                        change.consume()
                                    } else {
                                        // Scrubbing points on minor drag
                                        if (change.pressed) {
                                            val scrubPt = findClosestPoint(
                                                change.position.x,
                                                chartWidth,
                                                paddingLeft,
                                                windowStartTime,
                                                windowEndTime,
                                                visiblePoints
                                            )
                                            inspectedPoint = scrubPt
                                            inspectedPreviousPoint = findPreviousPoint(scrubPt, visiblePoints)
                                            onPointInspected(scrubPt)
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                val paddingLeft = 16f
                val paddingRight = 72f
                val paddingTop = 20f
                val paddingBottom = 42f

                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom

                fun timeToX(time: Long): Float {
                    val fraction = ((time - windowStartTime).toFloat() / (windowEndTime - windowStartTime).toFloat()).coerceIn(0f, 1f)
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
                val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                drawLine(
                    color = clinicalColors.inRange.copy(alpha = 0.5f),
                    start = Offset(paddingLeft, targetTopY),
                    end = Offset(paddingLeft + chartWidth, targetTopY),
                    strokeWidth = 1.5f,
                    pathEffect = dashedEffect
                )
                drawLine(
                    color = clinicalColors.inRange.copy(alpha = 0.5f),
                    start = Offset(paddingLeft, targetBottomY),
                    end = Offset(paddingLeft + chartWidth, targetBottomY),
                    strokeWidth = 1.5f,
                    pathEffect = dashedEffect
                )

                // 3. Draw Horizontal Grid Lines & Y-Axis Labels
                val yStepMgDl = if (unit == GlucoseUnit.MMOL_L) 36f else 50f
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

                    val labelText = unit.format(gridVal)
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        paddingLeft + chartWidth + 8f,
                        yPos + 4.sp.toPx(),
                        textPaint
                    )
                    gridVal += yStepMgDl
                }

                // Draw Target Threshold Labels on Y-axis
                val targetLabelPaint = Paint().apply {
                    color = clinicalColors.inRange.toArgb()
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    unit.format(targetHigh),
                    paddingLeft + chartWidth + 8f,
                    targetTopY + 4.sp.toPx(),
                    targetLabelPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    unit.format(targetLow),
                    paddingLeft + chartWidth + 8f,
                    targetBottomY + 4.sp.toPx(),
                    targetLabelPaint
                )

                // 4. Draw Vertical Time Grid & X-Axis Labels
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val hoursInWindow = windowDurationMillis / (3600 * 1000f)
                val stepHours = when {
                    hoursInWindow <= 3 -> 1
                    hoursInWindow <= 8 -> 2
                    hoursInWindow <= 16 -> 4
                    hoursInWindow <= 36 -> 6
                    else -> 24
                }
                val stepMillis = stepHours * 3600 * 1000L
                val firstTick = ((windowStartTime / stepMillis) + 1) * stepMillis

                var tickTime = firstTick
                while (tickTime < windowEndTime) {
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
                        xPos - 16f,
                        paddingTop + chartHeight + 18f,
                        textPaint
                    )
                    tickTime += stepMillis
                }

                // 5. Draw Layer: Continuous Real-Time Stream Curve
                val streamPoints = visiblePoints.filter { !it.isScan && !it.isHistory }
                if (displayConfig.showStream && streamPoints.size >= 2) {
                    val segments = ArrayList<List<GlucosePoint>>()
                    var currentSegment = ArrayList<GlucosePoint>()
                    for (i in streamPoints.indices) {
                        val pt = streamPoints[i]
                        if (currentSegment.isNotEmpty()) {
                            val prev = currentSegment.last()
                            if (pt.timestamp - prev.timestamp > 25 * 60 * 1000L) {
                                segments.add(currentSegment)
                                currentSegment = ArrayList()
                            }
                        }
                        currentSegment.add(pt)
                    }
                    if (currentSegment.isNotEmpty()) segments.add(currentSegment)

                    for (seg in segments) {
                        if (seg.size < 2) continue

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

                            val midX = (x0 + x1) / 2f
                            linePath.cubicTo(midX, y0, midX, y1, x1, y1)
                            areaPath.cubicTo(midX, y0, midX, y1, x1, y1)
                        }

                        val lastX = timeToX(seg.last().timestamp)
                        areaPath.lineTo(lastX, paddingTop + chartHeight)
                        areaPath.close()

                        // Soft gradient fill
                        val areaGradient = Brush.verticalGradient(
                            colors = listOf(
                                clinicalColors.inRange.copy(alpha = 0.16f),
                                clinicalColors.inRange.copy(alpha = 0.01f)
                            ),
                            startY = paddingTop,
                            endY = paddingTop + chartHeight
                        )
                        drawPath(path = areaPath, brush = areaGradient)

                        // Smooth curve stroke
                        drawPath(
                            path = linePath,
                            color = clinicalColors.inRange,
                            style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                        )

                        // Muted point dots
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

                            drawCircle(color = surfaceColor, radius = 4.5f, center = Offset(px, py))
                            drawCircle(color = ptColor, radius = 3.0f, center = Offset(px, py))
                        }
                    }
                }

                // 6. Draw Layer: Sensor History (15-min points connected by dashed line)
                val historyPoints = visiblePoints.filter { it.isHistory }
                if (displayConfig.showHistory && historyPoints.isNotEmpty()) {
                    for (i in historyPoints.indices) {
                        val pt = historyPoints[i]
                        val px = timeToX(pt.timestamp)
                        val py = valueToY(pt.valueMgDl)
                        val hColor = Color(0xFF6366F1) // Indigo for history

                        if (i > 0) {
                            val prev = historyPoints[i - 1]
                            if (pt.timestamp - prev.timestamp <= 30 * 60 * 1000L) {
                                val prevX = timeToX(prev.timestamp)
                                val prevY = valueToY(prev.valueMgDl)
                                drawLine(
                                    color = hColor.copy(alpha = 0.5f),
                                    start = Offset(prevX, prevY),
                                    end = Offset(px, py),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                            }
                        }

                        drawCircle(color = surfaceColor, radius = 4f, center = Offset(px, py))
                        drawCircle(color = hColor, radius = 2.8f, center = Offset(px, py))
                    }
                }

                // 7. Draw Layer: NFC Scans (Diamond markers)
                val scanPoints = visiblePoints.filter { it.isScan }
                if (displayConfig.showScans && scanPoints.isNotEmpty()) {
                    for (pt in scanPoints) {
                        val px = timeToX(pt.timestamp)
                        val py = valueToY(pt.valueMgDl)
                        val scanColor = Color(0xFFE11D48) // Rose diamond

                        val diamondPath = Path().apply {
                            moveTo(px, py - 6f)
                            lineTo(px + 6f, py)
                            lineTo(px, py + 6f)
                            lineTo(px - 6f, py)
                            close()
                        }
                        drawPath(path = diamondPath, color = scanColor)
                        drawPath(path = diamondPath, color = surfaceColor, style = Stroke(width = 1.2f))
                    }
                }

                // 8. Draw Layer: Amounts & Meals along Timeline with text tags
                if (displayConfig.showAmounts) {
                    val visibleLogs = logs.filter { it.timestamp in windowStartTime..windowEndTime }
                    val amountTextPaint = Paint().apply {
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                        isFakeBoldText = true
                    }

                    for (log in visibleLogs) {
                        val lx = timeToX(log.timestamp)
                        val ly = paddingTop + chartHeight + 4f
                        val markerColor = when (log.type) {
                            LogType.RAPID_INSULIN -> Color(0xFF2563EB)
                            LogType.BASAL_INSULIN -> Color(0xFF4F46E5)
                            LogType.CARBS, LogType.MEAL -> Color(0xFFD97706)
                            LogType.BLOOD_GLUCOSE -> Color(0xFFB91C1C)
                            LogType.NOTE -> Color(0xFF7C3AED)
                        }

                        drawCircle(color = markerColor, radius = 5.5f, center = Offset(lx, ly))
                        drawCircle(color = surfaceColor, radius = 2.2f, center = Offset(lx, ly))

                        // Draw value tag text
                        val labelText = when (log.type) {
                            LogType.RAPID_INSULIN -> "${log.value.toInt()}U"
                            LogType.BASAL_INSULIN -> "${log.value.toInt()}B"
                            LogType.CARBS, LogType.MEAL -> "${log.value.toInt()}g"
                            LogType.BLOOD_GLUCOSE -> unit.format(log.value)
                            LogType.NOTE -> "•"
                        }
                        amountTextPaint.color = markerColor.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            lx - 10f,
                            ly + 16f,
                            amountTextPaint
                        )
                    }
                }

                // 9. Interactive Draggable Crosshair Line
                val selected = inspectedPoint
                if (selected != null) {
                    val sx = timeToX(selected.timestamp)
                    val sy = valueToY(selected.valueMgDl)

                    drawLine(
                        color = textPrimary.copy(alpha = 0.6f),
                        start = Offset(sx, paddingTop),
                        end = Offset(sx, paddingTop + chartHeight),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    val statusColor = when (selected.status) {
                        GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
                        GlucoseStatus.LOW -> clinicalColors.low
                        GlucoseStatus.IN_RANGE -> clinicalColors.inRange
                        GlucoseStatus.HIGH -> clinicalColors.high
                        GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
                    }
                    drawCircle(color = statusColor.copy(alpha = 0.25f), radius = 10f, center = Offset(sx, sy))
                    drawCircle(color = surfaceColor, radius = 6f, center = Offset(sx, sy))
                    drawCircle(color = statusColor, radius = 4.5f, center = Offset(sx, sy))
                }
            }

            // 10. Floating Detailed Inspection Tooltip Card
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .clickable {
                            inspectedPoint = null
                            inspectedPreviousPoint = null
                            onPointInspected(null)
                        }
                ) {
                    RowDetailedInspection(
                        selected = selected,
                        previousPoint = inspectedPreviousPoint,
                        unit = unit,
                        displayConfig = displayConfig,
                        statusColor = statusColor
                    )
                }
            }

            // 11. Quick Day Edge Jump Buttons (Signature Landscape Feature)
            if (onNavigateDays != null) {
                IconButton(
                    onClick = { onNavigateDays(-1) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.day_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { onNavigateDays(1) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.day_later),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 12. Floating "Jump to Now" Pill
            if (scrollOffsetMillis > 0L) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 48.dp)
                        .clickable {
                            scrollOffsetMillis = 0L
                            zoomMultiplier = 1.0f
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.jump_to_now),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowDetailedInspection(
    selected: GlucosePoint,
    previousPoint: GlucosePoint?,
    unit: GlucoseUnit,
    displayConfig: DisplayConfig,
    statusColor: Color
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val trend = TrendArrow.fromRate(selected.rate)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
            text = selected.formatted(unit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
        if (!displayConfig.minimalistUnits) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (trend != TrendArrow.UNKNOWN && trend != TrendArrow.STABLE) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = trend.symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }

        // Calculate Delta from preceding reading
        if (previousPoint != null) {
            val deltaMgDl = selected.valueMgDl - previousPoint.valueMgDl
            val deltaMin = max(1L, (selected.timestamp - previousPoint.timestamp) / 60_000L)
            val deltaFormatted = if (deltaMgDl >= 0) "+${unit.format(deltaMgDl)}" else unit.format(deltaMgDl)
            Text(
                text = "  •  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "Δ $deltaFormatted (${deltaMin}m)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Rate of change if notable
        if (abs(selected.rate) >= 0.2f) {
            val rateFormatted = if (selected.rate >= 0) "+${unit.formatRate(selected.rate)}" else unit.formatRate(selected.rate)
            val rateSuffix = if (!displayConfig.minimalistUnits) {
                " " + if (unit == GlucoseUnit.MMOL_L) stringResource(R.string.rate_unit_mmol) else stringResource(R.string.rate_unit_mgdl)
            } else "/min"
            Text(
                text = " ($rateFormatted$rateSuffix)",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }
    }
}

private fun distance(p1: Offset, p2: Offset): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}

private fun findClosestPoint(
    touchX: Float,
    chartWidth: Float,
    paddingLeft: Float,
    startTime: Long,
    endTime: Long,
    points: List<GlucosePoint>
): GlucosePoint? {
    if (points.isEmpty()) return null
    val fraction = ((touchX - paddingLeft) / chartWidth).coerceIn(0f, 1f)
    val targetTime = startTime + (fraction * (endTime - startTime)).toLong()

    return points.minByOrNull { abs(it.timestamp - targetTime) }
}

private fun findPreviousPoint(
    current: GlucosePoint?,
    points: List<GlucosePoint>
): GlucosePoint? {
    if (current == null || points.isEmpty()) return null
    return points
        .filter { it.timestamp < current.timestamp }
        .maxByOrNull { it.timestamp }
}
