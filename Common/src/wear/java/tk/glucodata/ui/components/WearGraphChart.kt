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
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.ClinicalColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Everything the canvas needs for one frame, in mutable form.
 *
 * This is deliberately *not* a data class and is not built in composition. It is owned by the screen
 * and updated in place from the draw phase, which is what lets panning invalidate drawing alone: the
 * window lives in a snapshot state that only the draw lambda reads, so a drag re-runs
 * [WearGraphChartRenderer.draw] and nothing else - no recomposition of the screen and, crucially, no
 * fresh immutable spec allocated per frame.
 */
internal class WearGraphChartSpec {
    var series: WearSeries = WearSeries.Empty

    /** Inclusive index range of the points inside the window; `toIndex < fromIndex` means empty. */
    var fromIndex = 0
    var toIndex = -1

    var windowStart = 0L
    var windowEnd = 0L
    var selectedHours = 3
    var axisMax = 200f
    var unit: GlucoseUnit = GlucoseUnit.MG_DL
    var targetLow = 70f
    var targetHigh = 180f

    /** Index of the inspected point, or -1 for none. */
    var selectedIndex = -1

    var clinicalColors: ClinicalColors? = null
    var gridColor: Color = Color.Transparent
    var surfaceColor: Color = Color.Transparent
    var crosshairColor: Color = Color.Transparent
    var highlightColor: Color = Color.Transparent

    val visibleCount: Int get() = if (toIndex < fromIndex) 0 else toIndex - fromIndex + 1
    val isEmpty: Boolean get() = visibleCount == 0
}

/**
 * Draws the wear glucose chart without allocating.
 *
 * The previous version of this renderer built, on *every* frame including every frame of a pan: a
 * new spec, a new geometry object, a new transform object, two gradient brushes (each one an
 * `ArrayList` of ten boxed `Pair`s plus a fresh `Shader`), a new `PathEffect`, a new `Stroke`, a
 * `Calendar.getInstance()`, and a `Date` plus a formatted `String` per gridline. On a watch that is
 * enough churn to hold the render thread behind the GC.
 *
 * Everything that depends only on the chart's size or palette is now created once and reused; what
 * remains per frame is arithmetic and draw calls.
 */
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

    /** Reused rather than `Calendar.getInstance()` per frame. */
    private val calendar = Calendar.getInstance()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val axisBaselineOffset = axisTextSize * 0.36f

    // Padding, resolved once per density.
    private val leftPadPx = with(density) { 2.dp.toPx() }
    private val topPadPx = with(density) { 4.dp.toPx() }
    private val rightGutterPx = with(density) { 30.dp.toPx() }
    private val bottomGutterPx = with(density) { 17.dp.toPx() }
    private val minGutterPx = with(density) { 3.dp.toPx() }
    private val minPlotHeightPx = with(density) { 5.dp.toPx() }
    private val axisInsetPx = with(density) { 3.dp.toPx() }
    private val thinStrokePx = with(density) { 0.8.dp.toPx() }
    private val crosshairStrokePx = with(density) { 1.dp.toPx() }
    private val dashOnPx = with(density) { 5.dp.toPx() }
    private val dashOffPx = with(density) { 3.dp.toPx() }
    private val markerSpacingPx = with(density) { 8.dp.toPx() }
    private val markerRadiusPx = with(density) { 1.4.dp.toPx() }
    private val headGlowPx = with(density) { 6.dp.toPx() }
    private val headRadiusPx = with(density) { 2.2.dp.toPx() }
    private val selectGlowPx = with(density) { 7.dp.toPx() }
    private val selectRadiusPx = with(density) { 4.dp.toPx() }
    private val selectCorePx = with(density) { 2.dp.toPx() }
    private val denseStrokePx = with(density) { 1.7.dp.toPx() }
    private val normalStrokePx = with(density) { 2.dp.toPx() }

    // Reusable geometry and paths.
    private val linePath = Path()
    private val areaPath = Path()
    private val envelopePath = Path()
    private val meanPath = Path()
    private var columnMin = FloatArray(0)
    private var columnMax = FloatArray(0)
    private var columnSum = FloatArray(0)
    private var columnCount = IntArray(0)

    private val denseStroke = Stroke(denseStrokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val normalStroke = Stroke(normalStrokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dashOnPx, dashOffPx), 0f)

    // Chart rectangle and value mapping, refreshed at the start of each frame.
    private var chartLeft = 0f
    private var chartTop = 0f
    private var chartRight = 0f
    private var chartBottom = 0f
    private var chartWidth = 0f
    private var chartHeight = 0f
    private var windowStart = 0L
    private var windowSpanMillis = 1L
    private var minValue = AXIS_MIN_MGDL
    private var maxValue = 200f
    private var valueRange = 1f

    // Value-zone gradient, rebuilt only when the mapping or the palette changes.
    private var cachedBrush: Brush? = null
    private var brushTop = Float.NaN
    private var brushBottom = Float.NaN
    private var brushMin = Float.NaN
    private var brushMax = Float.NaN
    private var brushLow = Float.NaN
    private var brushHigh = Float.NaN
    private var brushPalette: ClinicalColors? = null

    // Ring of recently formatted tick labels. Panning advances ticks one step at a time, so this
    // turns a `SimpleDateFormat.format` per gridline per frame into a cache hit almost every time.
    private val tickLabelTimes = LongArray(TICK_CACHE_SIZE) { Long.MIN_VALUE }
    private val tickLabelTexts = arrayOfNulls<String>(TICK_CACHE_SIZE)
    private var tickLabelCursor = 0

    fun axisCeiling(highest: Float): Float {
        val wanted = highest + 20f
        var i = 0
        while (i < AXIS_LADDER.size) {
            if (AXIS_LADDER[i] >= wanted) return AXIS_LADDER[i]
            i++
        }
        return AXIS_LADDER[AXIS_LADDER.size - 1]
    }

    fun draw(scope: DrawScope, spec: WearGraphChartSpec) {
        val colors = spec.clinicalColors ?: return
        with(scope) {
            if (size.width <= 0f || size.height <= 0f) return
            updateGeometry(size.width, size.height, spec)

            drawTargetBand(spec, colors)
            drawValueAxis(spec)
            drawTimeAxis(spec)
            drawCurve(spec, colors)
            drawSelection(spec)
        }
    }

    private fun updateGeometry(width: Float, height: Float, spec: WearGraphChartSpec) {
        chartLeft = leftPadPx
        chartTop = topPadPx
        chartRight = max(minGutterPx, width - rightGutterPx)
        chartBottom = max(minPlotHeightPx, height - bottomGutterPx)
        chartWidth = max(1f, chartRight - chartLeft)
        chartHeight = max(1f, chartBottom - chartTop)
        windowStart = spec.windowStart
        windowSpanMillis = max(1L, spec.windowEnd - spec.windowStart)
        minValue = AXIS_MIN_MGDL
        maxValue = max(spec.axisMax, spec.targetHigh + 40f)
        valueRange = max(1f, maxValue - minValue)
    }

    private fun xFor(timestamp: Long): Float {
        val progress = ((timestamp - windowStart).toFloat() / windowSpanMillis).coerceIn(0f, 1f)
        return chartLeft + progress * chartWidth
    }

    private fun yFor(value: Float): Float {
        val clamped = if (value < minValue) minValue else if (value > maxValue) maxValue else value
        return chartBottom - ((clamped - minValue) / valueRange) * chartHeight
    }

    private fun DrawScope.drawTargetBand(spec: WearGraphChartSpec, colors: ClinicalColors) {
        val top = yFor(spec.targetHigh)
        val bottom = yFor(spec.targetLow)
        drawRect(
            color = colors.targetRangeShade,
            topLeft = Offset(chartLeft, top),
            size = Size(chartWidth, max(1f, bottom - top))
        )
    }

    private fun DrawScope.drawValueAxis(spec: WearGraphChartSpec) {
        val axisX = size.width - axisInsetPx
        val step = if (spec.unit == GlucoseUnit.MMOL_L) 36f else 50f
        val skipMargin = step * 0.35f
        var axisValue = ceil(minValue / step) * step
        while (axisValue <= maxValue) {
            if (abs(axisValue - spec.targetLow) > skipMargin &&
                abs(axisValue - spec.targetHigh) > skipMargin
            ) {
                val y = yFor(axisValue)
                val label = spec.unit.format(axisValue)
                val labelWidth = axisPaint.measureText(label)
                drawLine(
                    color = spec.gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(axisX - labelWidth - axisInsetPx, y),
                    strokeWidth = thinStrokePx
                )
                drawContext.canvas.nativeCanvas.drawText(label, axisX, y + axisBaselineOffset, axisPaint)
            }
            axisValue += step
        }

        drawTargetLine(spec, spec.targetLow, axisX)
        drawTargetLine(spec, spec.targetHigh, axisX)
    }

    private fun DrawScope.drawTargetLine(spec: WearGraphChartSpec, value: Float, axisX: Float) {
        val y = yFor(value)
        val label = spec.unit.format(value)
        val labelWidth = targetPaint.measureText(label)
        drawLine(
            color = spec.clinicalColors!!.inRange.copy(alpha = 0.65f),
            start = Offset(chartLeft, y),
            end = Offset(axisX - labelWidth - axisInsetPx, y),
            strokeWidth = crosshairStrokePx,
            pathEffect = dashEffect
        )
        drawContext.canvas.nativeCanvas.drawText(label, axisX, y + axisBaselineOffset, targetPaint)
    }

    private fun DrawScope.drawTimeAxis(spec: WearGraphChartSpec) {
        val timeStep = when {
            spec.selectedHours <= 1 -> 15 * 60 * 1000L
            spec.selectedHours <= 3 -> 60 * 60 * 1000L
            spec.selectedHours <= 6 -> 2 * 60 * 60 * 1000L
            else -> 3 * 60 * 60 * 1000L
        }
        calendar.timeInMillis = spec.windowStart
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.SECOND, 0)
        if (timeStep >= 60 * 60 * 1000L) calendar.set(Calendar.MINUTE, 0)
        var tick = calendar.timeInMillis
        if (tick < spec.windowStart) tick += timeStep
        val baseline = size.height - axisInsetPx
        var guard = 0

        while (tick <= spec.windowEnd && guard++ < MAX_TIME_TICKS) {
            val x = xFor(tick)
            val label = tickLabel(tick)
            val half = timePaint.measureText(label) / 2f
            val labelX = if (x < chartLeft + half) chartLeft + half
            else if (x > chartRight - half) chartRight - half
            else x
            drawLine(
                color = spec.gridColor,
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = thinStrokePx
            )
            drawContext.canvas.nativeCanvas.drawText(label, labelX, baseline, timePaint)
            tick += timeStep
        }
    }

    private fun tickLabel(tick: Long): String {
        var i = 0
        while (i < TICK_CACHE_SIZE) {
            if (tickLabelTimes[i] == tick) {
                tickLabelTexts[i]?.let { return it }
                break
            }
            i++
        }
        val text = timeFormatter.format(Date(tick))
        tickLabelTimes[tickLabelCursor] = tick
        tickLabelTexts[tickLabelCursor] = text
        tickLabelCursor = if (tickLabelCursor + 1 == TICK_CACHE_SIZE) 0 else tickLabelCursor + 1
        return text
    }

    private fun DrawScope.drawCurve(spec: WearGraphChartSpec, colors: ClinicalColors) {
        if (spec.isEmpty) return
        val stroke = if (spec.visibleCount > DENSE_POINT_COUNT) denseStroke else normalStroke
        if (spec.visibleCount > chartWidth * 1.2f) {
            drawEnvelope(spec, stroke)
        } else {
            drawSpline(spec, stroke)
        }
        drawSparseMarkers(spec, colors)
        drawHead(spec, colors)
    }

    private fun DrawScope.drawSpline(spec: WearGraphChartSpec, stroke: Stroke) {
        val series = spec.series
        val times = series.times
        val values = series.values
        val line = linePath.apply { reset() }
        val area = areaPath.apply { reset() }
        var hasSegment = false
        var previousX = 0f
        var previousY = 0f
        var segmentStartX = 0f
        var previousTime = Long.MIN_VALUE

        for (i in spec.fromIndex..spec.toIndex) {
            val time = times[i]
            val x = xFor(time)
            val y = yFor(values[i])
            if (!hasSegment || time - previousTime > GAP_MILLIS) {
                if (hasSegment) closeArea(area, previousX, segmentStartX)
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
            previousTime = time
        }
        if (hasSegment) closeArea(area, previousX, segmentStartX)

        val brush = zoneBrush(spec, spec.clinicalColors!!)
        drawPath(path = area, brush = brush, alpha = AREA_ALPHA)
        drawPath(path = line, brush = brush, style = stroke)
    }

    private fun closeArea(area: Path, lastX: Float, startX: Float) {
        area.lineTo(lastX, chartBottom)
        area.lineTo(startX, chartBottom)
        area.close()
    }

    private fun DrawScope.drawEnvelope(spec: WearGraphChartSpec, stroke: Stroke) {
        val series = spec.series
        val times = series.times
        val values = series.values
        val columns = chartWidth.toInt().coerceIn(1, MAX_COLUMNS)
        prepareColumns(columns)
        val columnWidth = chartWidth / columns

        for (i in spec.fromIndex..spec.toIndex) {
            val raw = (((xFor(times[i]) - chartLeft) / chartWidth) * columns).toInt()
            val column = if (raw < 0) 0 else if (raw >= columns) columns - 1 else raw
            val value = values[i]
            if (columnCount[column] == 0) {
                columnMin[column] = value
                columnMax[column] = value
                columnSum[column] = value
            } else {
                columnMin[column] = min(columnMin[column], value)
                columnMax[column] = max(columnMax[column], value)
                columnSum[column] += value
            }
            columnCount[column]++
        }

        val band = envelopePath.apply { reset() }
        val mean = meanPath.apply { reset() }
        var runStart = -1

        for (column in 0..columns) {
            if (column < columns && columnCount[column] > 0) {
                if (runStart < 0) runStart = column
                continue
            }
            if (runStart < 0) continue
            val runEnd = column - 1
            for (c in runStart..runEnd) {
                val x = chartLeft + (c + 0.5f) * columnWidth
                val y = yFor(columnMax[c])
                if (c == runStart) band.moveTo(x, y) else band.lineTo(x, y)
            }
            for (c in runEnd downTo runStart) {
                band.lineTo(chartLeft + (c + 0.5f) * columnWidth, yFor(columnMin[c]))
            }
            band.close()
            for (c in runStart..runEnd) {
                val x = chartLeft + (c + 0.5f) * columnWidth
                val y = yFor(columnSum[c] / columnCount[c])
                if (c == runStart) mean.moveTo(x, y) else mean.lineTo(x, y)
            }
            runStart = -1
        }

        val brush = zoneBrush(spec, spec.clinicalColors!!)
        drawPath(path = band, brush = brush, alpha = ENVELOPE_ALPHA)
        drawPath(path = mean, brush = brush, style = stroke)
    }

    private fun DrawScope.drawSparseMarkers(spec: WearGraphChartSpec, colors: ClinicalColors) {
        val series = spec.series
        val times = series.times
        val values = series.values
        var lastMarkerX = -Float.MAX_VALUE
        for (i in spec.fromIndex..spec.toIndex) {
            val x = xFor(times[i])
            if (x - lastMarkerX >= markerSpacingPx) {
                drawCircle(
                    color = wearStatusColor(series.statusAt(i), colors),
                    radius = markerRadiusPx,
                    center = Offset(x, yFor(values[i]))
                )
                lastMarkerX = x
            }
        }
    }

    private fun DrawScope.drawHead(spec: WearGraphChartSpec, colors: ClinicalColors) {
        val series = spec.series
        val last = spec.toIndex
        val center = Offset(xFor(series.times[last]), yFor(series.values[last]))
        val color = wearStatusColor(series.statusAt(last), colors)
        drawCircle(color.copy(alpha = 0.22f), headGlowPx, center)
        drawCircle(color, headRadiusPx, center)
    }

    private fun DrawScope.drawSelection(spec: WearGraphChartSpec) {
        val selected = spec.selectedIndex
        if (selected < spec.fromIndex || selected > spec.toIndex) return
        val series = spec.series
        val x = xFor(series.times[selected])
        val y = yFor(series.values[selected])
        drawLine(
            color = spec.crosshairColor,
            start = Offset(x, chartTop),
            end = Offset(x, chartBottom),
            strokeWidth = crosshairStrokePx
        )
        drawCircle(spec.highlightColor.copy(alpha = 0.25f), selectGlowPx, Offset(x, y))
        drawCircle(spec.highlightColor, selectRadiusPx, Offset(x, y))
        drawCircle(spec.surfaceColor, selectCorePx, Offset(x, y))
    }

    /**
     * Gradient that paints the curve in the colour of the range it passes through, rebuilt only when
     * the axis mapping or the palette changes rather than on every frame. Panning changes the mapping
     * on the first frame after the window is resized and then holds it steady, so in practice this
     * runs a handful of times over a drag instead of once per frame.
     */
    private fun zoneBrush(spec: WearGraphChartSpec, palette: ClinicalColors): Brush {
        val cached = cachedBrush
        if (cached != null && brushTop == chartTop && brushBottom == chartBottom &&
            brushMin == minValue && brushMax == maxValue &&
            brushLow == spec.targetLow && brushHigh == spec.targetHigh && brushPalette === palette
        ) {
            return cached
        }

        val top = chartTop
        val bottom = chartBottom
        val span = max(1f, bottom - top)
        val eps = 0.0008f

        fun stopAt(value: Float): Float = ((yFor(value) - top) / span).coerceIn(0f, 1f)

        val positions = FloatArray(ZONE_STOP_COUNT)
        val stopColors = arrayOfNulls<Color>(ZONE_STOP_COUNT)
        var previous = 0f
        var slot = 0
        fun append(position: Float, color: Color) {
            val monotonic = max(previous, min(1f, position))
            previous = monotonic
            positions[slot] = monotonic
            stopColors[slot] = color
            slot++
        }

        append(0f, palette.veryHigh)
        append(stopAt(250f), palette.veryHigh)
        append(stopAt(250f) + eps, palette.high)
        append(stopAt(spec.targetHigh), palette.high)
        append(stopAt(spec.targetHigh) + eps, palette.inRange)
        append(stopAt(spec.targetLow), palette.inRange)
        append(stopAt(spec.targetLow) + eps, palette.low)
        append(stopAt(54f), palette.low)
        append(stopAt(54f) + eps, palette.veryLow)
        append(1f, palette.veryLow)

        val colorStops = Array(slot) { index -> positions[index] to requireNotNull(stopColors[index]) }
        val brush = Brush.verticalGradient(colorStops = colorStops, startY = top, endY = bottom)
        cachedBrush = brush
        brushTop = top
        brushBottom = bottom
        brushMin = minValue
        brushMax = maxValue
        brushLow = spec.targetLow
        brushHigh = spec.targetHigh
        brushPalette = palette
        return brush
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

    private companion object {
        const val AXIS_MIN_MGDL = 40f
        const val MAX_COLUMNS = 2048
        const val MAX_TIME_TICKS = 32
        const val TICK_CACHE_SIZE = 16
        const val ZONE_STOP_COUNT = 10
        const val DENSE_POINT_COUNT = 240
        const val GAP_MILLIS = 25 * 60 * 1000L
        const val AREA_ALPHA = 0.12f
        const val ENVELOPE_ALPHA = 0.2f

        val AXIS_LADDER = floatArrayOf(200f, 240f, 280f, 320f, 360f, 420f, 500f, 600f)
    }
}

private fun wearStatusColor(status: GlucoseStatus, colors: ClinicalColors): Color = when (status) {
    GlucoseStatus.VERY_LOW -> colors.veryLow
    GlucoseStatus.LOW -> colors.low
    GlucoseStatus.IN_RANGE -> colors.inRange
    GlucoseStatus.HIGH -> colors.high
    GlucoseStatus.VERY_HIGH -> colors.veryHigh
}
