package tk.glucodata.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
import tk.glucodata.ui.theme.LocalClinicalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            // Header: Scrub inspection info OR current range + live state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 2.dp),
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
                            text = "${unit.label} • ${timeFormatter.format(Date(p.timestamp))}",
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
                            Spacer(modifier = Modifier.width(6.dp))
                            CompactButton(onClick = { windowOffsetMillis = 0L }) {
                                Text(
                                    text = "Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                                    val progress = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
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

                    val minGl = 40f
                    val maxGl = 260f
                    val rangeGl = maxGl - minGl

                    fun yFor(gl: Float): Float {
                        val clamped = gl.coerceIn(minGl, maxGl)
                        return height - ((clamped - minGl) / rangeGl) * height
                    }

                    fun xFor(timestamp: Long): Float {
                        val progress = ((timestamp - windowStart).toFloat() / (windowEnd - windowStart).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        return progress * width
                    }

                    // Shaded Target Range Band
                    val yTargetLow = yFor(targetLow)
                    val yTargetHigh = yFor(targetHigh)
                    val bandTop = yTargetHigh.coerceAtLeast(0f)
                    val bandBottom = yTargetLow.coerceAtMost(height)

                    drawRect(
                        color = clinical.targetRangeShade,
                        topLeft = Offset(0f, bandTop),
                        size = Size(width, (bandBottom - bandTop).coerceAtLeast(0f))
                    )

                    // Target Boundaries
                    drawLine(
                        color = clinical.graphGrid,
                        start = Offset(0f, yTargetHigh),
                        end = Offset(width, yTargetHigh),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = clinical.graphGrid,
                        start = Offset(0f, yTargetLow),
                        end = Offset(width, yTargetLow),
                        strokeWidth = 1f
                    )

                    // Connect path
                    if (visibleReadings.isNotEmpty()) {
                        val path = Path()
                        visibleReadings.forEachIndexed { index, point ->
                            val px = xFor(point.timestamp)
                            val py = yFor(point.valueMgDl)
                            if (index == 0) {
                                path.moveTo(px, py)
                            } else {
                                path.lineTo(px, py)
                            }
                        }

                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    clinical.inRange.copy(alpha = 0.6f),
                                    clinical.inRange
                                )
                            ),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Data points
                        visibleReadings.forEach { p ->
                            val px = xFor(p.timestamp)
                            val py = yFor(p.valueMgDl)
                            val color = when (p.status) {
                                GlucoseStatus.IN_RANGE -> clinical.inRange
                                GlucoseStatus.LOW, GlucoseStatus.HIGH -> clinical.low
                                GlucoseStatus.VERY_LOW, GlucoseStatus.VERY_HIGH -> clinical.veryLow
                            }
                            drawCircle(
                                color = color,
                                radius = 2.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }

                        // Scrub indicator
                        if (scrubX >= 0f && scrubbedPoint != null) {
                            val sx = xFor(scrubbedPoint!!.timestamp)
                            val sy = yFor(scrubbedPoint!!.valueMgDl)

                            // Vertical crosshair
                            drawLine(
                                color = crosshairColor,
                                start = Offset(sx, 0f),
                                end = Offset(sx, height),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            // Highlight point
                            drawCircle(
                                color = highlightPrimaryColor,
                                radius = 5.dp.toPx(),
                                center = Offset(sx, sy)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(sx, sy)
                            )
                        }
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
