package tk.glucodata.ui.graph

import tk.glucodata.ui.model.GlucoseRange
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
 *
 * Two sensors worn in parallel produce two interleaved curves in one [series]. They are never
 * stitched into each other: the curve is cut wherever the sensor changes, and every curve that
 * belongs to a sensor other than [primarySensorId] is darkened from [primaryStartTime] onwards,
 * which is where that sensor started and the two began to overlap. Before that point the older
 * sensor is on its own and keeps the normal colours, so the dimming always marks the overlap.
 */
internal fun DrawScope.drawCurveLayer(
    chart: ChartTransform,
    series: GraphSeries,
    clinicalColors: ClinicalColors,
    range: GlucoseRange,
    surfaceColor: Color,
    scratch: GraphScratch,
    showHead: Boolean,
    primarySensorId: Int,
    primaryStartTime: Long
) {
    if (series.isEmpty) return
    val from = (series.firstIndexAtOrAfter(chart.startTime) - 1).coerceAtLeast(0)
    val to = (series.lastIndexAtOrBefore(chart.endTime) + 1).coerceAtMost(series.size - 1)
    if (to < from) return

    val brushes = scratch.zoneBrushes(chart, clinicalColors, range)
    val dimmedBrushes = scratch.zoneBrushes(chart, clinicalColors, range, dimmed = true)
    val visibleCount = to - from + 1

    if (visibleCount > chart.metrics.chartWidth * 1.2f) {
        drawEnvelope(chart, series, from, to, brushes, dimmedBrushes, primarySensorId, primaryStartTime, scratch)
    } else {
        drawSpline(chart, series, from, to, brushes, dimmedBrushes, primarySensorId, primaryStartTime, scratch)
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

/**
 * A point is dimmed when it belongs to a sensor other than the primary one *and* the primary
 * sensor had already started by then, so the darkening begins exactly at the first overlap.
 */
private fun GraphSeries.isDimmedAt(index: Int, primarySensorId: Int, primaryStartTime: Long): Boolean =
    sensorIds[index] != primarySensorId && times[index] >= primaryStartTime

/** Starts a new curve run: no point before it, a reading gap, a different sensor, or a dim switch. */
private fun GraphSeries.breaksRunAt(index: Int, from: Int, primarySensorId: Int, primaryStartTime: Long): Boolean {
    if (index <= from) return false
    val previous = index - 1
    return times[index] - times[previous] > STREAM_GAP_MILLIS ||
        sensorIds[index] != sensorIds[previous] ||
        isDimmedAt(index, primarySensorId, primaryStartTime) != isDimmedAt(previous, primarySensorId, primaryStartTime)
}

/**
 * Smoothed curve, drawn as one run per sensor per dim state. The two passes rebuild the identical
 * runs: all areas go down first, so an older sensor's fill can never paint over the primary curve
 * that shares the same columns, and only then are the curves stroked on top.
 */
private fun DrawScope.drawSpline(
    chart: ChartTransform,
    series: GraphSeries,
    from: Int,
    to: Int,
    brushes: ZoneBrushes,
    dimmedBrushes: ZoneBrushes,
    primarySensorId: Int,
    primaryStartTime: Long,
    scratch: GraphScratch
) {
    val bottom = chart.metrics.chartBottom

    val area = scratch.areaPath.apply { reset() }
    var areaStartX = 0f
    var areaOpen = false
    var areaDimmed = false
    var areaPrevX = 0f
    var areaPrevY = 0f

    fun flushArea() {
        if (!areaOpen) return
        area.lineTo(areaPrevX, bottom)
        area.lineTo(areaStartX, bottom)
        area.close()
        drawPath(area, if (areaDimmed) dimmedBrushes.area else brushes.area)
        area.reset()
        areaOpen = false
    }

    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        if (!areaOpen || series.breaksRunAt(i, from, primarySensorId, primaryStartTime)) {
            flushArea()
            area.moveTo(x, bottom)
            area.lineTo(x, y)
            areaStartX = x
            areaDimmed = series.isDimmedAt(i, primarySensorId, primaryStartTime)
            areaOpen = true
        } else {
            val midX = (areaPrevX + x) / 2f
            area.cubicTo(midX, areaPrevY, midX, y, x, y)
        }
        areaPrevX = x
        areaPrevY = y
    }
    flushArea()

    val line = scratch.linePath.apply { reset() }
    var lineOpen = false
    var lineDimmed = false
    var linePrevX = 0f
    var linePrevY = 0f

    fun flushLine() {
        if (!lineOpen) return
        drawPath(
            path = line,
            brush = if (lineDimmed) dimmedBrushes.line else brushes.line,
            style = Stroke(width = chart.px(2.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        line.reset()
        lineOpen = false
    }

    for (i in from..to) {
        val x = chart.x(series.times[i])
        val y = chart.y(series.values[i])
        if (!lineOpen || series.breaksRunAt(i, from, primarySensorId, primaryStartTime)) {
            flushLine()
            line.moveTo(x, y)
            lineDimmed = series.isDimmedAt(i, primarySensorId, primaryStartTime)
            lineOpen = true
        } else {
            val midX = (linePrevX + x) / 2f
            line.cubicTo(midX, linePrevY, midX, y, x, y)
        }
        linePrevX = x
        linePrevY = y
    }
    flushLine()
}

/**
 * Per-column min/max envelope with a mean line, used once the window holds more points than the
 * chart has columns. A column that saw a primary-sensor reading is drawn in the normal colours; a
 * column holding only an older sensor's readings is drawn dimmed, so a zoomed-out view darkens
 * exactly where the two sensors overlap rather than merging them into one band.
 */
private fun DrawScope.drawEnvelope(
    chart: ChartTransform,
    series: GraphSeries,
    from: Int,
    to: Int,
    brushes: ZoneBrushes,
    dimmedBrushes: ZoneBrushes,
    primarySensorId: Int,
    primaryStartTime: Long,
    scratch: GraphScratch
) {
    val metrics = chart.metrics
    val columns = metrics.chartWidth.toInt().coerceIn(1, GraphScratch.MAX_COLUMNS)
    scratch.prepareColumns(columns)
    val minV = scratch.columnMin
    val maxV = scratch.columnMax
    val sumV = scratch.columnSum
    val counts = scratch.columnCount
    val primary = scratch.columnPrimary

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
        if (!series.isDimmedAt(i, primarySensorId, primaryStartTime)) primary[col] = true
    }

    val band = scratch.areaPath.apply { reset() }
    val mean = scratch.linePath.apply { reset() }
    val columnWidth = metrics.chartWidth / columns
    var runStart = -1
    var runPrimary = false

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
        // Runs are horizontally disjoint, so each can be drawn as soon as it is complete and the
        // dimming decided per run instead of needing a second pass.
        val runBrushes = if (runPrimary) brushes.line else dimmedBrushes.line
        drawPath(band, runBrushes, alpha = 0.28f)
        drawPath(mean, runBrushes, style = Stroke(width = chart.px(1.6f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        band.reset()
        mean.reset()
        runStart = -1
    }

    for (c in 0 until columns) {
        if (counts[c] > 0) {
            if (runStart < 0) {
                runStart = c
                runPrimary = primary[c]
            } else if (primary[c] != runPrimary) {
                // A column of the other kind starts a new run rather than being blended into this.
                flushRun(c - 1)
                runStart = c
                runPrimary = primary[c]
            }
        } else if (runStart >= 0) {
            flushRun(c - 1)
        }
    }
    if (runStart >= 0) flushRun(columns - 1)
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
