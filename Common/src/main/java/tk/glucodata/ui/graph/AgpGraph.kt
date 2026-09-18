package tk.glucodata.ui.graph

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.ui.model.AgpProfile
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.HourlyPercentiles
import tk.glucodata.ui.theme.LocalClinicalColors
import kotlin.math.max

@Composable
fun AgpGraph(
    profile: AgpProfile,
    unit: GlucoseUnit = GlucoseUnit.MG_DL,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    onHourInspected: (HourlyPercentiles?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current
    val gridColor = clinicalColors.graphGrid
    val targetShade = clinicalColors.targetRangeShade
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    var inspectedHour by remember { mutableStateOf<HourlyPercentiles?>(null) }
    val hourlyData = profile.hourlyPercentiles

    val minY = 40f
    val maxY = 260f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(surfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hourlyData) {
                    detectTapGestures(
                        onPress = { offset ->
                            val paddingLeft = 20f
                            val paddingRight = 85f
                            val chartWidth = size.width - paddingLeft - paddingRight
                            val fraction = ((offset.x - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                            val hourIdx = (fraction * 23.99f).toInt().coerceIn(0, 23)
                            val item = hourlyData.getOrNull(hourIdx)
                            inspectedHour = item
                            onHourInspected(item)
                            tryAwaitRelease()
                            inspectedHour = null
                            onHourInspected(null)
                        }
                    )
                }
                .pointerInput(hourlyData) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val paddingLeft = 20f
                            val paddingRight = 85f
                            val chartWidth = size.width - paddingLeft - paddingRight
                            val fraction = ((offset.x - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                            val hourIdx = (fraction * 23.99f).toInt().coerceIn(0, 23)
                            val item = hourlyData.getOrNull(hourIdx)
                            inspectedHour = item
                            onHourInspected(item)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val paddingLeft = 20f
                            val paddingRight = 85f
                            val chartWidth = size.width - paddingLeft - paddingRight
                            val fraction = ((change.position.x - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                            val hourIdx = (fraction * 23.99f).toInt().coerceIn(0, 23)
                            val item = hourlyData.getOrNull(hourIdx)
                            inspectedHour = item
                            onHourInspected(item)
                        },
                        onDragEnd = {
                            inspectedHour = null
                            onHourInspected(null)
                        },
                        onDragCancel = {
                            inspectedHour = null
                            onHourInspected(null)
                        }
                    )
                }
        ) {
            val paddingLeft = 20f
            val paddingRight = 85f
            val paddingTop = 20f
            val paddingBottom = 40f

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
                    paddingLeft + chartWidth + 10f,
                    yPos + 4.sp.toPx(),
                    textPaint
                )
                yVal += yStep
            }

            // 3. X-Axis Time Ticks (every 4 hours)
            for (h in 0..24 step 4) {
                val xPos = hourToX(h.toFloat())
                drawLine(
                    color = gridColor,
                    start = Offset(xPos, paddingTop),
                    end = Offset(xPos, paddingTop + chartHeight),
                    strokeWidth = 1f
                )
                val label = String.format(java.util.Locale.US, "%02d:00", if (h == 24) 0 else h)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    xPos - 16f,
                    paddingTop + chartHeight + 20f,
                    textPaint
                )
            }

            if (hourlyData.size >= 24) {
                // 4. 10th - 90th percentile outer band (light blue)
                val outerBandPath = Path()
                // Top curve (90th percentile)
                val p0X = hourToX(0f)
                val p0Y = valueToY(hourlyData[0].p90)
                outerBandPath.moveTo(p0X, p0Y)
                for (h in 1..23) {
                    val x0 = hourToX((h - 1).toFloat())
                    val y0 = valueToY(hourlyData[h - 1].p90)
                    val x1 = hourToX(h.toFloat())
                    val y1 = valueToY(hourlyData[h].p90)
                    outerBandPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                }
                // Connect to end
                val endX = hourToX(24f)
                val endY90 = valueToY(hourlyData[0].p90) // Wrap around
                outerBandPath.lineTo(endX, endY90)

                // Bottom curve back (10th percentile)
                val endY10 = valueToY(hourlyData[0].p10)
                outerBandPath.lineTo(endX, endY10)
                for (h in 23 downTo 0) {
                    val x1 = hourToX(h.toFloat())
                    val y1 = valueToY(hourlyData[h].p10)
                    outerBandPath.lineTo(x1, y1)
                }
                outerBandPath.close()

                drawPath(
                    path = outerBandPath,
                    color = Color(0xFF60A5FA).copy(alpha = 0.25f)
                )

                // 5. 25th - 75th percentile interquartile band (medium blue)
                val innerBandPath = Path()
                innerBandPath.moveTo(p0X, valueToY(hourlyData[0].p75))
                for (h in 1..23) {
                    val x0 = hourToX((h - 1).toFloat())
                    val y0 = valueToY(hourlyData[h - 1].p75)
                    val x1 = hourToX(h.toFloat())
                    val y1 = valueToY(hourlyData[h].p75)
                    innerBandPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                }
                innerBandPath.lineTo(endX, valueToY(hourlyData[0].p75))
                innerBandPath.lineTo(endX, valueToY(hourlyData[0].p25))
                for (h in 23 downTo 0) {
                    val x1 = hourToX(h.toFloat())
                    val y1 = valueToY(hourlyData[h].p25)
                    innerBandPath.lineTo(x1, y1)
                }
                innerBandPath.close()

                drawPath(
                    path = innerBandPath,
                    color = Color(0xFF3B82F6).copy(alpha = 0.40f)
                )

                // 6. 50th percentile (Median) Bold Curve (deep blue / primary)
                val medianPath = Path()
                medianPath.moveTo(p0X, valueToY(hourlyData[0].p50))
                for (h in 1..23) {
                    val x0 = hourToX((h - 1).toFloat())
                    val y0 = valueToY(hourlyData[h - 1].p50)
                    val x1 = hourToX(h.toFloat())
                    val y1 = valueToY(hourlyData[h].p50)
                    medianPath.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                }
                medianPath.lineTo(endX, valueToY(hourlyData[0].p50))

                drawPath(
                    path = medianPath,
                    color = Color(0xFF1D4ED8),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
            }

            // 7. Interactive Scrubbing Line & Inspection Dot
            inspectedHour?.let { ih ->
                val ix = hourToX(ih.hour.toFloat())
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
            }
        }
    }
}
