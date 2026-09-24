package tk.glucodata.ui.graph

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.ClinicalColors
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil

private const val HOUR_MILLIS = 3600 * 1000L

internal fun DrawScope.drawTargetBand(
    chart: ChartTransform,
    targetLow: Float,
    targetHigh: Float,
    bandColor: Color
) {
    val topY = chart.y(targetHigh)
    val bottomY = chart.y(targetLow)
    drawRect(
        color = bandColor,
        topLeft = Offset(chart.metrics.chartLeft, topY),
        size = Size(chart.metrics.chartWidth, (bottomY - topY).coerceAtLeast(1f))
    )
}

/**
 * Right-hand value axis with right-aligned labels.
 * Each line (both grid lines and dashed target boundary lines) extends continuously
 * across the chart up to the start of its own number.
 */
internal fun DrawScope.drawValueAxis(
    chart: ChartTransform,
    unit: GlucoseUnit,
    targetLow: Float,
    targetHigh: Float,
    gridColor: Color,
    paints: GraphPaints,
    clinicalColors: ClinicalColors
) {
    val metrics = chart.metrics
    val step = if (unit == GlucoseUnit.MMOL_L) 36f else 50f
    val strokeWidth = chart.px(0.8f)
    val baseline = paints.axisBaselineOffset

    var value = ceil(chart.minValue / step) * step
    if (value == chart.minValue) value += step
    while (value < chart.maxValue) {
        if (abs(value - targetLow) > step * 0.35f && abs(value - targetHigh) > step * 0.35f) {
            val y = chart.y(value)
            val label = unit.format(value)
            val labelWidth = paints.axis.measureText(label)
            val lineEnd = (metrics.labelAnchorX - labelWidth - metrics.labelGap)
                .coerceAtLeast(metrics.chartLeft + 1f)
            drawLine(gridColor, Offset(metrics.chartLeft, y), Offset(lineEnd, y), strokeWidth)
            drawContext.canvas.nativeCanvas.drawText(label, metrics.labelAnchorX, y + baseline, paints.axis)
        }
        value += step
    }

    drawAxisTargetBoundary(chart, targetHigh, unit.format(targetHigh), paints, clinicalColors)
    drawAxisTargetBoundary(chart, targetLow, unit.format(targetLow), paints, clinicalColors)
}

private fun DrawScope.drawAxisTargetBoundary(
    chart: ChartTransform,
    targetValue: Float,
    label: String,
    paints: GraphPaints,
    clinicalColors: ClinicalColors
) {
    val metrics = chart.metrics
    val y = chart.y(targetValue)
    val labelWidth = paints.target.measureText(label)
    val lineEnd = (metrics.labelAnchorX - labelWidth - metrics.labelGap).coerceAtLeast(metrics.chartLeft + 1f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(chart.px(6f), chart.px(4f)), 0f)
    val targetLineColor = clinicalColors.inRange.copy(alpha = 0.65f)

    drawLine(
        color = targetLineColor,
        start = Offset(metrics.chartLeft, y),
        end = Offset(lineEnd, y),
        strokeWidth = chart.px(1.2f),
        pathEffect = dash
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        metrics.labelAnchorX,
        y + paints.axisBaselineOffset,
        paints.target
    )
}

/** Adaptive time grid. Midnight gets a stronger line and a date label instead of "00:00". */
internal fun DrawScope.drawTimeAxis(
    chart: ChartTransform,
    windowDuration: Long,
    gridColor: Color,
    dayLineColor: Color,
    paints: GraphPaints
) {
    val metrics = chart.metrics
    val hours = windowDuration / HOUR_MILLIS.toFloat()
    val stepHours = when {
        hours <= 1.5f -> 0.25f
        hours <= 3.5f -> 1f
        hours <= 8f -> 2f
        hours <= 16f -> 3f
        hours <= 30f -> 6f
        hours <= 80f -> 12f
        hours <= 200f -> 24f
        else -> 48f
    }
    val stepMillis = (stepHours * HOUR_MILLIS).toLong()
    val showTimeOfDay = hours <= 80f

    val cal = Calendar.getInstance()
    cal.timeInMillis = chart.startTime
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.SECOND, 0)
    if (stepMillis >= HOUR_MILLIS) cal.set(Calendar.MINUTE, 0)
    if (stepMillis >= 24 * HOUR_MILLIS) cal.set(Calendar.HOUR_OF_DAY, 0)

    var tick = cal.timeInMillis
    while (tick < chart.startTime) tick += stepMillis
    val labelY = metrics.chartBottom + paints.timeLabelOffset
    val gridStroke = chart.px(0.8f)
    val dayStroke = chart.px(1.2f)
    var guard = 0

    while (tick <= chart.endTime && guard++ < 256) {
        val x = chart.x(tick)
        val isMidnight = isMidnight(tick, cal)
        drawLine(
            color = if (isMidnight) dayLineColor else gridColor,
            start = Offset(x, metrics.chartTop),
            end = Offset(x, metrics.chartBottom),
            strokeWidth = if (isMidnight) dayStroke else gridStroke
        )

        val label: String
        val paint: Paint
        if (isMidnight || !showTimeOfDay) {
            label = paints.dayFormat.format(Date(tick))
            paint = paints.dayLabel
        } else {
            label = paints.timeFormat.format(Date(tick))
            paint = paints.time
        }
        val half = paint.measureText(label) / 2f
        val labelX = x.coerceIn(metrics.chartLeft + half, metrics.chartRight - half)
        drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, paint)
        tick += stepMillis
    }
}

private fun isMidnight(timeMillis: Long, cal: Calendar): Boolean {
    cal.timeInMillis = timeMillis
    return cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
}
