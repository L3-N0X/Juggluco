package tk.glucodata.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import tk.glucodata.ui.components.WearGraphChartRenderer
import tk.glucodata.ui.components.WearGraphChartSpec
import tk.glucodata.ui.components.WearGraphHeader
import tk.glucodata.ui.components.WearTimeRangeSelector
import tk.glucodata.ui.components.rememberWearSeries
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.theme.LocalClinicalColors
import kotlin.math.abs

/**
 * Wear glucose graph.
 *
 * The window is split in two on purpose.
 *
 * [panOffset] is read **only** by the canvas' draw lambda, so dragging it invalidates drawing alone -
 * no recomposition of this screen, no rebuilt reading list, no per-frame allocations. It is a
 * snapshot state specifically so that Compose can track that narrow dependency.
 *
 * [settledOffset] is published once when the gesture ends, and that is what the header and the
 * pan limits react to, so the text above the chart updates once per interaction instead of sixty
 * times a second.
 *
 * The visible points are addressed as a binary-searched index range over the prepared
 * `WearSeries` rather than a filtered `List<GlucosePoint>`, so the amount of work in a frame is
 * proportional to what is actually on screen.
 */
@Composable
fun WearGraphScreen(
    repository: GlucoseRepository,
    onBack: () -> Unit
) {
    val readings by repository.readings.collectAsState()
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()

    var selectedHours by remember { mutableIntStateOf(3) }

    // Index of the inspected point, or -1. Kept as an index so scrubbing does not have to search the
    // window for the point it already has.
    var scrubIndex by remember { mutableIntStateOf(-1) }
    val panOffset = remember { mutableLongStateOf(0L) }
    var settledOffset by remember { mutableLongStateOf(0L) }

    val clinicalColors = LocalClinicalColors.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    val series by rememberWearSeries(readings)

    val windowDurationMillis = remember(selectedHours) { selectedHours * 3600 * 1000L }
    val newestTime = remember(series) { series.lastTime }
    val oldestTime = remember(series) { series.firstTime }
    val maxOffsetMillis = remember(newestTime, oldestTime, windowDurationMillis) {
        (newestTime - windowDurationMillis - oldestTime).coerceAtLeast(0L)
    }

    fun resetToLive() {
        panOffset.longValue = 0L
        settledOffset = 0L
        scrubIndex = -1
    }

    LaunchedEffect(selectedHours) {
        resetToLive()
    }

    LaunchedEffect(maxOffsetMillis) {
        if (panOffset.longValue > maxOffsetMillis) {
            panOffset.longValue = maxOffsetMillis
            settledOffset = maxOffsetMillis
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
    }

    // Values the draw lambda and the gesture handler both need, without subscribing them to
    // recomposition.
    val currentNewest by rememberUpdatedState(newestTime)
    val currentMaxOffset by rememberUpdatedState(maxOffsetMillis)
    val currentSeries by rememberUpdatedState(series)
    val currentDuration by rememberUpdatedState(windowDurationMillis)
    val chartSpec = remember { WearGraphChartSpec() }
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val graphGridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val graphSurfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val graphCrosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val graphHighlightColor = MaterialTheme.colorScheme.primary
    val renderer = remember(density, axisTextColor, clinicalColors) {
        WearGraphChartRenderer(
            density = density,
            textColor = axisTextColor,
            clinicalColors = clinicalColors
        )
    }

    val settledWindowEnd = newestTime - settledOffset
    val isLive = settledOffset <= 0L
    val inspectedPoint = remember(series, scrubIndex) {
        if (scrubIndex >= 0 && scrubIndex < series.size) series.pointAt(scrubIndex) else null
    }

    ScreenScaffold(
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent { event ->
                    val data = currentSeries
                    if (!data.isEmpty) {
                        val last = data.lastIndexAtOrBefore(currentNewest - panOffset.longValue)
                        if (last >= 0) {
                            // Start from the newest point, then walk from wherever the crown left us.
                            val current = if (scrubIndex < 0) last else scrubIndex.coerceIn(0, last)
                            val next = if (event.verticalScrollPixels > 0) {
                                (current + 1).coerceAtMost(last)
                            } else {
                                (current - 1).coerceAtLeast(0)
                            }
                            if (next != current || scrubIndex < 0) {
                                scrubIndex = next
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    true
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WearGraphHeader(
                selectedPoint = inspectedPoint,
                unit = unit,
                selectedHours = selectedHours,
                windowEnd = settledWindowEnd,
                isLive = isLive,
                clinicalColors = clinicalColors,
                onNow = { resetToLive() }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val touchSlopPx = with(density) { 12.dp.toPx() }
                            val edgeBackPx = with(density) { 28.dp.toPx() }
                            if (down.position.x < edgeBackPx) return@awaitEachGesture

                            val plotLeftPx = with(density) { 2.dp.toPx() }
                            val plotRightPx = size.width - with(density) { 30.dp.toPx() }
                            val plotWidth = (plotRightPx - plotLeftPx).coerceAtLeast(1f)

                            var totalDragX = 0f
                            var totalDragY = 0f
                            var isPan = false

                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size != 1) break
                                val change = event.changes[0]
                                val deltaX = change.position.x - change.previousPosition.x
                                val deltaY = change.position.y - change.previousPosition.y
                                totalDragX += abs(deltaX)
                                totalDragY += abs(deltaY)

                                if (!isPan && totalDragX > touchSlopPx && totalDragX > totalDragY * 1.15f) {
                                    isPan = true
                                    scrubIndex = -1
                                }
                                if (isPan) {
                                    val maxOffset = currentMaxOffset
                                    if (deltaX != 0f && maxOffset > 0L) {
                                        val deltaMillis = (-(deltaX / plotWidth) * currentDuration).toLong()
                                        val newEnd = (currentNewest - panOffset.longValue + deltaMillis)
                                            .coerceIn(currentNewest - maxOffset, currentNewest)
                                        panOffset.longValue = (currentNewest - newEnd)
                                            .coerceIn(0L, maxOffset)
                                    }
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            if (isPan) {
                                // Publish once the gesture has settled, so the header catches up
                                // without the drag having recomposed the screen on every frame.
                                settledOffset = panOffset.longValue
                            } else if (totalDragX < touchSlopPx && totalDragY < touchSlopPx) {
                                val data = currentSeries
                                if (!data.isEmpty && size.width > 0) {
                                    val windowEnd = currentNewest - panOffset.longValue
                                    val windowStart = windowEnd - currentDuration
                                    val progress = ((down.position.x - plotLeftPx) / plotWidth)
                                        .coerceIn(0f, 1f)
                                    val touchedTime = windowStart + (progress * currentDuration).toLong()
                                    val nearest = data.nearestIndexTo(touchedTime)
                                    if (nearest >= 0) {
                                        if (nearest == scrubIndex) {
                                            scrubIndex = -1
                                        } else {
                                            scrubIndex = nearest
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val data = series
                    val windowEnd = newestTime - panOffset.longValue
                    val windowStart = windowEnd - windowDurationMillis
                    val from = data.firstIndexAtOrAfter(windowStart)
                    val to = data.lastIndexAtOrBefore(windowEnd)

                    chartSpec.series = data
                    chartSpec.fromIndex = from
                    chartSpec.toIndex = to
                    chartSpec.windowStart = windowStart
                    chartSpec.windowEnd = windowEnd
                    chartSpec.selectedHours = selectedHours
                    chartSpec.unit = unit
                    chartSpec.targetLow = targetLow
                    chartSpec.targetHigh = targetHigh
                    chartSpec.clinicalColors = clinicalColors
                    chartSpec.gridColor = graphGridColor
                    chartSpec.surfaceColor = graphSurfaceColor
                    chartSpec.crosshairColor = graphCrosshairColor
                    chartSpec.highlightColor = graphHighlightColor
                    chartSpec.selectedIndex = scrubIndex
                    chartSpec.axisMax = renderer.axisCeiling(
                        data.maxValueBetween(windowStart, windowEnd).coerceAtLeast(targetHigh)
                    )
                    renderer.draw(this, chartSpec)
                }
            }

            WearTimeRangeSelector(
                selectedHours = selectedHours,
                onSelected = { selectedHours = it }
            )
        }
    }
}
