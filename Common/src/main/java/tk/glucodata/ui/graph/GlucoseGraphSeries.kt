package tk.glucodata.ui.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import tk.glucodata.ui.theme.ClinicalColors

private const val STREAM_GAP_MILLIS = 25 * 60 * 1000L
private const val HISTORY_GAP_MILLIS = 35 * 60 * 1000L

/**
 * The main sensor curve. Two modes: a smoothed spline while individual readings are resolvable,
 * and a per-pixel min/max envelope once the window holds more points than the chart has columns -
 * which keeps multi-day views both honest (peaks survive) and cheap.
 */
internal fun DrawScope.drawCurveLayer(
    chart: ChartTransform,
    series: GraphSeries,
    clinicalColors: ClinicalColors,
    targetLow: Float,
    targetHigh: Float,
    surfaceColor: Color,
    scratch: GraphScratch,
    showHead: Boolean
) {
    if (series.isEmpty) return
    val from = (series.firstIndexAtOrAfter(chart.startTime) - 1).coerceAtLeast(0)
    val to = (series.lastIndexAtOrBefore(chart.endTime) + 1).coerceAtMost(series.size - 1)
    if (to < from) return

    val brushes = scratch.zoneBrushes(chart, clinicalColors, targetLow, targetHigh)
    val visibleCount = to - from + 1

    if (visibleCount > chart.metrics.chartWidth * 1.2f) {
        drawEnvelope(chart, series, from, to, brushes, scratch)
    } else {
        drawSpline(chart, series, from, to, brushes, scratch)
    }

    if (showHead && to == series.size - 1) {
        val headTime = series.times[to]
        if (headTime in chart.startTime..chart.endTime) {
            val hx = chart.x(headTime)
            val hy = chart.y(series.values[to])
            val color = statusColor(series.statuses[to].toInt(), clinicalColors)
            drawCircle(color.copy(alpha = 0.20f), chart.px(6f), Offset(hx, hy))
            drawCircle(surfaceColor, chart.px(3.2f), Offset(hx, hy))
            drawCircle(color, chart.px(2.2f), Offset(hx, hy))
        }
    }
}

private fun DrawScope.drawSpline(
    chart: ChartTransform,
    series: GraphSeries,
    from: Int,
    to: Int,
    brushes: ZoneBrushes,
    scratch: GraphScratch
) {
    val line = scratch.linePath.apply { reset() }
    val area = scratch.areaPath.apply { reset() }
    val bottom = chart.metrics.chartBottom
    var segmentStartX = 0f
    var hasSegment = false
    var prevX = 0f
    var prevY = 0f

    fun closeSegment() {
        if (!hasSegment) return
        area.lineTo(prevX, bottom)
        area.lineTo(segmentStartX, bottom)
        area.close()
        hasSegment = false
    }

    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        val gap = i > from && (series.times[i] - series.times[i - 1] > STREAM_GAP_MILLIS)

        if (!hasSegment || gap) {
            closeSegment()
            line.moveTo(x, y)
            area.moveTo(x, bottom)
            area.lineTo(x, y)
            segmentStartX = x
            hasSegment = true
        } else {
            val midX = (prevX + x) / 2f
            line.cubicTo(midX, prevY, midX, y, x, y)
            area.cubicTo(midX, prevY, midX, y, x, y)
        }
        prevX = x
        prevY = y
    }
    closeSegment()

    drawPath(area, brushes.area)
    drawPath(
        path = line,
        brush = brushes.line,
        style = Stroke(width = chart.px(2.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawEnvelope(
    chart: ChartTransform,
    series: GraphSeries,
    from: Int,
    to: Int,
    brushes: ZoneBrushes,
    scratch: GraphScratch
) {
    val metrics = chart.metrics
    val columns = metrics.chartWidth.toInt().coerceIn(1, GraphScratch.MAX_COLUMNS)
    scratch.prepareColumns(columns)
    val minV = scratch.columnMin
    val maxV = scratch.columnMax
    val sumV = scratch.columnSum
    val counts = scratch.columnCount

    for (i in from..to) {
        val time = series.times[i]
        if (time < chart.startTime || time > chart.endTime) continue
        val fraction = (time - chart.startTime).toFloat() / chart.spanMillis
        val col = (fraction * columns).toInt().coerceIn(0, columns - 1)
        val v = series.values[i]
        if (counts[col] == 0) {
            minV[col] = v
            maxV[col] = v
            sumV[col] = v
        } else {
            if (v < minV[col]) minV[col] = v
            if (v > maxV[col]) maxV[col] = v
            sumV[col] += v
        }
        counts[col]++
    }

    val band = scratch.areaPath.apply { reset() }
    val mean = scratch.linePath.apply { reset() }
    val columnWidth = metrics.chartWidth / columns
    var runStart = -1

    fun flushRun(runEnd: Int) {
        if (runStart < 0) return
        for (c in runStart..runEnd) {
            val x = metrics.chartLeft + (c + 0.5f) * columnWidth
            val y = chart.y(maxV[c])
            if (c == runStart) band.moveTo(x, y) else band.lineTo(x, y)
        }
        for (c in runEnd downTo runStart) {
            val x = metrics.chartLeft + (c + 0.5f) * columnWidth
            band.lineTo(x, chart.y(minV[c]))
        }
        band.close()
        for (c in runStart..runEnd) {
            val x = metrics.chartLeft + (c + 0.5f) * columnWidth
            val y = chart.y(sumV[c] / counts[c])
            if (c == runStart) mean.moveTo(x, y) else mean.lineTo(x, y)
        }
        runStart = -1
    }

    for (c in 0 until columns) {
        if (counts[c] > 0) {
            if (runStart < 0) runStart = c
        } else if (runStart >= 0) {
            flushRun(c - 1)
        }
    }
    if (runStart >= 0) flushRun(columns - 1)

    drawPath(band, brushes.line, alpha = 0.28f)
    drawPath(mean, brushes.line, style = Stroke(width = chart.px(1.6f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 15-minute sensor history: dots joined by a light dashed line. */
internal fun DrawScope.drawPointLayer(
    chart: ChartTransform,
    series: GraphSeries,
    color: Color,
    scratch: GraphScratch
) {
    if (series.isEmpty) return
    val from = (series.firstIndexAtOrAfter(chart.startTime) - 1).coerceAtLeast(0)
    val to = (series.lastIndexAtOrBefore(chart.endTime) + 1).coerceAtMost(series.size - 1)
    if (to < from) return

    val path = scratch.dashedPath.apply { reset() }
    var started = false
    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        val gap = i > from && (series.times[i] - series.times[i - 1] > HISTORY_GAP_MILLIS)
        if (!started || gap) path.moveTo(x, y) else path.lineTo(x, y)
        started = true
    }
    drawPath(
        path = path,
        color = color.copy(alpha = 0.55f),
        style = Stroke(
            width = chart.px(1.2f),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(chart.px(4f), chart.px(4f)), 0f)
        )
    )

    val radius = chart.px(1.8f)
    val minSpacing = chart.px(6f)
    var lastX = -Float.MAX_VALUE
    for (i in from..to) {
        val x = chart.x(series.times[i])
        if (x - lastX < minSpacing) continue
        lastX = x
        drawCircle(color, radius, Offset(x, chart.y(series.values[i])))
    }
}

internal fun DrawScope.drawDashedCurve(
    chart: ChartTransform,
    series: GraphSeries,
    color: Color,
    scratch: GraphScratch
) {
    if (series.isEmpty) return
    val from = (series.firstIndexAtOrAfter(chart.startTime) - 1).coerceAtLeast(0)
    val to = (series.lastIndexAtOrBefore(chart.endTime) + 1).coerceAtMost(series.size - 1)
    if (to < from) return

    val path = scratch.dashedPath.apply { reset() }
    var started = false
    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        val gap = i > from && (series.times[i] - series.times[i - 1] > STREAM_GAP_MILLIS)
        if (!started || gap) path.moveTo(x, y) else path.lineTo(x, y)
        started = true
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = chart.px(1.8f),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(chart.px(6f), chart.px(4f)), 0f)
        )
    )
}

internal fun DrawScope.drawScanLayer(
    chart: ChartTransform,
    series: GraphSeries,
    color: Color,
    surfaceColor: Color,
    scratch: GraphScratch
) {
    if (series.isEmpty) return
    val from = series.firstIndexAtOrAfter(chart.startTime)
    val to = series.lastIndexAtOrBefore(chart.endTime)
    if (to < from) return

    val path = scratch.markerPath
    val r = chart.px(4.5f)
    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        path.reset()
        path.moveTo(x, y - r)
        path.lineTo(x + r, y)
        path.lineTo(x, y + r)
        path.lineTo(x - r, y)
        path.close()
        drawPath(path, color)
        drawPath(path, surfaceColor, style = Stroke(width = chart.px(1f)))
    }
}
