package tk.glucodata.ui.graph

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.ui.theme.ClinicalColors
import tk.glucodata.ui.theme.LocalClinicalColors
import tk.glucodata.ui.theme.LocalLogbookColors
import tk.glucodata.ui.theme.LogbookColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val STREAM_GAP_MILLIS = 25 * 60 * 1000L
private const val HISTORY_GAP_MILLIS = 35 * 60 * 1000L
private const val HOUR_MILLIS = 3600 * 1000L

/**
 * Interactive glucose graph.
 *
 * The visible window lives in [GraphViewportState] and is read **only from the draw phase**, so
 * panning, flinging and zooming invalidate drawing alone - no recomposition of this composable or
 * of the screen hosting it. All point data is pre-bucketed into primitive arrays
 * ([GraphRenderData]) and sliced with binary search, so a frame costs work proportional to what is
 * actually on screen rather than to the size of the whole dataset.
 */
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
    val logbookColors = LocalLogbookColors.current
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val dayLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)
    val targetRangeColor = clinicalColors.inRange.copy(alpha = 0.10f)
    val haptic = LocalHapticFeedback.current

    val renderData by rememberGraphRenderData(readings, logs)

    LaunchedEffect(renderData) {
        viewportState.oldestDataMillis = renderData.oldestTime
    }

    var inspected by remember { mutableStateOf<InspectedReading?>(null) }
    // Dropping the selection when the data set changes keeps the scrubber from pointing at nothing
    LaunchedEffect(renderData) { inspected = null }

    val paints = remember(textSecondary, clinicalColors, density) {
        GraphPaints(density, textSecondary, clinicalColors)
    }
    val scratch = remember { GraphScratch() }

    // --- Y axis -------------------------------------------------------------------------------
    // Snapped to a coarse ladder so the scale does not twitch while panning, then animated so the
    // rare step change glides instead of jumping. Read from the draw phase only.
    val axisTarget by remember(renderData, displayConfig, targetHigh) {
        derivedStateOf {
            val highest = renderData.maxValueBetween(
                viewportState.startTimeMillis,
                viewportState.endTimeMillis,
                displayConfig
            )
            axisCeiling(max(highest, targetHigh + 20f))
        }
    }
    val axisMax = remember { Animatable(axisTarget) }
    LaunchedEffect(axisTarget) {
        if (axisMax.value == 0f) axisMax.snapTo(axisTarget)
        else axisMax.animateTo(axisTarget, tween(320, easing = FastOutSlowInEasing))
    }

    val currentDisplayConfig by rememberUpdatedState(displayConfig)
    val currentRenderData by rememberUpdatedState(renderData)
    val currentOnLogEntryClicked by rememberUpdatedState(onLogEntryClicked)
    val decaySpec = remember(density) { androidx.compose.animation.core.exponentialDecay<Float>(frictionMultiplier = 1.4f) }

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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed-height header: it is always present, so navigating in time never shifts the
            // graph (or anything below it) by a single pixel.
            GraphHeader(
                viewportState = viewportState,
                inspected = inspected,
                unit = unit,
                clinicalColors = clinicalColors,
                minimalistUnits = displayConfig.minimalistUnits,
                onDismissInspection = { inspected = null }
            )

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
                                viewportState.stopAnimation()
                                val down = awaitFirstDown(requireUnconsumed = false)
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)

                                val metrics = ChartMetrics(size.width.toFloat(), size.height.toFloat(), this)
                                val chartWidth = metrics.chartWidth

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
                                            val focalFraction =
                                                ((curMidX - metrics.chartLeft) / chartWidth).coerceIn(0f, 1f)
                                            val focalTime = viewportState.startTimeMillis +
                                                (focalFraction * viewportState.durationMillis).toLong()
                                            viewportState.zoomBy(curDist / prevDist, focalTime)
                                            viewportState.panByPixels(curMidX - prevMidX, chartWidth)
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
                                            viewportState.panByPixels(dx, chartWidth)
                                            change.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (!isHorizontalDrag && !isMultiTouch &&
                                    totalDragX < touchSlop && totalDragY < touchSlop
                                ) {
                                    handleTap(
                                        touchX = down.position.x,
                                        touchY = down.position.y,
                                        metrics = metrics,
                                        viewportState = viewportState,
                                        data = currentRenderData,
                                        config = currentDisplayConfig,
                                        currentSelection = inspected,
                                        onLogTapped = {
                                            currentOnLogEntryClicked(it)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onSelectionChanged = { selection ->
                                            if (selection != null && selection.timestamp != inspected?.timestamp) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            inspected = selection
                                        }
                                    )
                                } else if (isHorizontalDrag && !isMultiTouch) {
                                    viewportState.fling(
                                        velocityTracker.calculateVelocity().x,
                                        chartWidth,
                                        decaySpec
                                    )
                                }
                            }
                        }
                ) {
                    // Every state read below happens in the draw phase: panning re-runs this lambda
                    // and nothing else.
                    val windowStart = viewportState.startTimeMillis
                    val windowEnd = viewportState.endTimeMillis
                    val windowDuration = viewportState.durationMillis
                    val data = renderData
                    val maxY = axisMax.value
                    val minY = AXIS_MIN

                    val metrics = ChartMetrics(size.width, size.height, this)
                    val chart = ChartTransform(metrics, windowStart, windowEnd, minY, maxY, this)

                    drawTargetBand(chart, targetLow, targetHigh, targetRangeColor)
                    drawValueAxis(chart, unit, targetLow, targetHigh, gridColor, paints, clinicalColors)
                    drawTimeAxis(chart, windowDuration, gridColor, dayLineColor, paints)

                    if (displayConfig.showHistory) {
                        drawPointLayer(chart, data.history, HISTORY_COLOR, scratch)
                    }
                    if (displayConfig.showCalibratedHistory) {
                        drawPointLayer(chart, data.calibratedHistory, CALIBRATED_COLOR, scratch)
                    }
                    if (displayConfig.showStream) {
                        drawCurveLayer(
                            chart = chart,
                            series = data.stream,
                            clinicalColors = clinicalColors,
                            targetLow = targetLow,
                            targetHigh = targetHigh,
                            surfaceColor = surfaceColor,
                            scratch = scratch,
                            showHead = true
                        )
                    }
                    if (displayConfig.showCalibratedStream) {
                        drawDashedCurve(chart, data.calibratedStream, CALIBRATED_COLOR, scratch)
                    }
                    if (displayConfig.showScans) {
                        drawScanLayer(chart, data.scans, SCAN_COLOR, surfaceColor, scratch)
                    }
                    if (displayConfig.showCalibratedScans) {
                        drawScanLayer(chart, data.calibratedScans, CALIBRATED_COLOR, surfaceColor, scratch)
                    }
                    if (displayConfig.showAmounts) {
                        drawEventStrip(chart, data.events, unit, surfaceColor, paints, logbookColors)
                    }

                    inspected?.let { drawScrubber(chart, it, clinicalColors, textPrimary, surfaceColor) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------

/**
 * Single-line header above the plot showing which day is on screen (the whole point of it: when you
 * jump back a few days you can still tell where you are) and the live/jump control. Both states
 * occupy the same fixed height, so the graph below never moves.
 */
@Composable
private fun GraphHeader(
    viewportState: GraphViewportState,
    inspected: InspectedReading?,
    unit: GlucoseUnit,
    clinicalColors: ClinicalColors,
    minimalistUnits: Boolean,
    onDismissInspection: () -> Unit
) {
    val dayKey = viewportState.dayKey
    val isLive = viewportState.isLive
    val locale = Locale.getDefault()
    val windowLabel = remember(dayKey, isLive, locale) {
        Snapshot.withoutReadObservation {
            formatWindowLabel(viewportState.startTimeMillis, viewportState.endTimeMillis, isLive, locale)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inspected != null) {
            InspectionSummary(
                reading = inspected,
                unit = unit,
                clinicalColors = clinicalColors,
                minimalistUnits = minimalistUnits,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(28.dp)
                    .clickableNoRipple(onDismissInspection)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.closename),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!isLive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = windowLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = windowLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
            LiveIndicator(
                isLive = isLive,
                clinicalColors = clinicalColors,
                onJumpToNow = { viewportState.jumpToNow() }
            )
        }
    }
}

@Composable
private fun LiveIndicator(
    isLive: Boolean,
    clinicalColors: ClinicalColors,
    onJumpToNow: () -> Unit
) {
    if (isLive) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(clinicalColors.inRange, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.live_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.clickableNoRipple(onJumpToNow)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.now),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun InspectionSummary(
    reading: InspectedReading,
    unit: GlucoseUnit,
    clinicalColors: ClinicalColors,
    minimalistUnits: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val timeLabel = remember(reading.timestamp, locale) {
        val sameDay = isSameDay(reading.timestamp, System.currentTimeMillis())
        val pattern = if (sameDay) "HH:mm" else "MMM d, HH:mm"
        SimpleDateFormat(pattern, locale).format(Date(reading.timestamp))
    }
    val statusColor = statusColor(reading.statusOrdinal, clinicalColors)
    val trend = TrendArrow.fromRate(reading.ratePerMinute)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = unit.format(reading.valueMgDl),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
        if (!minimalistUnits) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (trend != TrendArrow.UNKNOWN && trend != TrendArrow.STABLE) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = trend.symbol,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        if (reading.hasDelta) {
            Spacer(modifier = Modifier.width(8.dp))
            val delta = reading.valueMgDl - reading.previousValueMgDl
            val sign = if (delta >= 0f) "+" else "-"
            Text(
                text = "Δ$sign${unit.format(abs(delta))} · ${reading.deltaMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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
            text = stringResource(R.string.graph_no_data_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.graph_no_data_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------------------------

/** Plot rectangle. The right gutter holds the value axis; grid lines run into its labels. */
private class ChartMetrics(val width: Float, val height: Float, density: Density) {
    val gutterWidth = with(density) { 42.dp.toPx() }
    val chartLeft = with(density) { 8.dp.toPx() }
    val chartTop = with(density) { 10.dp.toPx() }
    val chartRight = width - gutterWidth
    val chartBottom = height - with(density) { 26.dp.toPx() }
    val chartWidth get() = (chartRight - chartLeft).coerceAtLeast(1f)
    val chartHeight get() = (chartBottom - chartTop).coerceAtLeast(1f)
    val labelAnchorX = width - with(density) { 6.dp.toPx() }
    val labelGap = with(density) { 4.dp.toPx() }
}

/** Maps time and glucose values onto the plot; carries the density for dp-correct stroke sizes. */
private class ChartTransform(
    val metrics: ChartMetrics,
    val startTime: Long,
    val endTime: Long,
    val minValue: Float,
    val maxValue: Float,
    val scope: DrawScope
) {
    val spanMillis = (endTime - startTime).coerceAtLeast(1L)

    fun x(time: Long): Float =
        metrics.chartLeft + ((time - startTime).toFloat() / spanMillis) * metrics.chartWidth

    fun xClamped(time: Long): Float = x(time).coerceIn(metrics.chartLeft, metrics.chartRight)

    fun y(value: Float): Float {
        val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        return metrics.chartBottom - fraction * metrics.chartHeight
    }

    fun timeAt(x: Float): Long =
        startTime + (((x - metrics.chartLeft) / metrics.chartWidth).coerceIn(0f, 1f) * spanMillis).toLong()

    fun px(dp: Float): Float = with(scope) { dp.dp.toPx() }
}

// ---------------------------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawTargetBand(
    chart: ChartTransform,
    targetLow: Float,
    targetHigh: Float,
    bandColor: Color
) {
    val topY = chart.y(targetHigh)
    val bottomY = chart.y(targetLow)
    drawRect(
        color = bandColor,
        topLeft = Offset(chart.metrics.chartLeft, topY),
        size = Size(chart.metrics.chartWidth, (bottomY - topY).coerceAtLeast(1f))
    )
}

/**
 * Right-hand value axis with right-aligned labels.
 * Each line (both grid lines and dashed target boundary lines) extends continuously
 * across the chart up to the start of its own number.
 */
private fun DrawScope.drawValueAxis(
    chart: ChartTransform,
    unit: GlucoseUnit,
    targetLow: Float,
    targetHigh: Float,
    gridColor: Color,
    paints: GraphPaints,
    clinicalColors: ClinicalColors
) {
    val metrics = chart.metrics
    val step = if (unit == GlucoseUnit.MMOL_L) 36f else 50f // 2 mmol/L or 50 mg/dL
    val strokeWidth = chart.px(0.8f)
    val baseline = paints.axisBaselineOffset

    var value = ceil(chart.minValue / step) * step
    if (value == chart.minValue) value += step
    while (value < chart.maxValue) {
        // Skip grid lines that would sit directly on top of a target boundary label
        if (abs(value - targetLow) > step * 0.35f && abs(value - targetHigh) > step * 0.35f) {
            val y = chart.y(value)
            val label = unit.format(value)
            val labelWidth = paints.axis.measureText(label)
            val lineEnd = (metrics.labelAnchorX - labelWidth - metrics.labelGap)
                .coerceAtLeast(metrics.chartLeft + 1f)
            drawLine(gridColor, Offset(metrics.chartLeft, y), Offset(lineEnd, y), strokeWidth)
            drawContext.canvas.nativeCanvas.drawText(label, metrics.labelAnchorX, y + baseline, paints.axis)
        }
        value += step
    }

    drawAxisTargetBoundary(chart, targetHigh, unit.format(targetHigh), paints, clinicalColors)
    drawAxisTargetBoundary(chart, targetLow, unit.format(targetLow), paints, clinicalColors)
}

private fun DrawScope.drawAxisTargetBoundary(
    chart: ChartTransform,
    targetValue: Float,
    label: String,
    paints: GraphPaints,
    clinicalColors: ClinicalColors
) {
    val metrics = chart.metrics
    val y = chart.y(targetValue)
    val labelWidth = paints.target.measureText(label)
    val lineEnd = (metrics.labelAnchorX - labelWidth - metrics.labelGap).coerceAtLeast(metrics.chartLeft + 1f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(chart.px(6f), chart.px(4f)), 0f)
    val targetLineColor = clinicalColors.inRange.copy(alpha = 0.65f)

    drawLine(
        color = targetLineColor,
        start = Offset(metrics.chartLeft, y),
        end = Offset(lineEnd, y),
        strokeWidth = chart.px(1.2f),
        pathEffect = dash
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        metrics.labelAnchorX,
        y + paints.axisBaselineOffset,
        paints.target
    )
}

/** Adaptive time grid. Midnight gets a stronger line and a date label instead of "00:00". */
private fun DrawScope.drawTimeAxis(
    chart: ChartTransform,
    windowDuration: Long,
    gridColor: Color,
    dayLineColor: Color,
    paints: GraphPaints
) {
    val metrics = chart.metrics
    val hours = windowDuration / HOUR_MILLIS.toFloat()
    val stepHours = when {
        hours <= 1.5f -> 0.25f
        hours <= 3.5f -> 1f
        hours <= 8f -> 2f
        hours <= 16f -> 3f
        hours <= 30f -> 6f
        hours <= 80f -> 12f
        hours <= 200f -> 24f
        else -> 48f
    }
    val stepMillis = (stepHours * HOUR_MILLIS).toLong()
    val showTimeOfDay = hours <= 80f

    val cal = Calendar.getInstance()
    cal.timeInMillis = chart.startTime
    // Snap to the local-time grid, so ticks land on whole hours in the user's zone
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.SECOND, 0)
    if (stepMillis >= HOUR_MILLIS) cal.set(Calendar.MINUTE, 0)
    if (stepMillis >= 24 * HOUR_MILLIS) cal.set(Calendar.HOUR_OF_DAY, 0)

    var tick = cal.timeInMillis
    while (tick < chart.startTime) tick += stepMillis
    val labelY = metrics.chartBottom + paints.timeLabelOffset
    val gridStroke = chart.px(0.8f)
    val dayStroke = chart.px(1.2f)
    var guard = 0

    while (tick <= chart.endTime && guard++ < 256) {
        val x = chart.x(tick)
        val isMidnight = isMidnight(tick, cal)
        drawLine(
            color = if (isMidnight) dayLineColor else gridColor,
            start = Offset(x, metrics.chartTop),
            end = Offset(x, metrics.chartBottom),
            strokeWidth = if (isMidnight) dayStroke else gridStroke
        )

        val label: String
        val paint: Paint
        if (isMidnight || !showTimeOfDay) {
            label = paints.dayFormat.format(Date(tick))
            paint = paints.dayLabel
        } else {
            label = paints.timeFormat.format(Date(tick))
            paint = paints.time
        }
        val half = paint.measureText(label) / 2f
        val labelX = x.coerceIn(metrics.chartLeft + half, metrics.chartRight - half)
        drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, paint)
        tick += stepMillis
    }
}

/**
 * The main sensor curve. Two modes: a smoothed spline while individual readings are resolvable,
 * and a per-pixel min/max envelope once the window holds more points than the chart has columns -
 * which keeps multi-day views both honest (peaks survive) and cheap.
 */
private fun DrawScope.drawCurveLayer(
    chart: ChartTransform,
    series: GraphSeries,
    clinicalColors: ClinicalColors,
    targetLow: Float,
    targetHigh: Float,
    surfaceColor: Color,
    scratch: GraphScratch,
    showHead: Boolean
) {
    if (series.isEmpty) return
    val from = (series.firstIndexAtOrAfter(chart.startTime) - 1).coerceAtLeast(0)
    val to = (series.lastIndexAtOrBefore(chart.endTime) + 1).coerceAtMost(series.size - 1)
    if (to < from) return

    val brushes = scratch.zoneBrushes(chart, clinicalColors, targetLow, targetHigh)
    val visibleCount = to - from + 1

    if (visibleCount > chart.metrics.chartWidth * 1.2f) {
        drawEnvelope(chart, series, from, to, brushes, scratch)
    } else {
        drawSpline(chart, series, from, to, brushes, scratch)
    }

    if (showHead && to == series.size - 1) {
        val headTime = series.times[to]
        if (headTime in chart.startTime..chart.endTime) {
            val hx = chart.x(headTime)
            val hy = chart.y(series.values[to])
            val color = statusColor(series.statuses[to].toInt(), clinicalColors)
            drawCircle(color.copy(alpha = 0.20f), chart.px(6f), Offset(hx, hy))
            drawCircle(surfaceColor, chart.px(3.2f), Offset(hx, hy))
            drawCircle(color, chart.px(2.2f), Offset(hx, hy))
        }
    }
}

private fun DrawScope.drawSpline(
    chart: ChartTransform,
    series: GraphSeries,
    from: Int,
    to: Int,
    brushes: ZoneBrushes,
    scratch: GraphScratch
) {
    val line = scratch.linePath.apply { reset() }
    val area = scratch.areaPath.apply { reset() }
    val bottom = chart.metrics.chartBottom
    var segmentStartX = 0f
    var hasSegment = false
    var prevX = 0f
    var prevY = 0f

    fun closeSegment() {
        if (!hasSegment) return
        area.lineTo(prevX, bottom)
        area.lineTo(segmentStartX, bottom)
        area.close()
        hasSegment = false
    }

    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        val gap = i > from && (series.times[i] - series.times[i - 1] > STREAM_GAP_MILLIS)

        if (!hasSegment || gap) {
            closeSegment()
            line.moveTo(x, y)
            area.moveTo(x, bottom)
            area.lineTo(x, y)
            segmentStartX = x
            hasSegment = true
        } else {
            val midX = (prevX + x) / 2f
            line.cubicTo(midX, prevY, midX, y, x, y)
            area.cubicTo(midX, prevY, midX, y, x, y)
        }
        prevX = x
        prevY = y
    }
    closeSegment()

    drawPath(area, brushes.area)
    drawPath(
        path = line,
        brush = brushes.line,
        style = Stroke(width = chart.px(2.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawEnvelope(
    chart: ChartTransform,
    series: GraphSeries,
    from: Int,
    to: Int,
    brushes: ZoneBrushes,
    scratch: GraphScratch
) {
    val metrics = chart.metrics
    val columns = metrics.chartWidth.toInt().coerceIn(1, GraphScratch.MAX_COLUMNS)
    scratch.prepareColumns(columns)
    val minV = scratch.columnMin
    val maxV = scratch.columnMax
    val sumV = scratch.columnSum
    val counts = scratch.columnCount

    for (i in from..to) {
        val time = series.times[i]
        if (time < chart.startTime || time > chart.endTime) continue
        val fraction = (time - chart.startTime).toFloat() / chart.spanMillis
        val col = (fraction * columns).toInt().coerceIn(0, columns - 1)
        val v = series.values[i]
        if (counts[col] == 0) {
            minV[col] = v
            maxV[col] = v
            sumV[col] = v
        } else {
            if (v < minV[col]) minV[col] = v
            if (v > maxV[col]) maxV[col] = v
            sumV[col] += v
        }
        counts[col]++
    }

    val band = scratch.areaPath.apply { reset() }
    val mean = scratch.linePath.apply { reset() }
    val columnWidth = metrics.chartWidth / columns
    var runStart = -1

    fun flushRun(runEnd: Int) {
        if (runStart < 0) return
        // Upper edge left to right, lower edge back again
        for (c in runStart..runEnd) {
            val x = metrics.chartLeft + (c + 0.5f) * columnWidth
            val y = chart.y(maxV[c])
            if (c == runStart) band.moveTo(x, y) else band.lineTo(x, y)
        }
        for (c in runEnd downTo runStart) {
            val x = metrics.chartLeft + (c + 0.5f) * columnWidth
            band.lineTo(x, chart.y(minV[c]))
        }
        band.close()
        for (c in runStart..runEnd) {
            val x = metrics.chartLeft + (c + 0.5f) * columnWidth
            val y = chart.y(sumV[c] / counts[c])
            if (c == runStart) mean.moveTo(x, y) else mean.lineTo(x, y)
        }
        runStart = -1
    }

    for (c in 0 until columns) {
        if (counts[c] > 0) {
            if (runStart < 0) runStart = c
        } else if (runStart >= 0) {
            flushRun(c - 1)
        }
    }
    if (runStart >= 0) flushRun(columns - 1)

    drawPath(band, brushes.line, alpha = 0.28f)
    drawPath(mean, brushes.line, style = Stroke(width = chart.px(1.6f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 15-minute sensor history: dots joined by a light dashed line. */
private fun DrawScope.drawPointLayer(
    chart: ChartTransform,
    series: GraphSeries,
    color: Color,
    scratch: GraphScratch
) {
    if (series.isEmpty) return
    val from = (series.firstIndexAtOrAfter(chart.startTime) - 1).coerceAtLeast(0)
    val to = (series.lastIndexAtOrBefore(chart.endTime) + 1).coerceAtMost(series.size - 1)
    if (to < from) return

    val path = scratch.dashedPath.apply { reset() }
    var started = false
    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        val gap = i > from && (series.times[i] - series.times[i - 1] > HISTORY_GAP_MILLIS)
        if (!started || gap) path.moveTo(x, y) else path.lineTo(x, y)
        started = true
    }
    drawPath(
        path = path,
        color = color.copy(alpha = 0.55f),
        style = Stroke(
            width = chart.px(1.2f),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(chart.px(4f), chart.px(4f)), 0f)
        )
    )

    // Only mark individual samples when they are far enough apart to stay readable
    val radius = chart.px(1.8f)
    val minSpacing = chart.px(6f)
    var lastX = -Float.MAX_VALUE
    for (i in from..to) {
        val x = chart.x(series.times[i])
        if (x - lastX < minSpacing) continue
        lastX = x
        drawCircle(color, radius, Offset(x, chart.y(series.values[i])))
    }
}

private fun DrawScope.drawDashedCurve(
    chart: ChartTransform,
    series: GraphSeries,
    color: Color,
    scratch: GraphScratch
) {
    if (series.isEmpty) return
    val from = (series.firstIndexAtOrAfter(chart.startTime) - 1).coerceAtLeast(0)
    val to = (series.lastIndexAtOrBefore(chart.endTime) + 1).coerceAtMost(series.size - 1)
    if (to < from) return

    val path = scratch.dashedPath.apply { reset() }
    var started = false
    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        val gap = i > from && (series.times[i] - series.times[i - 1] > STREAM_GAP_MILLIS)
        if (!started || gap) path.moveTo(x, y) else path.lineTo(x, y)
        started = true
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = chart.px(1.8f),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(chart.px(6f), chart.px(4f)), 0f)
        )
    )
}

private fun DrawScope.drawScanLayer(
    chart: ChartTransform,
    series: GraphSeries,
    color: Color,
    surfaceColor: Color,
    scratch: GraphScratch
) {
    if (series.isEmpty) return
    val from = series.firstIndexAtOrAfter(chart.startTime)
    val to = series.lastIndexAtOrBefore(chart.endTime)
    if (to < from) return

    val path = scratch.markerPath
    val r = chart.px(4.5f)
    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        path.reset()
        path.moveTo(x, y - r)
        path.lineTo(x + r, y)
        path.lineTo(x, y + r)
        path.lineTo(x - r, y)
        path.close()
        drawPath(path, color)
        drawPath(path, surfaceColor, style = Stroke(width = chart.px(1f)))
    }
}

private fun DrawScope.drawEventStrip(
    chart: ChartTransform,
    events: GraphEvents,
    unit: GlucoseUnit,
    surfaceColor: Color,
    paints: GraphPaints,
    logbookColors: LogbookColors
) {
    if (events.size == 0) return
    val y = chart.metrics.chartBottom - chart.px(4f)
    val radius = chart.px(4f)
    var index = events.firstIndexAtOrAfter(chart.startTime)
    var lastLabelX = -Float.MAX_VALUE
    val labelSpacing = chart.px(18f)

    while (index < events.size && events.times[index] <= chart.endTime) {
        val record = events.records[index]
        val x = chart.x(record.timestamp)
        val color = logbookColors.colorFor(record.type)
        drawCircle(color, radius, Offset(x, y))
        drawCircle(surfaceColor, radius * 0.4f, Offset(x, y))

        if (x - lastLabelX >= labelSpacing) {
            lastLabelX = x
            paints.event.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                eventLabel(record, unit),
                x,
                y - chart.px(7f),
                paints.event
            )
        }
        index++
    }
}

private fun DrawScope.drawScrubber(
    chart: ChartTransform,
    reading: InspectedReading,
    clinicalColors: ClinicalColors,
    textPrimary: Color,
    surfaceColor: Color
) {
    if (reading.timestamp < chart.startTime || reading.timestamp > chart.endTime) return
    val x = chart.x(reading.timestamp)
    val y = chart.y(reading.valueMgDl)
    drawLine(
        color = textPrimary.copy(alpha = 0.55f),
        start = Offset(x, chart.metrics.chartTop),
        end = Offset(x, chart.metrics.chartBottom),
        strokeWidth = chart.px(1f),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(chart.px(3f), chart.px(3f)), 0f)
    )
    val color = statusColor(reading.statusOrdinal, clinicalColors)
    drawCircle(color.copy(alpha = 0.22f), chart.px(9f), Offset(x, y))
    drawCircle(surfaceColor, chart.px(4.5f), Offset(x, y))
    drawCircle(color, chart.px(3.2f), Offset(x, y))
}

// ---------------------------------------------------------------------------------------------
// Interaction helpers
// ---------------------------------------------------------------------------------------------

@Immutable
private data class InspectedReading(
    val timestamp: Long,
    val valueMgDl: Float,
    val statusOrdinal: Int,
    val previousTimestamp: Long,
    val previousValueMgDl: Float
) {
    val hasDelta: Boolean get() = previousTimestamp > 0L
    val deltaMinutes: Long get() = max(1L, (timestamp - previousTimestamp) / 60_000L)
    val ratePerMinute: Float
        get() = if (!hasDelta) 0f else (valueMgDl - previousValueMgDl) / deltaMinutes.toFloat()
}

private fun handleTap(
    touchX: Float,
    touchY: Float,
    metrics: ChartMetrics,
    viewportState: GraphViewportState,
    data: GraphRenderData,
    config: DisplayConfig,
    currentSelection: InspectedReading?,
    onLogTapped: (LogRecord) -> Unit,
    onSelectionChanged: (InspectedReading?) -> Unit
) {
    val fraction = ((touchX - metrics.chartLeft) / metrics.chartWidth).coerceIn(0f, 1f)
    val start = viewportState.startTimeMillis
    val span = (viewportState.endTimeMillis - start).coerceAtLeast(1L)
    val touchTime = start + (fraction * span).toLong()

    if (config.showAmounts && touchY > metrics.chartBottom - metrics.chartHeight * 0.14f) {
        val log = findNearestEvent(data.events, touchTime, (span * 0.04f).toLong())
        if (log != null) {
            onLogTapped(log)
            return
        }
    }

    val nearest = findNearestReading(data, config, touchTime, (span * 0.05f).toLong())
    onSelectionChanged(
        if (nearest != null && nearest.timestamp == currentSelection?.timestamp) null else nearest
    )
}

private fun findNearestEvent(events: GraphEvents, targetTime: Long, tolerance: Long): LogRecord? {
    if (events.size == 0) return null
    val index = events.firstIndexAtOrAfter(targetTime)
    var best: LogRecord? = null
    var bestDistance = Long.MAX_VALUE
    for (i in (index - 1)..index) {
        if (i < 0 || i >= events.size) continue
        val distance = abs(events.times[i] - targetTime)
        if (distance < bestDistance) {
            bestDistance = distance
            best = events.records[i]
        }
    }
    return if (bestDistance <= tolerance) best else null
}

private fun findNearestReading(
    data: GraphRenderData,
    config: DisplayConfig,
    targetTime: Long,
    tolerance: Long
): InspectedReading? {
    var best: InspectedReading? = null
    var bestDistance = Long.MAX_VALUE

    fun consider(series: GraphSeries, enabled: Boolean) {
        if (!enabled || series.isEmpty) return
        val index = series.firstIndexAtOrAfter(targetTime)
        for (i in (index - 1)..index) {
            if (i < 0 || i >= series.size) continue
            val distance = abs(series.times[i] - targetTime)
            if (distance < bestDistance) {
                bestDistance = distance
                best = InspectedReading(
                    timestamp = series.times[i],
                    valueMgDl = series.values[i],
                    statusOrdinal = series.statuses[i].toInt(),
                    previousTimestamp = if (i > 0) series.times[i - 1] else 0L,
                    previousValueMgDl = if (i > 0) series.values[i - 1] else 0f
                )
            }
        }
    }

    consider(data.stream, config.showStream)
    consider(data.calibratedStream, config.showCalibratedStream)
    consider(data.history, config.showHistory)
    consider(data.calibratedHistory, config.showCalibratedHistory)
    consider(data.scans, config.showScans)
    consider(data.calibratedScans, config.showCalibratedScans)

    return if (bestDistance <= tolerance) best else null
}

private fun distance(p1: Offset, p2: Offset): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}

// ---------------------------------------------------------------------------------------------
// Styling helpers
// ---------------------------------------------------------------------------------------------

private val HISTORY_COLOR = Color(0xFF818CF8) // Indigo
private val CALIBRATED_COLOR = Color(0xFF06B6D4) // Cyan
private val SCAN_COLOR = Color(0xFFF43F5E) // Rose
private const val AXIS_MIN = 40f

/** Reusable native paints; text sizes resolved once per density/colour change. */
private class GraphPaints(density: Density, textColor: Color, clinicalColors: ClinicalColors) {
    private val axisTextSize = with(density) { 10.5.sp.toPx() }
    private val timeTextSize = with(density) { 10.sp.toPx() }
    private val eventTextSize = with(density) { 9.sp.toPx() }

    val axis = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
    }
    val target = Paint().apply {
        isAntiAlias = true
        color = clinicalColors.inRange.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    val time = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = timeTextSize
        textAlign = Paint.Align.CENTER
    }
    val dayLabel = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = timeTextSize
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val event = Paint().apply {
        isAntiAlias = true
        textSize = eventTextSize
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** Vertical offset that centres axis text on its grid line. */
    val axisBaselineOffset = axisTextSize * 0.36f
    val timeLabelOffset = with(density) { 16.dp.toPx() }

    val timeFormat: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dayFormat: SimpleDateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
}

private class ZoneBrushes(val line: Brush, val area: Brush)

/**
 * Per-frame scratch space: paths, envelope column buffers and the cached value-zone gradients.
 * Nothing here is allocated while panning unless the chart geometry or palette actually changed.
 */
private class GraphScratch {
    val linePath = Path()
    val areaPath = Path()
    val dashedPath = Path()
    val markerPath = Path()

    var columnMin = FloatArray(0)
        private set
    var columnMax = FloatArray(0)
        private set
    var columnSum = FloatArray(0)
        private set
    var columnCount = IntArray(0)
        private set

    fun prepareColumns(count: Int) {
        if (columnMin.size < count) {
            columnMin = FloatArray(count)
            columnMax = FloatArray(count)
            columnSum = FloatArray(count)
            columnCount = IntArray(count)
        } else {
            java.util.Arrays.fill(columnCount, 0, count, 0)
        }
        java.util.Arrays.fill(columnCount, 0, count, 0)
    }

    private var brushes: ZoneBrushes? = null
    private var brushTop = Float.NaN
    private var brushBottom = Float.NaN
    private var brushMin = Float.NaN
    private var brushMax = Float.NaN
    private var brushLow = Float.NaN
    private var brushHigh = Float.NaN
    private var brushPalette: ClinicalColors? = null

    /**
     * Gradient that paints the curve in the colour of the range it passes through, built from the
     * current axis mapping. Rebuilt only when the mapping or the palette changes.
     */
    fun zoneBrushes(
        chart: ChartTransform,
        colors: ClinicalColors,
        targetLow: Float,
        targetHigh: Float
    ): ZoneBrushes {
        val top = chart.metrics.chartTop
        val bottom = chart.metrics.chartBottom
        val cached = brushes
        if (cached != null && brushTop == top && brushBottom == bottom &&
            brushMin == chart.minValue && brushMax == chart.maxValue &&
            brushLow == targetLow && brushHigh == targetHigh && brushPalette === colors
        ) {
            return cached
        }

        fun stopAt(value: Float): Float =
            ((chart.y(value) - top) / (bottom - top).coerceAtLeast(1f)).coerceIn(0f, 1f)

        val eps = 0.0008f
        val raw = ArrayList<Pair<Float, Color>>(10)
        raw.add(0f to colors.veryHigh)
        raw.add(stopAt(250f) to colors.veryHigh)
        raw.add((stopAt(250f) + eps) to colors.high)
        raw.add(stopAt(targetHigh) to colors.high)
        raw.add((stopAt(targetHigh) + eps) to colors.inRange)
        raw.add(stopAt(targetLow) to colors.inRange)
        raw.add((stopAt(targetLow) + eps) to colors.low)
        raw.add(stopAt(54f) to colors.low)
        raw.add((stopAt(54f) + eps) to colors.veryLow)
        raw.add(1f to colors.veryLow)

        var previous = 0f
        val stops = Array(raw.size) { index ->
            val (position, color) = raw[index]
            val monotonic = max(previous, min(1f, position))
            previous = monotonic
            monotonic to color
        }
        val areaStops = Array(stops.size) { index ->
            val (position, color) = stops[index]
            position to color.copy(alpha = 0.16f)
        }

        val result = ZoneBrushes(
            line = Brush.verticalGradient(colorStops = stops, startY = top, endY = bottom),
            area = Brush.verticalGradient(colorStops = areaStops, startY = top, endY = bottom)
        )
        brushes = result
        brushTop = top
        brushBottom = bottom
        brushMin = chart.minValue
        brushMax = chart.maxValue
        brushLow = targetLow
        brushHigh = targetHigh
        brushPalette = colors
        return result
    }

    companion object {
        const val MAX_COLUMNS = 2048
    }
}

private fun statusColor(ordinal: Int, colors: ClinicalColors): Color = when (ordinal) {
    0 -> colors.veryLow
    1 -> colors.low
    2 -> colors.inRange
    3 -> colors.high
    else -> colors.veryHigh
}

private fun eventLabel(record: LogRecord, unit: GlucoseUnit): String = when (record.type) {
    LogType.RAPID_INSULIN -> "${record.value.roundToInt()}U"
    LogType.BASAL_INSULIN -> "${record.value.roundToInt()}B"
    LogType.CARBS, LogType.MEAL -> "${record.value.roundToInt()}g"
    LogType.BLOOD_GLUCOSE -> unit.format(record.value)
    LogType.NOTE -> "•"
}

/** Coarse ladder for the top of the value axis, so the scale stays put while panning. */
private fun axisCeiling(highest: Float): Float {
    val ladder = floatArrayOf(200f, 240f, 280f, 320f, 360f, 400f, 450f, 500f, 600f)
    val wanted = highest + 20f
    for (step in ladder) if (wanted <= step) return step
    return ladder.last()
}

private fun isMidnight(timeMillis: Long, cal: Calendar): Boolean {
    cal.timeInMillis = timeMillis
    return cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = a
    val dayA = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = b
    return dayA == cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

/**
 * Human label for the visible window: "Today", "Yesterday", "Mon, 15 Sep" or a range across days.
 */
internal fun formatWindowLabel(
    startMillis: Long,
    endMillis: Long,
    isLive: Boolean,
    locale: Locale
): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = endMillis
    val endDay = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = startMillis
    val startDay = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = System.currentTimeMillis()
    val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)

    val dayFormat = SimpleDateFormat("EEE, d MMM", locale)
    val shortFormat = SimpleDateFormat("d MMM", locale)
    val yearFormat = SimpleDateFormat("EEE, d MMM yyyy", locale)

    fun dayName(dayIndex: Int, timeMillis: Long): String = when (dayIndex) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> {
            val targetCal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
            if (targetCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
                dayFormat.format(Date(timeMillis))
            } else {
                yearFormat.format(Date(timeMillis))
            }
        }
    }

    return when {
        isLive -> "Today · ${shortFormat.format(Date(System.currentTimeMillis()))}"
        startDay == endDay -> {
            val name = dayName(endDay, endMillis)
            if (endDay == today || endDay == today - 1) {
                "$name · ${shortFormat.format(Date(endMillis))}"
            } else {
                name
            }
        }
        else -> "${shortFormat.format(Date(startMillis))} – ${shortFormat.format(Date(endMillis))}"
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
