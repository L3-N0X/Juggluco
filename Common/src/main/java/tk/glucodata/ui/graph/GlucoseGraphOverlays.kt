package tk.glucodata.ui.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.ClinicalColors
import tk.glucodata.ui.theme.LogbookColors

internal fun DrawScope.drawAlertIndicators(
    chart: ChartTransform,
    events: GraphAlertEvents,
    alertColor: Color,
    markerColor: Color,
    markerContentColor: Color
) {
    if (events.size == 0) return
    var index = events.firstIndexAtOrAfter(chart.startTime)
    val groupingDistance = chart.px(8f)
    val radius = chart.px(5.5f)
    val centerY = chart.metrics.chartTop + radius
    val dash = PathEffect.dashPathEffect(floatArrayOf(chart.px(3f), chart.px(4f)), 0f)

    while (index < events.size && events.times[index] <= chart.endTime) {
        var markerX = chart.x(events.times[index])
        var count = 1
        while (index + 1 < events.size && events.times[index + 1] <= chart.endTime) {
            val nextX = chart.x(events.times[index + 1])
            if (nextX - markerX > groupingDistance) break
            index++
            markerX = nextX
            count++
        }

        drawLine(
            color = alertColor.copy(alpha = 0.38f),
            start = Offset(markerX, centerY + radius + chart.px(2f)),
            end = Offset(markerX, chart.metrics.chartBottom),
            strokeWidth = chart.px(1.2f),
            pathEffect = dash
        )
        drawCircle(markerColor, radius, Offset(markerX, centerY))
        drawCircle(
            color = alertColor,
            radius = radius,
            center = Offset(markerX, centerY),
            style = Stroke(width = chart.px(1.2f))
        )
        drawLine(
            color = markerContentColor,
            start = Offset(markerX, centerY - chart.px(2.4f)),
            end = Offset(markerX, centerY + chart.px(0.8f)),
            strokeWidth = chart.px(1.2f),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = markerContentColor,
            radius = chart.px(0.7f),
            center = Offset(markerX, centerY + chart.px(2.7f))
        )
        if (count > 1) {
            drawCircle(
                color = alertColor,
                radius = radius + chart.px(2f),
                center = Offset(markerX, centerY),
                style = Stroke(width = chart.px(0.8f))
            )
        }
        index++
    }
}

internal fun DrawScope.drawEventStrip(
    chart: ChartTransform,
    events: GraphEvents,
    unit: GlucoseUnit,
    surfaceColor: Color,
    paints: GraphPaints,
    logbookColors: LogbookColors
) {
    if (events.size == 0) return
    val y = chart.metrics.chartBottom - chart.px(4f)
    val radius = chart.px(4f)
    var index = events.firstIndexAtOrAfter(chart.startTime)
    var lastLabelX = -Float.MAX_VALUE
    val labelSpacing = chart.px(18f)

    while (index < events.size && events.times[index] <= chart.endTime) {
        val record = events.records[index]
        val x = chart.x(record.timestamp)
        val color = logbookColors.colorFor(record.type)
        drawCircle(color, radius, Offset(x, y))
        drawCircle(surfaceColor, radius * 0.4f, Offset(x, y))

        if (x - lastLabelX >= labelSpacing) {
            lastLabelX = x
            paints.event.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                eventLabel(record, unit),
                x,
                y - chart.px(7f),
                paints.event
            )
        }
        index++
    }
}

internal fun DrawScope.drawScrubber(
    chart: ChartTransform,
    reading: InspectedReading,
    clinicalColors: ClinicalColors,
    textPrimary: Color,
    surfaceColor: Color
) {
    if (reading.timestamp < chart.startTime || reading.timestamp > chart.endTime) return
    val x = chart.x(reading.timestamp)
    val y = chart.y(reading.valueMgDl)
    drawLine(
        color = textPrimary.copy(alpha = 0.55f),
        start = Offset(x, chart.metrics.chartTop),
        end = Offset(x, chart.metrics.chartBottom),
        strokeWidth = chart.px(1f),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(chart.px(3f), chart.px(3f)), 0f)
    )
    val color = statusColor(reading.statusOrdinal, clinicalColors)
    drawCircle(color.copy(alpha = 0.22f), chart.px(9f), Offset(x, y))
    drawCircle(surfaceColor, chart.px(4.5f), Offset(x, y))
    drawCircle(color, chart.px(3.2f), Offset(x, y))
}
