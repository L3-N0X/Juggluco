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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.theme.LocalClinicalColors
import kotlin.math.abs

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
    var scrubbedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var scrubX by remember { mutableFloatStateOf(-1f) }
    var windowOffsetMillis by remember { mutableLongStateOf(0L) }
    val clinicalColors = LocalClinicalColors.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedHours) {
        windowOffsetMillis = 0L
        scrubbedPoint = null
        scrubX = -1f
    }

    val now = remember(readings) {
        readings.lastOrNull()?.timestamp ?: System.currentTimeMillis()
    }
    val windowDurationMillis = selectedHours * 3600 * 1000L
    val oldestTimestamp = remember(readings) {
        readings.firstOrNull()?.timestamp ?: now
    }
    val maxOffsetMillis = remember(now, oldestTimestamp, windowDurationMillis) {
        (now - windowDurationMillis - oldestTimestamp).coerceAtLeast(0L)
    }

    LaunchedEffect(maxOffsetMillis) {
        if (windowOffsetMillis > maxOffsetMillis) windowOffsetMillis = maxOffsetMillis
    }

    val windowEnd = now - windowOffsetMillis
    val windowStart = windowEnd - windowDurationMillis
    val isLive = windowOffsetMillis <= 0L
    val visibleReadings = remember(readings, windowStart, windowEnd) {
        readings.filter { it.timestamp in windowStart..windowEnd }
    }
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val graphGridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val graphSurfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val graphCrosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val graphHighlightColor = MaterialTheme.colorScheme.primary
    val graphRenderer = remember(density, axisTextColor, clinicalColors) {
        WearGraphChartRenderer(density, axisTextColor, clinicalColors)
    }
    val axisMax = remember(visibleReadings, targetHigh, graphRenderer) {
        graphRenderer.axisCeiling(visibleReadings.maxOfOrNull { it.valueMgDl } ?: targetHigh)
    }

    val latestWindowStart by rememberUpdatedState(windowStart)
    val latestWindowEnd by rememberUpdatedState(windowEnd)
    val latestNow by rememberUpdatedState(now)
    val latestDuration by rememberUpdatedState(windowDurationMillis)
    val latestMaxOffset by rememberUpdatedState(maxOffsetMillis)
    val latestVisible by rememberUpdatedState(visibleReadings)
    val latestScrub by rememberUpdatedState(scrubbedPoint)

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
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
                    if (visibleReadings.isNotEmpty()) {
                        val currentIndex = scrubbedPoint?.let(visibleReadings::indexOf) ?: visibleReadings.lastIndex
                        val newIndex = if (event.verticalScrollPixels > 0) {
                            (currentIndex + 1).coerceAtMost(visibleReadings.lastIndex)
                        } else {
                            (currentIndex - 1).coerceAtLeast(0)
                        }
                        if (newIndex != currentIndex) {
                            scrubbedPoint = visibleReadings[newIndex]
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    true
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WearGraphHeader(
                selectedPoint = scrubbedPoint,
                unit = unit,
                selectedHours = selectedHours,
                windowEnd = windowEnd,
                isLive = isLive,
                clinicalColors = clinicalColors,
                onNow = {
                    windowOffsetMillis = 0L
                    scrubbedPoint = null
                    scrubX = -1f
                }
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
                                    scrubbedPoint = null
                                    scrubX = -1f
                                }
                                if (isPan) {
                                    val chartWidth = size.width.toFloat()
                                    if (chartWidth > 0f && deltaX != 0f && latestMaxOffset > 0L) {
                                        val deltaMillis = (-(deltaX / chartWidth) * latestDuration).toLong()
                                        val newEnd = (latestWindowEnd + deltaMillis)
                                            .coerceIn(latestNow - latestMaxOffset, latestNow)
                                        windowOffsetMillis = (latestNow - newEnd)
                                            .coerceIn(0L, latestMaxOffset)
                                    }
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            if (!isPan && totalDragX < touchSlopPx && totalDragY < touchSlopPx) {
                                val snapshot = latestVisible
                                if (snapshot.isNotEmpty() && size.width > 0) {
                                    val chartLeft = with(density) { 2.dp.toPx() }
                                    val chartRight = size.width - with(density) { 30.dp.toPx() }
                                    val progress = ((down.position.x - chartLeft) / (chartRight - chartLeft).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    val touchedTime = latestWindowStart + (progress * (latestWindowEnd - latestWindowStart)).toLong()
                                    val nearest = snapshot.minByOrNull { abs(it.timestamp - touchedTime) }
                                    if (nearest != null && nearest.timestamp == latestScrub?.timestamp) {
                                        scrubbedPoint = null
                                        scrubX = -1f
                                    } else if (nearest != null) {
                                        scrubbedPoint = nearest
                                        scrubX = down.position.x
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    graphRenderer.draw(
                        scope = this,
                        spec = WearGraphChartSpec(
                            readings = visibleReadings,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            selectedHours = selectedHours,
                            axisMax = axisMax,
                            unit = unit,
                            targetLow = targetLow,
                            targetHigh = targetHigh,
                            clinicalColors = clinicalColors,
                            gridColor = graphGridColor,
                            surfaceColor = graphSurfaceColor,
                            crosshairColor = graphCrosshairColor,
                            highlightColor = graphHighlightColor,
                            selectedPoint = scrubbedPoint.takeIf { scrubX >= 0f }
                        )
                    )
                }
            }

            WearTimeRangeSelector(
                selectedHours = selectedHours,
                onSelected = { selectedHours = it }
            )
        }
    }
}
