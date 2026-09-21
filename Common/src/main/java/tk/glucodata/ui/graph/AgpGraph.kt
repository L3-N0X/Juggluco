package tk.glucodata.ui.graph

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.glucodata.R
import tk.glucodata.ui.model.AgpProfile
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.HourlyPercentiles
import tk.glucodata.ui.theme.DarkClinicalColors
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun AgpGraph(
    profile: AgpProfile,
    unit: GlucoseUnit = GlucoseUnit.MG_DL,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    onHourInspected: (HourlyPercentiles?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val clinicalColors = LocalClinicalColors.current
    val isDark = clinicalColors == DarkClinicalColors
    val gridColor = clinicalColors.graphGrid
    val targetShade = clinicalColors.targetRangeShade
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    val outerBandColor = if (isDark) Color(0xFF2563EB).copy(alpha = 0.38f) else Color(0xFF60A5FA).copy(alpha = 0.25f)
    val innerBandColor = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.25f) else Color(0xFF3B82F6).copy(alpha = 0.40f)
    val medianColor = Color(0xFF1D4ED8)

    val singleOuterLineColor = if (isDark) Color(0xFF2563EB).copy(alpha = 0.8f) else Color(0xFF60A5FA).copy(alpha = 0.6f)
    val singleInnerLineColor = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.65f) else Color(0xFF3B82F6).copy(alpha = 0.7f)

    var inspectedHour by remember { mutableStateOf<HourlyPercentiles?>(null) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val hourlyData = profile.hourlyPercentiles

    val minY = 40f
    val maxY = 260f

    val noDataLabel = stringResource(R.string.nodata)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(surfaceColor, RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hourlyData, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hideJob?.cancel()
                        val paddingLeft = with(density) { 8.dp.toPx() }
                        val paddingRight = with(density) { 36.dp.toPx() }
                        val chartWidth = size.width - paddingLeft - paddingRight

                        fun updateInspection(x: Float) {
                            val fraction = ((x - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                            val hourIdx = (fraction * 23.99f).toInt().coerceIn(0, 23)
                            val item = hourlyData.getOrNull(hourIdx)
                            inspectedHour = item
                            onHourInspected(item)
                        }

                        updateInspection(down.position.x)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                break
                            }
                            updateInspection(change.position.x)
                            change.consume()
                        }

                        hideJob = coroutineScope.launch {
                            delay(1800)
                            inspectedHour = null
                            onHourInspected(null)
                        }
                    }
                }
        ) {
            val paddingLeft = with(density) { 8.dp.toPx() }
            val paddingRight = with(density) { 36.dp.toPx() }
            val paddingTop = with(density) { 10.dp.toPx() }
            val paddingBottom = with(density) { 26.dp.toPx() }

            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            fun hourToX(hour: Float): Float {
                val fraction = (hour / 24f).coerceIn(0f, 1f)
                return paddingLeft + (fraction * chartWidth)
            }

            fun valueToY(value: Float): Float {
                val fraction = ((value - minY) / (maxY - minY)).coerceIn(0f, 1f)
                return paddingTop + chartHeight - (fraction * chartHeight)
            }

            // 1. Shaded Target Range Background
            val targetTopY = valueToY(targetHigh)
            val targetBottomY = valueToY(targetLow)
            drawRect(
                color = targetShade,
                topLeft = Offset(paddingLeft, targetTopY),
                size = Size(chartWidth, targetBottomY - targetTopY)
            )

            // Dashed target lines
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
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

            // 2. Y-Axis Grid Lines & Labels
            val yStep = if (unit == GlucoseUnit.MMOL_L) 36f else 50f
            var yVal = 50f
            val textPaint = Paint().apply {
                color = textSecondary.toArgb()
                textSize = 10.sp.toPx()
                isAntiAlias = true
            }
            val yLabelPaint = Paint().apply {
                color = textSecondary.toArgb()
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            val yLabelX = size.width - with(density) { 4.dp.toPx() }

            while (yVal <= maxY) {
                val yPos = valueToY(yVal)
                drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, yPos),
                    end = Offset(paddingLeft + chartWidth, yPos),
                    strokeWidth = 1f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    unit.format(yVal),
                    yLabelX,
                    yPos + 4.sp.toPx(),
                    yLabelPaint
                )
                yVal += yStep
            }

            // 3. X-Axis Time Ticks (every 4 hours)
            val xLabelY = paddingTop + chartHeight + with(density) { 18.dp.toPx() }
            for (h in 0..24 step 4) {
                val xPos = hourToX(h.toFloat())
                drawLine(
                    color = gridColor,
                    start = Offset(xPos, paddingTop),
                    end = Offset(xPos, paddingTop + chartHeight),
                    strokeWidth = 1f
                )
                val label = String.format(java.util.Locale.US, "%02d:00", if (h == 24) 0 else h)
                textPaint.textAlign = when (h) {
                    0 -> Paint.Align.LEFT
                    24 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    xPos,
                    xLabelY,
                    textPaint
                )
            }

            // 4. Data vs No-Data Segments
            if (profile.hasAnyData) {
                // Find contiguous sequences of hours with data
                val dataSegments = mutableListOf<List<HourlyPercentiles>>()
                var currentSegment = mutableListOf<HourlyPercentiles>()

                for (item in hourlyData) {
                    if (item.hasData) {
                        currentSegment.add(item)
                    } else {
                        if (currentSegment.isNotEmpty()) {
                            dataSegments.add(currentSegment)
                            currentSegment = mutableListOf()
                        }
                    }
                }
                if (currentSegment.isNotEmpty()) {
                    dataSegments.add(currentSegment)
                }

                // If the last segment ends at hour 23, extend it to hour 24 so the plot reaches the right edge
                val h0 = hourlyData.firstOrNull { it.hour == 0 && it.hasData }
                val processedSegments = dataSegments.map { segment ->
                    if (segment.isNotEmpty() && segment.last().hour == 23) {
                        val endPoint = if (h0 != null) {
                            HourlyPercentiles(
                                hour = 24,
                                p10 = h0.p10,
                                p25 = h0.p25,
                                p50 = h0.p50,
                                p75 = h0.p75,
                                p90 = h0.p90,
                                count = h0.count
                            )
                        } else {
                            val last = segment.last()
                            HourlyPercentiles(
                                hour = 24,
                                p10 = last.p10,
                                p25 = last.p25,
                                p50 = last.p50,
                                p75 = last.p75,
                                p90 = last.p90,
                                count = last.count
                            )
                        }
                        segment + endPoint
                    } else {
                        segment
                    }
                }

                // Highlight no-measurement sequences subtly
                val noDataPaint = Paint().apply {
                    color = textSecondary.copy(alpha = 0.45f).toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                var gapStart: Int? = null
                for (h in 0..24) {
                    val hasDataAtH = if (h < 24) {
                        hourlyData.getOrNull(h)?.hasData == true
                    } else {
                        hourlyData.getOrNull(23)?.hasData == true
                    }
                    if (!hasDataAtH) {
                        if (gapStart == null) gapStart = h
                    } else {
                        if (gapStart != null) {
                            val startX = hourToX(gapStart.toFloat())
                            val endX = hourToX(h.toFloat())
                            val gapWidth = endX - startX

                            // Tint gap area with subtle empty background
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.06f),
                                topLeft = Offset(startX, paddingTop),
                                size = Size(gapWidth, chartHeight)
                            )

                            // Show "No data" badge text if gap is at least 3 hours
                            if (h - gapStart >= 3) {
                                val centerX = (startX + endX) / 2f
                                val centerY = paddingTop + chartHeight / 2f
                                drawContext.canvas.nativeCanvas.drawText(
                                    noDataLabel,
                                    centerX,
                                    centerY,
                                    noDataPaint
                                )
                            }
                            gapStart = null
                        }
                    }
                }
                if (gapStart != null) {
                    val startX = hourToX(gapStart.toFloat())
                    val endX = hourToX(24f)
                    val gapWidth = endX - startX
                    drawRect(
                        color = Color.Gray.copy(alpha = 0.06f),
                        topLeft = Offset(startX, paddingTop),
                        size = Size(gapWidth, chartHeight)
                    )
                    if (24 - gapStart >= 3) {
                        val centerX = (startX + endX) / 2f
                        val centerY = paddingTop + chartHeight / 2f
                        drawContext.canvas.nativeCanvas.drawText(
                            noDataLabel,
                            centerX,
                            centerY,
                            noDataPaint
                        )
                    }
                }

                // Draw percentile bands only for data segments
                for (segment in processedSegments) {
                    if (segment.size == 1) {
                        val single = segment[0]
                        val sx = hourToX(single.hour + 0.5f)
                        // 10-90 range line
                        drawLine(
                            color = singleOuterLineColor,
                            start = Offset(sx, valueToY(single.p10)),
                            end = Offset(sx, valueToY(single.p90)),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                        // 25-75 IQR bar
                        drawLine(
                            color = singleInnerLineColor,
                            start = Offset(sx, valueToY(single.p25)),
                            end = Offset(sx, valueToY(single.p75)),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                        // Median dot
                        drawCircle(
                            color = medianColor,
                            radius = 4.dp.toPx(),
                            center = Offset(sx, valueToY(single.p50))
                        )
                    } else {
                        // Outer band (10th - 90th percentile)
                        val outerBandPath = Path()
                        val p0X = hourToX(segment[0].hour.toFloat())
                        val p0Y = valueToY(segment[0].p90)
                        outerBandPath.moveTo(p0X, p0Y)
                        for (i in 1 until segment.size) {
                            val prev = segment[i - 1]
                            val curr = segment[i]
                            val x0 = hourToX(prev.hour.toFloat())
                            val y0 = valueToY(prev.p90)
                            val x1 = hourToX(curr.hour.toFloat())
                            val y1 = valueToY(curr.p90)
                            outerBandPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                        }

                        val lastX = hourToX(segment.last().hour.toFloat())
                        val lastY10 = valueToY(segment.last().p10)
                        outerBandPath.lineTo(lastX, lastY10)

                        for (i in segment.size - 2 downTo 0) {
                            val prev = segment[i + 1]
                            val curr = segment[i]
                            val x0 = hourToX(prev.hour.toFloat())
                            val y0 = valueToY(prev.p10)
                            val x1 = hourToX(curr.hour.toFloat())
                            val y1 = valueToY(curr.p10)
                            outerBandPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                        }
                        outerBandPath.close()

                        drawPath(
                            path = outerBandPath,
                            color = outerBandColor
                        )

                        // Inner band (25th - 75th percentile)
                        val innerBandPath = Path()
                        innerBandPath.moveTo(p0X, valueToY(segment[0].p75))
                        for (i in 1 until segment.size) {
                            val prev = segment[i - 1]
                            val curr = segment[i]
                            val x0 = hourToX(prev.hour.toFloat())
                            val y0 = valueToY(prev.p75)
                            val x1 = hourToX(curr.hour.toFloat())
                            val y1 = valueToY(curr.p75)
                            innerBandPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                        }
                        innerBandPath.lineTo(lastX, valueToY(segment.last().p25))
                        for (i in segment.size - 2 downTo 0) {
                            val prev = segment[i + 1]
                            val curr = segment[i]
                            val x0 = hourToX(prev.hour.toFloat())
                            val y0 = valueToY(prev.p25)
                            val x1 = hourToX(curr.hour.toFloat())
                            val y1 = valueToY(curr.p25)
                            innerBandPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                        }
                        innerBandPath.close()

                        drawPath(
                            path = innerBandPath,
                            color = innerBandColor
                        )

                        // Median Curve (50th percentile)
                        val medianPath = Path()
                        medianPath.moveTo(p0X, valueToY(segment[0].p50))
                        for (i in 1 until segment.size) {
                            val prev = segment[i - 1]
                            val curr = segment[i]
                            val x0 = hourToX(prev.hour.toFloat())
                            val y0 = valueToY(prev.p50)
                            val x1 = hourToX(curr.hour.toFloat())
                            val y1 = valueToY(curr.p50)
                            medianPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                        }

                        drawPath(
                            path = medianPath,
                            color = medianColor,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // 5. Interactive Scrubbing Line & Inspection Dot
            inspectedHour?.let { ih ->
                val ix = hourToX(ih.hour.toFloat())
                if (ih.hasData) {
                    val iy = valueToY(ih.p50)

                    drawLine(
                        color = Color(0xFF1D4ED8).copy(alpha = 0.7f),
                        start = Offset(ix, paddingTop),
                        end = Offset(ix, paddingTop + chartHeight),
                        strokeWidth = 1.5f
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(ix, iy)
                    )
                    drawCircle(
                        color = Color(0xFF1D4ED8),
                        radius = 4.dp.toPx(),
                        center = Offset(ix, iy)
                    )
                } else {
                    // Scrubber line over sequence without data
                    drawLine(
                        color = textSecondary.copy(alpha = 0.5f),
                        start = Offset(ix, paddingTop),
                        end = Offset(ix, paddingTop + chartHeight),
                        strokeWidth = 1.5f,
                        pathEffect = dashedEffect
                    )
                }
            }
        }

        // 6. Centered Empty State Overlay when no data in the entire profile
        if (!profile.hasAnyData) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.agp_no_data_period),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 7. Debounced Floating Inspection Overlay on top (does not shift the UI!)
        AnimatedVisibility(
            visible = inspectedHour != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp, start = 8.dp, end = 8.dp)
        ) {
            inspectedHour?.let { ih ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%02d:00", ih.hour),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (ih.hasData) {
                            Text(
                                text = "Median: ${unit.format(ih.p50)} • IQR: ${unit.format(ih.p25)}–${unit.format(ih.p75)}" +
                                    (if (ih.count > 0) " (${ih.count})" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.agp_no_data_sequence),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
