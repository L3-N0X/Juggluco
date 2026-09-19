package tk.glucodata.ui.graph

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tk.glucodata.R
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.ui.theme.ClinicalColors
import tk.glucodata.ui.theme.LocalClinicalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun GlucoseGraph(
    readings: List<GlucosePoint>,
    logs: List<LogRecord> = emptyList(),
    viewportState: GraphViewportState,
    unit: GlucoseUnit = GlucoseUnit.MG_DL,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    displayConfig: DisplayConfig = DisplayConfig(),
    onLogEntryClicked: (LogRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val targetRangeColor = clinicalColors.inRange.copy(alpha = 0.12f)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var inspectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var inspectedPreviousPoint by remember { mutableStateOf<GlucosePoint?>(null) }

    val windowStartTime = viewportState.startTimeMillis
    val windowEndTime = viewportState.endTimeMillis
    val windowDurationMillis = viewportState.durationMillis

    // Filter points in active window
    val visiblePoints = remember(readings, windowStartTime, windowEndTime, displayConfig) {
        val margin = (windowDurationMillis * 0.05f).toLong().coerceIn(10 * 60 * 1000L, 2 * 3600 * 1000L)
        readings.filter { pt ->
            pt.timestamp in (windowStartTime - margin)..(windowEndTime + margin) &&
                when {
                    pt.isScan -> displayConfig.showScans || (pt.isCalibrated && displayConfig.showCalibratedScans)
                    pt.isHistory -> displayConfig.showHistory || (pt.isCalibrated && displayConfig.showCalibratedHistory)
                    pt.isCalibrated -> displayConfig.showCalibratedStream
                    else -> displayConfig.showStream
                }
        }
    }

    // Dynamic Y scale bounds (peaks never clipped)
    val minY = 40f
    val highestReading = visiblePoints.maxOfOrNull { it.valueMgDl } ?: 180f
    val maxY = max(240f, ((highestReading + 30f) / 20f).roundToInt() * 20f)

    val currentViewportState = rememberUpdatedState(viewportState)
    val currentVisiblePoints = rememberUpdatedState(visiblePoints)
    val currentLogs = rememberUpdatedState(logs)

    // Reusable drawing paths to prevent garbage collection stutter during drag/pinch
    val linePath = remember { Path() }
    val areaPath = remember { Path() }
    val dashedPath = remember { Path() }
    val diamondPath = remember { Path() }

    val gridTextPaint = remember(textSecondary) {
        Paint().apply {
            color = textSecondary.toArgb()
            isAntiAlias = true
        }
    }
    val targetHighTextPaint = remember(clinicalColors.inRange) {
        Paint().apply {
            color = clinicalColors.inRange.toArgb()
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val amountTextPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    var flingJob by remember { mutableStateOf<Job?>(null) }

    val cardModifier = if (modifier == Modifier) {
        Modifier
            .fillMaxWidth()
            .height(310.dp)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            if (readings.isEmpty()) {
                EmptyDataState()
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val touchSlop = 8f
                            val velocityTracker = VelocityTracker()

                            awaitEachGesture {
                                flingJob?.cancel()
                                val down = awaitFirstDown(requireUnconsumed = false)
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)

                                val paddingLeft = 8.dp.toPx()
                                val paddingRight = 36.dp.toPx()
                                val chartWidth = (size.width - paddingLeft - paddingRight).coerceAtLeast(10f)

                                var totalDragX = 0f
                                var totalDragY = 0f
                                var isHorizontalDrag = false
                                var isMultiTouch = false

                                var prevDist = 0f
                                var prevMidX = 0f

                                do {
                                    val event = awaitPointerEvent()
                                    val count = event.changes.size

                                    if (count >= 2) {
                                        isMultiTouch = true
                                        isHorizontalDrag = true
                                        val p1 = event.changes[0]
                                        val p2 = event.changes[1]

                                        val curDist = distance(p1.position, p2.position)
                                        val curMidX = (p1.position.x + p2.position.x) / 2f

                                        if (prevDist > 10f && curDist > 10f) {
                                            val zoomChange = curDist / prevDist
                                            val vp = currentViewportState.value
                                            val focalFraction = ((curMidX - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                                            val focalTime = vp.startTimeMillis + (focalFraction * vp.durationMillis).toLong()
                                            vp.zoomBy(zoomChange, focalTime)
                                        }

                                        if (prevDist > 10f) {
                                            val panDx = curMidX - prevMidX
                                            val vp = currentViewportState.value
                                            val timeDelta = (-(panDx / chartWidth) * vp.durationMillis).toLong()
                                            vp.panBy(timeDelta)
                                        }

                                        prevDist = curDist
                                        prevMidX = curMidX

                                        p1.consume()
                                        p2.consume()
                                    } else if (!isMultiTouch && count == 1) {
                                        val change = event.changes[0]
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                                        val dx = change.position.x - change.previousPosition.x
                                        val dy = change.position.y - change.previousPosition.y
                                        totalDragX += abs(dx)
                                        totalDragY += abs(dy)

                                        if (!isHorizontalDrag) {
                                            if (totalDragX > touchSlop && totalDragX > totalDragY * 1.15f) {
                                                isHorizontalDrag = true
                                                change.consume()
                                            }
                                        } else {
                                            val vp = currentViewportState.value
                                            val timeDelta = (-(dx / chartWidth) * vp.durationMillis).toLong()
                                            vp.panBy(timeDelta)
                                            change.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                // Touch release / Tap detection
                                if (!isHorizontalDrag && !isMultiTouch && totalDragX < touchSlop && totalDragY < touchSlop) {
                                    val touchX = down.position.x
                                    val touchY = down.position.y
                                    val vp = currentViewportState.value

                                    val tappedLog = findTappedLog(
                                        touchX,
                                        touchY,
                                        size.height.toFloat(),
                                        chartWidth,
                                        paddingLeft,
                                        vp.startTimeMillis,
                                        vp.endTimeMillis,
                                        currentLogs.value
                                    )
                                    if (tappedLog != null) {
                                        onLogEntryClicked(tappedLog)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        val closest = findClosestPoint(
                                            touchX,
                                            chartWidth,
                                            paddingLeft,
                                            vp.startTimeMillis,
                                            vp.endTimeMillis,
                                            currentVisiblePoints.value
                                        )
                                        if (closest != null) {
                                            if (inspectedPoint?.timestamp == closest.timestamp) {
                                                inspectedPoint = null
                                                inspectedPreviousPoint = null
                                            } else {
                                                inspectedPoint = closest
                                                inspectedPreviousPoint = findPreviousPoint(closest, currentVisiblePoints.value)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        } else {
                                            inspectedPoint = null
                                            inspectedPreviousPoint = null
                                        }
                                    }
                                } else if (isHorizontalDrag && !isMultiTouch) {
                                    // Fling momentum
                                    val velocity = velocityTracker.calculateVelocity()
                                    val vx = velocity.x
                                    if (abs(vx) > 350f) {
                                        flingJob = coroutineScope.launch {
                                            var currentVelocity = vx
                                            while (abs(currentVelocity) > 80f && isActive) {
                                                val stepTimeSec = 0.016f
                                                val vp = currentViewportState.value
                                                val panDeltaPx = currentVelocity * stepTimeSec
                                                val timeDelta = (-(panDeltaPx / chartWidth) * vp.durationMillis).toLong()
                                                vp.panBy(timeDelta)
                                                currentVelocity *= 0.92f
                                                delay(16)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val paddingLeft = 8.dp.toPx()
                    val paddingRight = 36.dp.toPx()
                    val paddingTop = 18.dp.toPx()
                    val paddingBottom = 32.dp.toPx()

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

                    // 1. Draw Target Range Band (Shaded zone with clear contrast)
                    val targetTopY = valueToY(targetHigh)
                    val targetBottomY = valueToY(targetLow)
                    drawRect(
                        color = targetRangeColor,
                        topLeft = Offset(paddingLeft, targetTopY),
                        size = Size(chartWidth, (targetBottomY - targetTopY).coerceAtLeast(1f))
                    )

                    // 2. Draw Target Boundary Lines (Dashed)
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    drawLine(
                        color = clinicalColors.inRange.copy(alpha = 0.65f),
                        start = Offset(paddingLeft, targetTopY),
                        end = Offset(paddingLeft + chartWidth, targetTopY),
                        strokeWidth = 1.5f,
                        pathEffect = dashedEffect
                    )
                    drawLine(
                        color = clinicalColors.inRange.copy(alpha = 0.65f),
                        start = Offset(paddingLeft, targetBottomY),
                        end = Offset(paddingLeft + chartWidth, targetBottomY),
                        strokeWidth = 1.5f,
                        pathEffect = dashedEffect
                    )

                    // 3. Draw Horizontal Grid Lines & Y-Axis Labels (Right-aligned against card edge)
                    val yStepMgDl = if (unit == GlucoseUnit.MMOL_L) 36f else 50f
                    var gridVal = minY + (yStepMgDl - (minY % yStepMgDl))
                    gridTextPaint.textSize = 10.5.sp.toPx()
                    targetHighTextPaint.textSize = 10.5.sp.toPx()

                    while (gridVal < maxY) {
                        val yPos = valueToY(gridVal)
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, yPos),
                            end = Offset(paddingLeft + chartWidth, yPos),
                            strokeWidth = 1f
                        )

                        val labelText = unit.format(gridVal)
                        val textW = gridTextPaint.measureText(labelText)
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            size.width - textW - 3.dp.toPx(),
                            yPos + 4.sp.toPx(),
                            gridTextPaint
                        )
                        gridVal += yStepMgDl
                    }

                    // Y-axis target labels right-aligned
                    val highText = unit.format(targetHigh)
                    val highTextW = targetHighTextPaint.measureText(highText)
                    drawContext.canvas.nativeCanvas.drawText(
                        highText,
                        size.width - highTextW - 3.dp.toPx(),
                        targetTopY + 4.sp.toPx(),
                        targetHighTextPaint
                    )

                    val lowText = unit.format(targetLow)
                    val lowTextW = targetHighTextPaint.measureText(lowText)
                    drawContext.canvas.nativeCanvas.drawText(
                        lowText,
                        size.width - lowTextW - 3.dp.toPx(),
                        targetBottomY + 4.sp.toPx(),
                        targetHighTextPaint
                    )

                    // 4. Adaptive Vertical Time Grid & X-Axis Labels
                    drawTimeAxisGrid(
                        windowStartTime = windowStartTime,
                        windowEndTime = windowEndTime,
                        windowDurationMillis = windowDurationMillis,
                        paddingLeft = paddingLeft,
                        paddingTop = paddingTop,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        gridColor = gridColor,
                        textPaint = gridTextPaint
                    )

                    // 5. Draw Layer: Sensor History (15-min points connected by dashed line)
                    if (displayConfig.showHistory) {
                        val historyPoints = visiblePoints.filter { it.isHistory }
                        drawHistoryLayer(
                            points = historyPoints,
                            timeToX = ::timeToX,
                            valueToY = ::valueToY
                        )
                    }

                    // 6. Draw Layer: Real-Time Stream Curve (Clean continuous spline, live pulse dot at head, no artifact borders)
                    if (displayConfig.showStream) {
                        val streamPoints = visiblePoints.filter { !it.isScan && !it.isHistory && !it.isCalibrated }
                        drawStreamCurve(
                            points = streamPoints,
                            timeToX = ::timeToX,
                            valueToY = ::valueToY,
                            paddingTop = paddingTop,
                            chartHeight = chartHeight,
                            windowDurationMillis = windowDurationMillis,
                            clinicalColors = clinicalColors,
                            surfaceColor = surfaceColor,
                            linePath = linePath,
                            areaPath = areaPath
                        )
                    }

                    // 7. Draw Layer: Calibrated Stream Curve (Clean cyan/teal dashed curve)
                    if (displayConfig.showCalibratedStream) {
                        val caliPoints = visiblePoints.filter { it.isCalibrated && !it.isScan && !it.isHistory }
                        drawCalibratedStreamLayer(
                            points = caliPoints,
                            timeToX = ::timeToX,
                            valueToY = ::valueToY,
                            path = dashedPath
                        )
                    }

                    // 8. Draw Layer: NFC Scans (Rose Diamond Markers)
                    if (displayConfig.showScans) {
                        val scanPoints = visiblePoints.filter { it.isScan }
                        drawScansLayer(
                            points = scanPoints,
                            timeToX = ::timeToX,
                            valueToY = ::valueToY,
                            surfaceColor = surfaceColor,
                            path = diamondPath
                        )
                    }

                    // 9. Draw Layer: Amounts & Meals along Timeline
                    if (displayConfig.showAmounts) {
                        val visibleLogs = logs.filter { it.timestamp in windowStartTime..windowEndTime }
                        amountTextPaint.textSize = 9.sp.toPx()
                        drawAmountsLayer(
                            logs = visibleLogs,
                            unit = unit,
                            timeToX = ::timeToX,
                            paddingTop = paddingTop,
                            chartHeight = chartHeight,
                            surfaceColor = surfaceColor,
                            amountTextPaint = amountTextPaint
                        )
                    }

                    // 10. Draw Interactive Scrubber Guide Line & Point Ring
                    val selected = inspectedPoint
                    if (selected != null) {
                        val sx = timeToX(selected.timestamp)
                        val sy = valueToY(selected.valueMgDl)

                        drawLine(
                            color = textPrimary.copy(alpha = 0.65f),
                            start = Offset(sx, paddingTop),
                            end = Offset(sx, paddingTop + chartHeight),
                            strokeWidth = 1.6f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        val statusColor = when (selected.status) {
                            GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
                            GlucoseStatus.LOW -> clinicalColors.low
                            GlucoseStatus.IN_RANGE -> clinicalColors.inRange
                            GlucoseStatus.HIGH -> clinicalColors.high
                            GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
                        }
                        drawCircle(color = statusColor.copy(alpha = 0.25f), radius = 12f, center = Offset(sx, sy))
                        drawCircle(color = surfaceColor, radius = 6.0f, center = Offset(sx, sy))
                        drawCircle(color = statusColor, radius = 4.5f, center = Offset(sx, sy))
                    }
                }

                // 11. Floating Detailed Inspection HUD Card (Top Center)
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
                    ) {
                        RowDetailedInspection(
                            selected = selected,
                            previousPoint = inspectedPreviousPoint,
                            unit = unit,
                            displayConfig = displayConfig,
                            statusColor = statusColor,
                            onDismiss = {
                                inspectedPoint = null
                                inspectedPreviousPoint = null
                            }
                        )
                    }
                }

                // 12. Floating "Now" Jump Pill (Top Start - guaranteed clear of all axes and curve data)
                if (!viewportState.isLive && inspectedPoint == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 3.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 8.dp)
                            .clickable {
                                viewportState.jumpToNow()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = stringResource(R.string.now),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.now),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDataState() {
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
            text = "No readings available in this period. Swipe or jump to Now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun DrawScope.drawTimeAxisGrid(
    windowStartTime: Long,
    windowEndTime: Long,
    windowDurationMillis: Long,
    paddingLeft: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    gridColor: Color,
    textPaint: Paint
) {
    val hoursInWindow = windowDurationMillis / (3600 * 1000f)
    val stepHours = when {
        hoursInWindow <= 1.5f -> 0.5f
        hoursInWindow <= 3.5f -> 1f
        hoursInWindow <= 8f -> 2f
        hoursInWindow <= 16f -> 4f
        hoursInWindow <= 36f -> 6f
        hoursInWindow <= 72f -> 12f
        hoursInWindow <= 168f -> 24f
        else -> 48f
    }
    val stepMillis = (stepHours * 3600 * 1000L).toLong()
    val firstTick = ((windowStartTime / stepMillis) + 1) * stepMillis

    val timeFormat = if (hoursInWindow <= 36f) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }

    fun timeToX(time: Long): Float {
        val fraction = ((time - windowStartTime).toFloat() / (windowEndTime - windowStartTime).toFloat()).coerceIn(0f, 1f)
        return paddingLeft + (fraction * chartWidth)
    }

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
        val textW = textPaint.measureText(timeStr)
        drawContext.canvas.nativeCanvas.drawText(
            timeStr,
            (xPos - (textW / 2f)).coerceIn(paddingLeft, paddingLeft + chartWidth - textW),
            paddingTop + chartHeight + 18f,
            textPaint
        )
        tickTime += stepMillis
    }
}

private fun DrawScope.drawStreamCurve(
    points: List<GlucosePoint>,
    timeToX: (Long) -> Float,
    valueToY: (Float) -> Float,
    paddingTop: Float,
    chartHeight: Float,
    windowDurationMillis: Long,
    clinicalColors: ClinicalColors,
    surfaceColor: Color,
    linePath: Path,
    areaPath: Path
) {
    if (points.size < 2) return

    // Group into segments split across data gaps (> 25 minutes)
    val segments = ArrayList<List<GlucosePoint>>()
    var curSegment = ArrayList<GlucosePoint>()
    for (i in points.indices) {
        val pt = points[i]
        if (curSegment.isNotEmpty()) {
            val prev = curSegment.last()
            if (pt.timestamp - prev.timestamp > 25 * 60 * 1000L) {
                segments.add(curSegment)
                curSegment = ArrayList()
            }
        }
        curSegment.add(pt)
    }
    if (curSegment.isNotEmpty()) segments.add(curSegment)

    val isUltraZoomed = windowDurationMillis <= 90 * 60 * 1000L

    for (seg in segments) {
        if (seg.size < 2) {
            if (seg.size == 1) {
                val pt = seg[0]
                val px = timeToX(pt.timestamp)
                val py = valueToY(pt.valueMgDl)
                drawCircle(color = clinicalColors.inRange, radius = 3.0f, center = Offset(px, py))
            }
            continue
        }

        linePath.reset()
        areaPath.reset()

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

        // Soft gradient fill under curve
        val areaGradient = Brush.verticalGradient(
            colors = listOf(
                clinicalColors.inRange.copy(alpha = 0.18f),
                clinicalColors.inRange.copy(alpha = 0.01f)
            ),
            startY = paddingTop,
            endY = paddingTop + chartHeight
        )
        drawPath(path = areaPath, brush = areaGradient)

        // Continuous smooth curve stroke
        drawPath(
            path = linePath,
            color = clinicalColors.inRange,
            style = Stroke(width = 3.0f, cap = StrokeCap.Round)
        )

        // Only draw discrete point dots when ultra-zoomed in where points are widely spaced (no border overlap)
        if (isUltraZoomed) {
            var lastPx = -100f
            for (pt in seg) {
                val px = timeToX(pt.timestamp)
                val py = valueToY(pt.valueMgDl)
                if (px - lastPx >= 16f) {
                    val ptColor = when (pt.status) {
                        GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
                        GlucoseStatus.LOW -> clinicalColors.low
                        GlucoseStatus.IN_RANGE -> clinicalColors.inRange
                        GlucoseStatus.HIGH -> clinicalColors.high
                        GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
                    }
                    drawCircle(color = ptColor, radius = 2.2f, center = Offset(px, py))
                    lastPx = px
                }
            }
        }

        // Live reading indicator at the head of the stream (clean pulse dot)
        val latestPt = seg.lastOrNull()
        if (latestPt != null) {
            val lx = timeToX(latestPt.timestamp)
            val ly = valueToY(latestPt.valueMgDl)
            val liveColor = when (latestPt.status) {
                GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
                GlucoseStatus.LOW -> clinicalColors.low
                GlucoseStatus.IN_RANGE -> clinicalColors.inRange
                GlucoseStatus.HIGH -> clinicalColors.high
                GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
            }
            drawCircle(color = liveColor.copy(alpha = 0.20f), radius = 8f, center = Offset(lx, ly))
            drawCircle(color = surfaceColor, radius = 4.5f, center = Offset(lx, ly))
            drawCircle(color = liveColor, radius = 3.0f, center = Offset(lx, ly))
        }
    }
}

private fun DrawScope.drawCalibratedStreamLayer(
    points: List<GlucosePoint>,
    timeToX: (Long) -> Float,
    valueToY: (Float) -> Float,
    path: Path
) {
    if (points.isEmpty()) return
    path.reset()

    val caliColor = Color(0xFF06B6D4) // Cyan / Teal accent for calibrated curve
    var started = false

    for (i in points.indices) {
        val pt = points[i]
        val px = timeToX(pt.timestamp)
        val py = valueToY(pt.valueMgDl)

        if (!started) {
            path.moveTo(px, py)
            started = true
        } else {
            val prev = points[i - 1]
            if (pt.timestamp - prev.timestamp > 25 * 60 * 1000L) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }
    }

    drawPath(
        path = path,
        color = caliColor,
        style = Stroke(
            width = 2.2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
        )
    )
}

private fun DrawScope.drawHistoryLayer(
    points: List<GlucosePoint>,
    timeToX: (Long) -> Float,
    valueToY: (Float) -> Float
) {
    if (points.isEmpty()) return
    val historyColor = Color(0xFF818CF8) // Indigo

    for (i in points.indices) {
        val pt = points[i]
        val px = timeToX(pt.timestamp)
        val py = valueToY(pt.valueMgDl)

        if (i > 0) {
            val prev = points[i - 1]
            if (pt.timestamp - prev.timestamp <= 30 * 60 * 1000L) {
                val prevX = timeToX(prev.timestamp)
                val prevY = valueToY(prev.valueMgDl)
                drawLine(
                    color = historyColor.copy(alpha = 0.55f),
                    start = Offset(prevX, prevY),
                    end = Offset(px, py),
                    strokeWidth = 1.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
        }

        drawCircle(color = historyColor, radius = 2.8f, center = Offset(px, py))
    }
}

private fun DrawScope.drawScansLayer(
    points: List<GlucosePoint>,
    timeToX: (Long) -> Float,
    valueToY: (Float) -> Float,
    surfaceColor: Color,
    path: Path
) {
    if (points.isEmpty()) return
    val scanColor = Color(0xFFF43F5E) // Rose Diamond

    for (pt in points) {
        val px = timeToX(pt.timestamp)
        val py = valueToY(pt.valueMgDl)

        path.reset()
        path.moveTo(px, py - 6f)
        path.lineTo(px + 6f, py)
        path.lineTo(px, py + 6f)
        path.lineTo(px - 6f, py)
        path.close()

        drawPath(path = path, color = scanColor)
        drawPath(path = path, color = surfaceColor, style = Stroke(width = 1.2f))
    }
}

private fun DrawScope.drawAmountsLayer(
    logs: List<LogRecord>,
    unit: GlucoseUnit,
    timeToX: (Long) -> Float,
    paddingTop: Float,
    chartHeight: Float,
    surfaceColor: Color,
    amountTextPaint: Paint
) {
    val ly = paddingTop + chartHeight + 4f

    for (log in logs) {
        val lx = timeToX(log.timestamp)
        val markerColor = when (log.type) {
            LogType.RAPID_INSULIN -> Color(0xFF2563EB)
            LogType.BASAL_INSULIN -> Color(0xFF4F46E5)
            LogType.CARBS, LogType.MEAL -> Color(0xFFD97706)
            LogType.BLOOD_GLUCOSE -> Color(0xFFB91C1C)
            LogType.NOTE -> Color(0xFF7C3AED)
        }

        drawCircle(color = markerColor, radius = 5.5f, center = Offset(lx, ly))
        drawCircle(color = surfaceColor, radius = 2.2f, center = Offset(lx, ly))

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
            lx - 8f,
            ly + 16f,
            amountTextPaint
        )
    }
}

@Composable
private fun RowDetailedInspection(
    selected: GlucosePoint,
    previousPoint: GlucosePoint?,
    unit: GlucoseUnit,
    displayConfig: DisplayConfig,
    statusColor: Color,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val isToday = remember(selected.timestamp) {
        val now = System.currentTimeMillis()
        abs(now - selected.timestamp) < 24 * 3600 * 1000L
    }
    val trend = TrendArrow.fromRate(selected.rate)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isToday) timeFormat.format(Date(selected.timestamp)) else dateFormat.format(Date(selected.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
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

            // Delta from preceding reading
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

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.closename),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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

private fun findTappedLog(
    touchX: Float,
    touchY: Float,
    chartTotalHeight: Float,
    chartWidth: Float,
    paddingLeft: Float,
    startTime: Long,
    endTime: Long,
    logs: List<LogRecord>
): LogRecord? {
    val bottomStripY = chartTotalHeight - 44f
    if (touchY < bottomStripY) return null

    val touchFraction = ((touchX - paddingLeft) / chartWidth).coerceIn(0f, 1f)
    val touchTime = startTime + (touchFraction * (endTime - startTime)).toLong()

    return logs.minByOrNull { abs(it.timestamp - touchTime) }?.takeIf {
        abs(it.timestamp - touchTime) < (endTime - startTime) * 0.05f
    }
}
