package tk.glucodata.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.wear.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.ClinicalColors
import tk.glucodata.ui.theme.LocalClinicalColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

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
    // How far back the visible window is scrolled from live (0 = following now).
    // Reset on range change so switching 1h/12h always lands back on live data.
    var windowOffsetMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(selectedHours) {
        windowOffsetMillis = 0L
        scrubbedPoint = null
        scrubX = -1f
    }

    val clinical = LocalClinicalColors.current
    val density = LocalDensity.current

    val now = remember(readings) {
        readings.lastOrNull()?.timestamp ?: System.currentTimeMillis()
    }
    val windowDurationMillis = selectedHours * 3600 * 1000L
    val oldestTimestamp = remember(readings) {
        readings.firstOrNull()?.timestamp ?: now
    }
    // Oldest offset that still keeps some data on screen.
    val maxOffsetMillis = remember(now, oldestTimestamp, windowDurationMillis) {
        (now - windowDurationMillis - oldestTimestamp).coerceAtLeast(0L)
    }
    // Keep offset valid when new data arrives or the range changes.
    LaunchedEffect(maxOffsetMillis) {
        if (windowOffsetMillis > maxOffsetMillis) windowOffsetMillis = maxOffsetMillis
    }
    val windowEnd = now - windowOffsetMillis
    val windowStart = windowEnd - windowDurationMillis
    val isLive = windowOffsetMillis <= 0L

    val visibleReadings = remember(readings, windowStart, windowEnd) {
        readings.filter { it.timestamp in windowStart..windowEnd }
    }
    // Latest window values for the gesture handler below. The handler is keyed
    // on Unit so an ongoing drag is never cancelled by the recompositions that
    // panning itself triggers (same pattern as the phone graph's viewport).
    val latestWindowStart by rememberUpdatedState(windowStart)
    val latestWindowEnd by rememberUpdatedState(windowEnd)
    val latestNow by rememberUpdatedState(now)
    val latestDuration by rememberUpdatedState(windowDurationMillis)
    val latestMaxOffset by rememberUpdatedState(maxOffsetMillis)
    val latestVisible by rememberUpdatedState(visibleReadings)
    val latestScrub by rememberUpdatedState(scrubbedPoint)

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val highlightPrimaryColor = MaterialTheme.colorScheme.primary
    val graphGridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val graphSurfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisMax = remember(visibleReadings, targetHigh) {
        wearAxisCeiling(visibleReadings.maxOfOrNull { it.valueMgDl } ?: targetHigh)
    }
    val graphPaints = remember(density, axisTextColor, clinical) {
        WearGraphPaints(density, axisTextColor, clinical)
    }
    val graphScratch = remember { WearGraphScratch() }

    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

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
                        val currentIndex = if (scrubbedPoint != null) {
                            visibleReadings.indexOf(scrubbedPoint)
                        } else {
                            visibleReadings.lastIndex
                        }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(top = 18.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (scrubbedPoint != null) {
                    val p = scrubbedPoint!!
                    val statusColor = when (p.status) {
                        GlucoseStatus.IN_RANGE -> clinical.inRange
                        GlucoseStatus.LOW, GlucoseStatus.HIGH -> clinical.low
                        GlucoseStatus.VERY_LOW, GlucoseStatus.VERY_HIGH -> clinical.veryLow
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = p.formatted(unit),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${unit.symbol} • ${timeFormatter.format(Date(p.timestamp))}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isLive) "History (${selectedHours}h)" else timeFormatter.format(Date(windowEnd)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isLive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NOW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable(
                                        role = Role.Button,
                                        onClick = { windowOffsetMillis = 0L }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Interactive graph: tap inspects a reading, horizontal drag scrolls history.
            // Drags starting at the left edge are left unconsumed so the system
            // swipe-to-dismiss (back) keeps working on round watches.
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
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalDragX += kotlin.math.abs(dx)
                                totalDragY += kotlin.math.abs(dy)

                                if (!isPan) {
                                    if (totalDragX > touchSlopPx && totalDragX > totalDragY * 1.15f) {
                                        isPan = true
                                        scrubbedPoint = null
                                        scrubX = -1f
                                    }
                                }
                                if (isPan) {
                                    val chartWidth = size.width.toFloat()
                                    if (chartWidth > 0f && dx != 0f && latestMaxOffset > 0L) {
                                        val deltaMillis = (-(dx / chartWidth) * latestDuration).toLong()
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
                                    val start = latestWindowStart
                                    val end = latestWindowEnd
                                    val chartLeft = with(density) { 2.dp.toPx() }
                                    val chartRight = size.width - with(density) { 30.dp.toPx() }
                                    val progress = ((down.position.x - chartLeft) / (chartRight - chartLeft).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    val touchedTime = start + (progress * (end - start)).toLong()
                                    val nearest = snapshot.minByOrNull { kotlin.math.abs(it.timestamp - touchedTime) }
                                    if (nearest != null && nearest.timestamp == latestScrub?.timestamp) {
                                        scrubbedPoint = null
                                        scrubX = -1f
                                    } else {
                                        scrubbedPoint = nearest
                                        scrubX = down.position.x
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    if (width <= 0f || height <= 0f) return@Canvas

                    val chartLeft = 2.dp.toPx()
                    val chartTop = 4.dp.toPx()
                    val chartRight = (width - 30.dp.toPx()).coerceAtLeast(chartLeft + 1f)
                    val chartBottom = (height - 17.dp.toPx()).coerceAtLeast(chartTop + 1f)
                    val chartWidth = chartRight - chartLeft
                    val chartHeight = chartBottom - chartTop
                    val windowSpan = (windowEnd - windowStart).coerceAtLeast(1L)
                    val minGl = 40f
                    val maxGl = axisMax.coerceAtLeast(targetHigh + 40f)
                    val rangeGl = maxGl - minGl

                    fun yFor(gl: Float): Float {
                        val fraction = ((gl.coerceIn(minGl, maxGl) - minGl) / rangeGl).coerceIn(0f, 1f)
                        return chartBottom - fraction * chartHeight
                    }

                    fun xFor(timestamp: Long): Float {
                        val progress = ((timestamp - windowStart).toFloat() / windowSpan).coerceIn(0f, 1f)
                        return chartLeft + progress * chartWidth
                    }

                    val yTargetLow = yFor(targetLow)
                    val yTargetHigh = yFor(targetHigh)
                    drawRect(
                        color = clinical.targetRangeShade,
                        topLeft = Offset(chartLeft, yTargetHigh),
                        size = Size(chartWidth, (yTargetLow - yTargetHigh).coerceAtLeast(1f))
                    )

                    val gridColor = graphGridColor
                    val axisX = width - 3.dp.toPx()
                    val step = if (unit == GlucoseUnit.MMOL_L) 36f else 50f
                    var axisValue = ceil(minGl / step) * step
                    while (axisValue <= maxGl) {
                        if (abs(axisValue - targetLow) > step * 0.35f &&
                            abs(axisValue - targetHigh) > step * 0.35f
                        ) {
                            val y = yFor(axisValue)
                            val label = unit.format(axisValue)
                            val labelWidth = graphPaints.axis.measureText(label)
                            drawLine(
                                color = gridColor,
                                start = Offset(chartLeft, y),
                                end = Offset(axisX - labelWidth - 3.dp.toPx(), y),
                                strokeWidth = 0.8.dp.toPx()
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                axisX,
                                y + graphPaints.axisBaselineOffset,
                                graphPaints.axis
                            )
                        }
                        axisValue += step
                    }

                    val targetDash = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 3.dp.toPx()),
                        0f
                    )
                    listOf(targetLow, targetHigh).forEach { value ->
                        val y = yFor(value)
                        val label = unit.format(value)
                        val labelWidth = graphPaints.target.measureText(label)
                        drawLine(
                            color = clinical.inRange.copy(alpha = 0.65f),
                            start = Offset(chartLeft, y),
                            end = Offset(axisX - labelWidth - 3.dp.toPx(), y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = targetDash
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            axisX,
                            y + graphPaints.axisBaselineOffset,
                            graphPaints.target
                        )
                    }

                    val timeStep = when {
                        selectedHours <= 1 -> 15 * 60 * 1000L
                        selectedHours <= 3 -> 60 * 60 * 1000L
                        selectedHours <= 6 -> 2 * 60 * 60 * 1000L
                        else -> 3 * 60 * 60 * 1000L
                    }
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = windowStart
                        set(Calendar.MILLISECOND, 0)
                        set(Calendar.SECOND, 0)
                        if (timeStep >= 60 * 60 * 1000L) set(Calendar.MINUTE, 0)
                    }
                    var tick = calendar.timeInMillis
                    if (tick < windowStart) tick += timeStep
                    var tickGuard = 0
                    while (tick <= windowEnd && tickGuard++ < 32) {
                        val x = xFor(tick)
                        val label = timeFormatter.format(Date(tick))
                        val labelWidth = graphPaints.time.measureText(label)
                        val labelX = x.coerceIn(chartLeft + labelWidth / 2f, chartRight - labelWidth / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(x, chartTop),
                            end = Offset(x, chartBottom),
                            strokeWidth = 0.8.dp.toPx()
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            labelX,
                            height - 3.dp.toPx(),
                            graphPaints.time
                        )
                        tick += timeStep
                    }

                    val curveBrush = wearZoneBrush(
                        top = chartTop,
                        bottom = chartBottom,
                        minValue = minGl,
                        maxValue = maxGl,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        colors = clinical
                    )
                    drawWearCurve(
                        readings = visibleReadings,
                        chartLeft = chartLeft,
                        chartRight = chartRight,
                        chartTop = chartTop,
                        chartBottom = chartBottom,
                        windowStart = windowStart,
                        windowSpan = windowSpan,
                        minValue = minGl,
                        maxValue = maxGl,
                        brush = curveBrush,
                        clinicalColors = clinical,
                        scratch = graphScratch,
                        markerSpacing = 8.dp.toPx(),
                        strokeWidth = if (visibleReadings.size > 240) 1.7.dp.toPx() else 2.dp.toPx()
                    )

                    if (scrubX >= 0f && scrubbedPoint != null) {
                        val selected = scrubbedPoint!!
                        val sx = xFor(selected.timestamp)
                        val sy = yFor(selected.valueMgDl)
                        drawLine(
                            color = crosshairColor,
                            start = Offset(sx, chartTop),
                            end = Offset(sx, chartBottom),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawCircle(
                            color = highlightPrimaryColor.copy(alpha = 0.25f),
                            radius = 7.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = highlightPrimaryColor,
                            radius = 4.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = graphSurfaceColor,
                            radius = 2.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }

            // Bottom controls: horizontally scrollable so the outer 1h/12h pills
            // stay reachable on narrow round screens (the bottom chord is short).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(1, 3, 6, 12).forEach { hours ->
                    TimeRangePill(
                        hours = hours,
                        isSelected = selectedHours == hours,
                        onClick = { selectedHours = hours }
                    )
                }
            }
        }
    }
}

private fun wearAxisCeiling(highest: Float): Float {
    val ladder = floatArrayOf(200f, 240f, 280f, 320f, 360f, 420f, 500f, 600f)
    val wanted = highest + 20f
    return ladder.firstOrNull { it >= wanted } ?: ladder.last()
}

private class WearGraphPaints(
    density: Density,
    textColor: Color,
    clinicalColors: ClinicalColors
) {
    private val axisTextSize = with(density) { 9.sp.toPx() }
    private val timeTextSize = with(density) { 8.5.sp.toPx() }

    val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
    }
    val target = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = clinicalColors.inRange.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    val time = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.copy(alpha = 0.8f).toArgb()
        textSize = timeTextSize
        textAlign = Paint.Align.CENTER
    }
    val axisBaselineOffset = axisTextSize * 0.36f
}

private class WearGraphScratch {
    val linePath = Path()
    val areaPath = Path()
    val envelopePath = Path()
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
    }
}

private fun wearZoneBrush(
    top: Float,
    bottom: Float,
    minValue: Float,
    maxValue: Float,
    targetLow: Float,
    targetHigh: Float,
    colors: ClinicalColors
): Brush {
    fun stopAt(value: Float): Float =
        ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    val stops = ArrayList<Pair<Float, Color>>(6)
    val epsilon = 0.0015f

    fun append(position: Float, color: Color) {
        val previous = stops.lastOrNull()?.first ?: 0f
        stops += (position.coerceIn(previous, 1f)) to color
    }

    fun transition(value: Float, color: Color) {
        if (value in minValue..maxValue) append(stopAt(value) + epsilon, color)
    }

    when {
        maxValue > 250f -> append(0f, colors.veryHigh)
        maxValue > targetHigh -> append(0f, colors.high)
        maxValue > targetLow -> append(0f, colors.inRange)
        maxValue > 54f -> append(0f, colors.low)
        else -> append(0f, colors.veryLow)
    }
    if (maxValue > 250f) transition(targetHigh, colors.high)
    if (maxValue > targetHigh) transition(targetHigh, colors.inRange)
    if (maxValue > targetLow) transition(targetLow, colors.low)
    if (maxValue > 54f) transition(54f, colors.veryLow)
    append(1f, stops.last().second)

    return Brush.verticalGradient(colorStops = stops.toTypedArray(), startY = top, endY = bottom)
}

private fun DrawScope.drawWearCurve(
    readings: List<GlucosePoint>,
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    windowStart: Long,
    windowSpan: Long,
    minValue: Float,
    maxValue: Float,
    brush: Brush,
    clinicalColors: ClinicalColors,
    scratch: WearGraphScratch,
    markerSpacing: Float,
    strokeWidth: Float
) {
    if (readings.isEmpty()) return
    val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
    val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
    val valueRange = (maxValue - minValue).coerceAtLeast(1f)

    fun xFor(timestamp: Long): Float {
        val progress = ((timestamp - windowStart).toFloat() / windowSpan).coerceIn(0f, 1f)
        return chartLeft + progress * chartWidth
    }

    fun yFor(value: Float): Float {
        val fraction = ((value.coerceIn(minValue, maxValue) - minValue) / valueRange).coerceIn(0f, 1f)
        return chartBottom - fraction * chartHeight
    }

    if (readings.size > chartWidth * 1.2f) {
        drawWearEnvelope(
            readings = readings,
            chartLeft = chartLeft,
            chartWidth = chartWidth,
            xFor = ::xFor,
            yFor = ::yFor,
            brush = brush,
            scratch = scratch,
            strokeWidth = strokeWidth
        )
    } else {
        drawWearSpline(
            readings = readings,
            chartBottom = chartBottom,
            xFor = ::xFor,
            yFor = ::yFor,
            brush = brush,
            scratch = scratch,
            strokeWidth = strokeWidth
        )
    }

    if (markerSpacing > 0f) {
        var lastMarkerX = -Float.MAX_VALUE
        readings.forEach { point ->
            val x = xFor(point.timestamp)
            if (x - lastMarkerX >= markerSpacing) {
                drawCircle(
                    color = wearStatusColor(point.status, clinicalColors),
                    radius = 1.4.dp.toPx(),
                    center = Offset(x, yFor(point.valueMgDl))
                )
                lastMarkerX = x
            }
        }
    }

    val latest = readings.last()
    val head = Offset(xFor(latest.timestamp), yFor(latest.valueMgDl))
    val headColor = wearStatusColor(latest.status, clinicalColors)
    drawCircle(color = headColor.copy(alpha = 0.22f), radius = 6.dp.toPx(), center = head)
    drawCircle(color = headColor, radius = 2.2.dp.toPx(), center = head)
}

private fun DrawScope.drawWearSpline(
    readings: List<GlucosePoint>,
    chartBottom: Float,
    xFor: (Long) -> Float,
    yFor: (Float) -> Float,
    brush: Brush,
    scratch: WearGraphScratch,
    strokeWidth: Float
) {
    val line = scratch.linePath.apply { reset() }
    val area = scratch.areaPath.apply { reset() }
    var hasSegment = false
    var previousX = 0f
    var previousY = 0f
    var segmentStartX = 0f
    var previousTime = Long.MIN_VALUE

    fun closeSegment() {
        if (!hasSegment) return
        area.lineTo(previousX, chartBottom)
        area.lineTo(segmentStartX, chartBottom)
        area.close()
        hasSegment = false
    }

    readings.forEach { point ->
        val x = xFor(point.timestamp)
        val y = yFor(point.valueMgDl)
        val gap = hasSegment && point.timestamp - previousTime > 25 * 60 * 1000L
        if (!hasSegment || gap) {
            closeSegment()
            line.moveTo(x, y)
            area.moveTo(x, chartBottom)
            area.lineTo(x, y)
            segmentStartX = x
            hasSegment = true
        } else {
            val midpointX = (previousX + x) / 2f
            line.cubicTo(midpointX, previousY, midpointX, y, x, y)
            area.cubicTo(midpointX, previousY, midpointX, y, x, y)
        }
        previousX = x
        previousY = y
        previousTime = point.timestamp
    }
    closeSegment()

    drawPath(path = area, brush = brush, alpha = 0.12f)
    drawPath(
        path = line,
        brush = brush,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawWearEnvelope(
    readings: List<GlucosePoint>,
    chartLeft: Float,
    chartWidth: Float,
    xFor: (Long) -> Float,
    yFor: (Float) -> Float,
    brush: Brush,
    scratch: WearGraphScratch,
    strokeWidth: Float
) {
    val columns = chartWidth.toInt().coerceIn(1, 2048)
    scratch.prepareColumns(columns)
    val minimum = scratch.columnMin
    val maximum = scratch.columnMax
    val sum = scratch.columnSum
    val counts = scratch.columnCount

    readings.forEach { point ->
        val column = xFor(point.timestamp).let { x ->
            (((x - chartLeft) / chartWidth) * columns).toInt().coerceIn(0, columns - 1)
        }
        val value = point.valueMgDl
        if (counts[column] == 0) {
            minimum[column] = value
            maximum[column] = value
            sum[column] = value
        } else {
            minimum[column] = minOf(minimum[column], value)
            maximum[column] = maxOf(maximum[column], value)
            sum[column] += value
        }
        counts[column]++
    }

    val band = scratch.envelopePath.apply { reset() }
    val mean = scratch.linePath.apply { reset() }
    val columnWidth = chartWidth / columns
    var runStart = -1

    fun flushRun(runEnd: Int) {
        if (runStart < 0) return
        for (column in runStart..runEnd) {
            val x = chartLeft + (column + 0.5f) * columnWidth
            val y = yFor(maximum[column])
            if (column == runStart) band.moveTo(x, y) else band.lineTo(x, y)
        }
        for (column in runEnd downTo runStart) {
            band.lineTo(
                chartLeft + (column + 0.5f) * columnWidth,
                yFor(minimum[column])
            )
        }
        band.close()
        for (column in runStart..runEnd) {
            val x = chartLeft + (column + 0.5f) * columnWidth
            val y = yFor(sum[column] / counts[column])
            if (column == runStart) mean.moveTo(x, y) else mean.lineTo(x, y)
        }
        runStart = -1
    }

    for (column in 0 until columns) {
        if (counts[column] > 0) {
            if (runStart < 0) runStart = column
        } else if (runStart >= 0) {
            flushRun(column - 1)
        }
    }
    if (runStart >= 0) flushRun(columns - 1)

    drawPath(path = band, brush = brush, alpha = 0.2f)
    drawPath(
        path = mean,
        brush = brush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun wearStatusColor(status: GlucoseStatus, colors: ClinicalColors): Color = when (status) {
    GlucoseStatus.VERY_LOW -> colors.veryLow
    GlucoseStatus.LOW -> colors.low
    GlucoseStatus.IN_RANGE -> colors.inRange
    GlucoseStatus.HIGH -> colors.high
    GlucoseStatus.VERY_HIGH -> colors.veryHigh
}

@Composable
private fun TimeRangePill(
    hours: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    CompactButton(
        onClick = onClick,
        colors = if (isSelected) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        Text(
            text = "${hours}h",
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
