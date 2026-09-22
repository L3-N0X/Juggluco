package tk.glucodata.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    val clinical = LocalClinicalColors.current

    val now = remember(readings) {
        readings.lastOrNull()?.timestamp ?: System.currentTimeMillis()
    }
    val windowStart = now - (selectedHours * 3600 * 1000L)

    val visibleReadings = remember(readings, windowStart, selectedHours) {
        readings.filter { it.timestamp >= windowStart }
    }

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
            // Header: Scrub inspection info OR current range
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 2.dp),
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
                            text = "History (${selectedHours}h)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Interactive Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(visibleReadings, windowStart, now) {
                        detectTapGestures(
                            onPress = { offset ->
                                if (visibleReadings.isNotEmpty()) {
                                    val progress = (offset.x / size.width).coerceIn(0f, 1f)
                                    val touchedTime = windowStart + (progress * (now - windowStart)).toLong()
                                    scrubbedPoint = visibleReadings.minByOrNull { kotlin.math.abs(it.timestamp - touchedTime) }
                                    scrubX = offset.x
                                }
                            }
                        )
                    }
                    .pointerInput(visibleReadings, windowStart, now) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (visibleReadings.isNotEmpty()) {
                                    val progress = (offset.x / size.width).coerceIn(0f, 1f)
                                    val touchedTime = windowStart + (progress * (now - windowStart)).toLong()
                                    scrubbedPoint = visibleReadings.minByOrNull { kotlin.math.abs(it.timestamp - touchedTime) }
                                    scrubX = offset.x
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val x = change.position.x
                                if (visibleReadings.isNotEmpty() && size.width > 0) {
                                    val progress = (x / size.width).coerceIn(0f, 1f)
                                    val touchedTime = windowStart + (progress * (now - windowStart)).toLong()
                                    scrubbedPoint = visibleReadings.minByOrNull { kotlin.math.abs(it.timestamp - touchedTime) }
                                    scrubX = x
                                }
                            },
                            onDragEnd = {
                                scrubbedPoint = null
                                scrubX = -1f
                            },
                            onDragCancel = {
                                scrubbedPoint = null
                                scrubX = -1f
                            }
                        )
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
                        val progress = ((timestamp - windowStart).toFloat() / (now - windowStart).coerceAtLeast(1L)).coerceIn(0f, 1f)
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

            // Bottom controls: Time range selector pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
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
