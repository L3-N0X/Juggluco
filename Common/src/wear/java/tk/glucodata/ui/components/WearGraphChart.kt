package tk.glucodata.ui.components

import android.graphics.Paint
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.ClinicalColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

internal data class WearGraphChartSpec(
    val readings: List<GlucosePoint>,
    val windowStart: Long,
    val windowEnd: Long,
    val selectedHours: Int,
    val axisMax: Float,
    val unit: GlucoseUnit,
    val targetLow: Float,
    val targetHigh: Float,
    val clinicalColors: ClinicalColors,
    val gridColor: Color,
    val surfaceColor: Color,
    val crosshairColor: Color,
    val highlightColor: Color,
    val selectedPoint: GlucosePoint?
)

internal class WearGraphChartRenderer(
    density: Density,
    textColor: Color,
    clinicalColors: ClinicalColors
) {
    private val axisTextSize = with(density) { 9.sp.toPx() }
    private val timeTextSize = with(density) { 8.5.sp.toPx() }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = clinicalColors.inRange.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.copy(alpha = 0.8f).toArgb()
        textSize = timeTextSize
        textAlign = Paint.Align.CENTER
    }
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val axisBaselineOffset = axisTextSize * 0.36f
    private val linePath = Path()
    private val areaPath = Path()
    private val envelopePath = Path()
    private var columnMin = FloatArray(0)
    private var columnMax = FloatArray(0)
    private var columnSum = FloatArray(0)
    private var columnCount = IntArray(0)

    fun axisCeiling(highest: Float): Float {
        val ladder = floatArrayOf(200f, 240f, 280f, 320f, 360f, 420f, 500f, 600f)
        val wanted = highest + 20f
        return ladder.firstOrNull { it >= wanted } ?: ladder.last()
    }

    fun draw(scope: DrawScope, spec: WearGraphChartSpec): Unit = with(scope) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@with

        val geometry = WearGraphGeometry(
            left = 2.dp.toPx(),
            top = 4.dp.toPx(),
            right = (width - 30.dp.toPx()).coerceAtLeast(3.dp.toPx()),
            bottom = (height - 17.dp.toPx()).coerceAtLeast(5.dp.toPx())
        )
        val transform = WearGraphTransform(
            geometry = geometry,
            windowStart = spec.windowStart,
            windowSpan = (spec.windowEnd - spec.windowStart).coerceAtLeast(1L),
            minValue = 40f,
            maxValue = spec.axisMax.coerceAtLeast(spec.targetHigh + 40f)
        )

        drawTargetBand(transform, spec.targetLow, spec.targetHigh, spec.clinicalColors.targetRangeShade)
        drawValueAxis(transform, spec)
        drawTimeAxis(transform, spec)
        drawCurve(transform, spec)

        spec.selectedPoint?.let { point ->
            val selectedX = transform.x(point.timestamp)
            val selectedY = transform.y(point.valueMgDl)
            drawLine(
                color = spec.crosshairColor,
                start = Offset(selectedX, geometry.top),
                end = Offset(selectedX, geometry.bottom),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(
                color = spec.highlightColor.copy(alpha = 0.25f),
                radius = 7.dp.toPx(),
                center = Offset(selectedX, selectedY)
            )
            drawCircle(
                color = spec.highlightColor,
                radius = 4.dp.toPx(),
                center = Offset(selectedX, selectedY)
            )
            drawCircle(
                color = spec.surfaceColor,
                radius = 2.dp.toPx(),
                center = Offset(selectedX, selectedY)
            )
        }
    }

    private fun DrawScope.drawTargetBand(
        transform: WearGraphTransform,
        targetLow: Float,
        targetHigh: Float,
        color: Color
    ) {
        drawRect(
            color = color,
            topLeft = Offset(transform.geometry.left, transform.y(targetHigh)),
            size = Size(
                transform.geometry.width,
                (transform.y(targetLow) - transform.y(targetHigh)).coerceAtLeast(1f)
            )
        )
    }

    private fun DrawScope.drawValueAxis(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec
    ) {
        val width = size.width
        val axisX = width - 3.dp.toPx()
        val step = if (spec.unit == GlucoseUnit.MMOL_L) 36f else 50f
        var axisValue = ceil(transform.minValue / step) * step
        while (axisValue <= transform.maxValue) {
            if (abs(axisValue - spec.targetLow) > step * 0.35f &&
                abs(axisValue - spec.targetHigh) > step * 0.35f
            ) {
                val y = transform.y(axisValue)
                val label = spec.unit.format(axisValue)
                val labelWidth = axisPaint.measureText(label)
                drawLine(
                    color = spec.gridColor,
                    start = Offset(transform.geometry.left, y),
                    end = Offset(axisX - labelWidth - 3.dp.toPx(), y),
                    strokeWidth = 0.8.dp.toPx()
                )
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    axisX,
                    y + axisBaselineOffset,
                    axisPaint
                )
            }
            axisValue += step
        }

        val dash = PathEffect.dashPathEffect(
            floatArrayOf(5.dp.toPx(), 3.dp.toPx()),
            0f
        )
        listOf(spec.targetLow, spec.targetHigh).forEach { value ->
            val y = transform.y(value)
            val label = spec.unit.format(value)
            val labelWidth = targetPaint.measureText(label)
            drawLine(
                color = spec.clinicalColors.inRange.copy(alpha = 0.65f),
                start = Offset(transform.geometry.left, y),
                end = Offset(axisX - labelWidth - 3.dp.toPx(), y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                axisX,
                y + axisBaselineOffset,
                targetPaint
            )
        }
    }

    private fun DrawScope.drawTimeAxis(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec
    ) {
        val timeStep = when {
            spec.selectedHours <= 1 -> 15 * 60 * 1000L
            spec.selectedHours <= 3 -> 60 * 60 * 1000L
            spec.selectedHours <= 6 -> 2 * 60 * 60 * 1000L
            else -> 3 * 60 * 60 * 1000L
        }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = transform.windowStart
            set(Calendar.MILLISECOND, 0)
            set(Calendar.SECOND, 0)
            if (timeStep >= 60 * 60 * 1000L) set(Calendar.MINUTE, 0)
        }
        var tick = calendar.timeInMillis
        if (tick < transform.windowStart) tick += timeStep
        var tickGuard = 0

        while (tick <= spec.windowEnd && tickGuard++ < 32) {
            val x = transform.x(tick)
            val label = timeFormatter.format(Date(tick))
            val labelWidth = timePaint.measureText(label)
            val labelX = x.coerceIn(
                transform.geometry.left + labelWidth / 2f,
                transform.geometry.right - labelWidth / 2f
            )
            drawLine(
                color = spec.gridColor,
                start = Offset(x, transform.geometry.top),
                end = Offset(x, transform.geometry.bottom),
                strokeWidth = 0.8.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                labelX,
                size.height - 3.dp.toPx(),
                timePaint
            )
            tick += timeStep
        }
    }

    private fun DrawScope.drawCurve(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec
    ) {
        if (spec.readings.isEmpty()) return
        val strokeWidth = if (spec.readings.size > 240) 1.7.dp.toPx() else 2.dp.toPx()
        if (spec.readings.size > transform.geometry.width * 1.2f) {
            drawEnvelope(transform, spec, strokeWidth)
        } else {
            drawSpline(transform, spec, strokeWidth)
        }
        drawSparseMarkers(transform, spec)
        drawHead(transform, spec)
    }

    private fun DrawScope.drawSpline(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec,
        strokeWidth: Float
    ) {
        val line = linePath.apply { reset() }
        val area = areaPath.apply { reset() }
        var hasSegment = false
        var previousX = 0f
        var previousY = 0f
        var segmentStartX = 0f
        var previousTime = Long.MIN_VALUE

        fun closeSegment() {
            if (!hasSegment) return
            area.lineTo(previousX, transform.geometry.bottom)
            area.lineTo(segmentStartX, transform.geometry.bottom)
            area.close()
            hasSegment = false
        }

        spec.readings.forEach { point ->
            val x = transform.x(point.timestamp)
            val y = transform.y(point.valueMgDl)
            val gap = hasSegment && point.timestamp - previousTime > 25 * 60 * 1000L
            if (!hasSegment || gap) {
                closeSegment()
                line.moveTo(x, y)
                area.moveTo(x, transform.geometry.bottom)
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

        val brush = zoneBrush(transform, spec)
        drawPath(path = area, brush = brush, alpha = 0.12f)
        drawPath(
            path = line,
            brush = brush,
            style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    private fun DrawScope.drawEnvelope(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec,
        strokeWidth: Float
    ) {
        val columns = transform.geometry.width.toInt().coerceIn(1, 2048)
        prepareColumns(columns)
        val columnWidth = transform.geometry.width / columns
        spec.readings.forEach { point ->
            val column = ((((transform.x(point.timestamp) - transform.geometry.left) / transform.geometry.width) * columns).toInt())
                .coerceIn(0, columns - 1)
            val value = point.valueMgDl
            if (columnCount[column] == 0) {
                columnMin[column] = value
                columnMax[column] = value
                columnSum[column] = value
            } else {
                columnMin[column] = minOf(columnMin[column], value)
                columnMax[column] = maxOf(columnMax[column], value)
                columnSum[column] += value
            }
            columnCount[column]++
        }

        val band = envelopePath.apply { reset() }
        val mean = linePath.apply { reset() }
        var runStart = -1

        fun flushRun(runEnd: Int) {
            if (runStart < 0) return
            for (column in runStart..runEnd) {
                val x = transform.geometry.left + (column + 0.5f) * columnWidth
                val y = transform.y(columnMax[column])
                if (column == runStart) band.moveTo(x, y) else band.lineTo(x, y)
            }
            for (column in runEnd downTo runStart) {
                band.lineTo(
                    transform.geometry.left + (column + 0.5f) * columnWidth,
                    transform.y(columnMin[column])
                )
            }
            band.close()
            for (column in runStart..runEnd) {
                val x = transform.geometry.left + (column + 0.5f) * columnWidth
                val y = transform.y(columnSum[column] / columnCount[column])
                if (column == runStart) mean.moveTo(x, y) else mean.lineTo(x, y)
            }
            runStart = -1
        }

        for (column in 0 until columns) {
            if (columnCount[column] > 0) {
                if (runStart < 0) runStart = column
            } else if (runStart >= 0) {
                flushRun(column - 1)
            }
        }
        if (runStart >= 0) flushRun(columns - 1)

        val brush = zoneBrush(transform, spec)
        drawPath(path = band, brush = brush, alpha = 0.2f)
        drawPath(
            path = mean,
            brush = brush,
            style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    private fun DrawScope.drawSparseMarkers(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec
    ) {
        var lastMarkerX = -Float.MAX_VALUE
        val markerSpacing = 8.dp.toPx()
        spec.readings.forEach { point ->
            val x = transform.x(point.timestamp)
            if (x - lastMarkerX >= markerSpacing) {
                drawCircle(
                    color = wearStatusColor(point.status, spec.clinicalColors),
                    radius = 1.4.dp.toPx(),
                    center = Offset(x, transform.y(point.valueMgDl))
                )
                lastMarkerX = x
            }
        }
    }

    private fun DrawScope.drawHead(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec
    ) {
        val latest = spec.readings.last()
        val center = Offset(
            transform.x(latest.timestamp),
            transform.y(latest.valueMgDl)
        )
        val color = wearStatusColor(latest.status, spec.clinicalColors)
        drawCircle(color.copy(alpha = 0.22f), 6.dp.toPx(), center)
        drawCircle(color, 2.2.dp.toPx(), center)
    }

    private fun zoneBrush(
        transform: WearGraphTransform,
        spec: WearGraphChartSpec
    ): Brush {
        fun stopAt(value: Float): Float =
            ((transform.y(value) - transform.geometry.top) /
                (transform.geometry.bottom - transform.geometry.top).coerceAtLeast(1f))
                .coerceIn(0f, 1f)

        val colors = spec.clinicalColors
        val stops = ArrayList<Pair<Float, Color>>(10)
        val epsilon = 0.0008f

        fun append(position: Float, color: Color) {
            val previous = stops.lastOrNull()?.first ?: 0f
            stops += position.coerceIn(previous, 1f) to color
        }

        append(0f, colors.veryHigh)
        append(stopAt(250f), colors.veryHigh)
        append(stopAt(250f) + epsilon, colors.high)
        append(stopAt(spec.targetHigh), colors.high)
        append(stopAt(spec.targetHigh) + epsilon, colors.inRange)
        append(stopAt(spec.targetLow), colors.inRange)
        append(stopAt(spec.targetLow) + epsilon, colors.low)
        append(stopAt(54f), colors.low)
        append(stopAt(54f) + epsilon, colors.veryLow)
        append(1f, colors.veryLow)

        return Brush.verticalGradient(
            colorStops = stops.toTypedArray(),
            startY = transform.geometry.top,
            endY = transform.geometry.bottom
        )
    }

    private fun prepareColumns(count: Int) {
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

internal data class WearGraphGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

internal data class WearGraphTransform(
    val geometry: WearGraphGeometry,
    val windowStart: Long,
    val windowSpan: Long,
    val minValue: Float,
    val maxValue: Float
) {
    fun x(timestamp: Long): Float {
        val progress = ((timestamp - windowStart).toFloat() / windowSpan).coerceIn(0f, 1f)
        return geometry.left + progress * geometry.width
    }

    fun y(value: Float): Float {
        val fraction = ((value.coerceIn(minValue, maxValue) - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        return geometry.bottom - fraction * geometry.height
    }
}

private fun wearStatusColor(status: GlucoseStatus, colors: ClinicalColors): Color = when (status) {
    GlucoseStatus.VERY_LOW -> colors.veryLow
    GlucoseStatus.LOW -> colors.low
    GlucoseStatus.IN_RANGE -> colors.inRange
    GlucoseStatus.HIGH -> colors.high
    GlucoseStatus.VERY_HIGH -> colors.veryHigh
}
