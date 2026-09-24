package tk.glucodata.ui.graph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import tk.glucodata.alerts.AlertEvent
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.theme.LocalClinicalColors
import tk.glucodata.ui.theme.LocalLogbookColors
import kotlin.math.abs
import kotlin.math.max

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
    alertEvents: List<AlertEvent> = emptyList(),
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
    val alertColor = MaterialTheme.colorScheme.error
    val alertMarkerColor = MaterialTheme.colorScheme.errorContainer
    val alertMarkerContentColor = MaterialTheme.colorScheme.onErrorContainer
    val haptic = LocalHapticFeedback.current

    val renderData by rememberGraphRenderData(readings, logs, alertEvents)

    LaunchedEffect(renderData) {
        viewportState.oldestDataMillis = renderData.oldestTime
    }

    var inspected by remember { mutableStateOf<InspectedReading?>(null) }
    LaunchedEffect(renderData) { inspected = null }

    val paints = remember(textSecondary, clinicalColors, density) {
        GraphPaints(density, textSecondary, clinicalColors)
    }
    val scratch = remember { GraphScratch() }

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

    val graphModifier = if (modifier == Modifier) {
        Modifier
            .fillMaxWidth()
            .height(310.dp)
    } else {
        modifier
    }

    Column(modifier = graphModifier) {
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
                drawAlertIndicators(
                    chart = chart,
                    events = data.alertEvents,
                    alertColor = alertColor,
                    markerColor = alertMarkerColor,
                    markerContentColor = alertMarkerContentColor
                )
                if (displayConfig.showAmounts) {
                    drawEventStrip(chart, data.events, unit, surfaceColor, paints, logbookColors)
                }

                inspected?.let { drawScrubber(chart, it, clinicalColors, textPrimary, surfaceColor) }
            }
        }
    }
}
