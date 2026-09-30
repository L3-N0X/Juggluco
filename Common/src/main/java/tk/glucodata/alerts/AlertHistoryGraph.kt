package tk.glucodata.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.ui.data.NativeHistory
import tk.glucodata.ui.model.GlucoseRange
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.ClinicalColors
import kotlin.math.max

/** The last few hours of readings around an alert, with the ranges they are judged against. */
@Immutable
class AlertHistory(
    val times: LongArray,
    val values: FloatArray,
    val range: GlucoseRange,
    val windowMillis: Long
) {
    val isEmpty: Boolean get() = times.isEmpty()

    companion object {
        const val DEFAULT_HOURS = 3
        val Empty = AlertHistory(LongArray(0), FloatArray(0), GlucoseRange(), DEFAULT_HOURS * 3_600_000L)

        /** Reads the stored history plus [alert]'s own reading when the stream does not have it yet. Call off the main thread. */
        fun load(alert: AlertPlayer.ActiveAlert, hours: Int = DEFAULT_HOURS): AlertHistory {
            val windowMillis = hours * 3_600_000L
            if (!Applic.Nativesloaded) return Empty
            val unit = GlucoseUnit.fromNative(Applic.unit)
            val range = try {
                GlucoseRange(
                    veryLowMgDl = unit.toMgDl(Natives.verylow()),
                    lowMgDl = unit.toMgDl(Natives.targetlow()),
                    highMgDl = unit.toMgDl(Natives.targethigh()),
                    veryHighMgDl = unit.toMgDl(Natives.veryhigh())
                ).normalized()
            } catch (_: Throwable) {
                GlucoseRange()
            }
            var times = LongArray(0)
            var values = FloatArray(0)
            try {
                val history = NativeHistory.read(System.currentTimeMillis() - windowMillis)
                times = history.first
                values = history.second
            } catch (_: Throwable) {
            }
            alert.reading?.takeIf { it.mgdl > 0f && it.timeMillis > 0L }?.let { reading ->
                if (times.isEmpty() || reading.timeMillis > times.last() + 30_000L) {
                    times += reading.timeMillis
                    values += reading.mgdl
                }
            }
            return AlertHistory(times, values, range, windowMillis)
        }
    }
}

/** The level [rule] triggers at, for the kinds that have one; rate and signal alerts have no line to draw. */
fun AlertRule.graphThresholdMgDl(): Float? = when (kind) {
    AlertKind.LOW, AlertKind.HIGH -> thresholdMgdl
    else -> null
}

/** History for [alert], read off the main thread and refreshed when a newer reading arrives for the same alert. */
@Composable
fun rememberAlertHistory(alert: AlertPlayer.ActiveAlert, hours: Int = AlertHistory.DEFAULT_HOURS): State<AlertHistory> =
    produceState(AlertHistory.Empty, alert.rule.id, alert.startedAt, alert.reading?.timeMillis, hours) {
        value = withContext(Dispatchers.Default) { AlertHistory.load(alert, hours) }
    }

/**
 * Compact trend graph for the full-screen alerts: target band, the alert's threshold as a dashed
 * line, the curve in the colour of the range it passes through and a dot on the latest reading.
 * Colours are passed in so the phone (Material 3) and the watch (Wear Material 3) theme it alike.
 */
@Composable
fun AlertHistoryGraph(
    history: AlertHistory,
    clinical: ClinicalColors,
    surfaceColor: Color,
    highlightColor: Color,
    thresholdMgDl: Float?,
    modifier: Modifier = Modifier.fillMaxWidth().height(AlertGraphDefaultHeight)
) {
    val density = LocalDensity.current
    val linePath = remember { Path() }
    val areaPath = remember { Path() }
    val lineStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val guideStroke = remember(density) { with(density) { 1.dp.toPx() } }
    val guideDash = remember(density) {
        PathEffect.dashPathEffect(floatArrayOf(with(density) { 5.dp.toPx() }, with(density) { 3.dp.toPx() }), 0f)
    }
    val dotGlow = remember(density) { with(density) { 8.dp.toPx() } }
    val dotRing = remember(density) { with(density) { 4.5.dp.toPx() } }
    val dotCore = remember(density) { with(density) { 3.dp.toPx() } }

    // Scale to what is on screen (plus the very high edge and the threshold), not to a fixed axis.
    val range = history.range
    val axis = remember(history, thresholdMgDl) {
        var highest = range.veryHighMgDl
        var lowest = range.veryLowMgDl
        for (value in history.values) {
            if (value > highest) highest = value
            if (value < lowest) lowest = value
        }
        if (thresholdMgDl != null) {
            highest = max(highest, thresholdMgDl)
            lowest = minOf(lowest, thresholdMgDl)
        }
        AxisBounds(minOf(lowest, range.veryLowMgDl) - 10f, highest + 20f)
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f || history.isEmpty) return@Canvas

        val now = history.times.last()
        val windowStart = now - history.windowMillis
        val valueSpan = (axis.max - axis.min).coerceAtLeast(1f)
        fun yFor(value: Float): Float = height - ((value.coerceIn(axis.min, axis.max) - axis.min) / valueSpan) * height
        fun xFor(time: Long): Float = ((time - windowStart).toFloat() / history.windowMillis).coerceIn(0f, 1f) * width

        val bandTop = yFor(range.highMgDl)
        val bandBottom = yFor(range.lowMgDl)
        drawRect(
            color = clinical.targetRangeShade,
            topLeft = Offset(0f, bandTop),
            size = Size(width, (bandBottom - bandTop).coerceAtLeast(0f))
        )

        val rangeLine = clinical.inRange.copy(alpha = 0.45f)
        for (edge in floatArrayOf(range.highMgDl, range.lowMgDl)) {
            drawLine(rangeLine, Offset(0f, yFor(edge)), Offset(width, yFor(edge)), guideStroke, pathEffect = guideDash)
        }
        if (thresholdMgDl != null) {
            val y = yFor(thresholdMgDl)
            drawLine(highlightColor, Offset(0f, y), Offset(width, y), guideStroke * 1.5f, pathEffect = guideDash)
        }

        // Midpoint-smoothed curve that breaks on a data gap instead of bridging a dropped sensor.
        val line = linePath.apply { reset() }
        val area = areaPath.apply { reset() }
        var hasSegment = false
        var previousX = 0f
        var previousY = 0f
        var segmentStartX = 0f
        var previousTime = Long.MIN_VALUE
        for (i in history.times.indices) {
            val time = history.times[i]
            if (time < windowStart) continue
            val x = xFor(time)
            val y = yFor(history.values[i])
            if (!hasSegment || time - previousTime > GAP_MILLIS) {
                if (hasSegment) closeArea(area, previousX, segmentStartX, height)
                line.moveTo(x, y)
                area.moveTo(x, height)
                area.lineTo(x, y)
                segmentStartX = x
                hasSegment = true
            } else {
                val midX = (previousX + x) / 2f
                line.cubicTo(midX, previousY, midX, y, x, y)
                area.cubicTo(midX, previousY, midX, y, x, y)
            }
            previousX = x
            previousY = y
            previousTime = time
        }
        if (!hasSegment) return@Canvas
        closeArea(area, previousX, segmentStartX, height)

        val brush = zoneBrush(axis, range, clinical, height)
        drawPath(area, brush = brush, alpha = AREA_ALPHA)
        drawPath(line, brush = brush, style = lineStroke)

        val last = Offset(previousX, previousY)
        val dotColor = when (range.statusOf(history.values.last())) {
            GlucoseStatus.VERY_LOW -> clinical.veryLow
            GlucoseStatus.LOW -> clinical.low
            GlucoseStatus.IN_RANGE -> clinical.inRange
            GlucoseStatus.HIGH -> clinical.high
            GlucoseStatus.VERY_HIGH -> clinical.veryHigh
        }
        drawCircle(dotColor.copy(alpha = 0.22f), dotGlow, last)
        drawCircle(surfaceColor, dotRing, last)
        drawCircle(dotColor, dotCore, last)
    }
}

private class AxisBounds(val min: Float, val max: Float)

private fun closeArea(area: Path, lastX: Float, startX: Float, bottom: Float) {
    area.lineTo(lastX, bottom)
    area.lineTo(startX, bottom)
    area.close()
}

/** Vertical gradient that paints the curve in the colour of the band it passes through, with hard edges at the band limits. */
private fun zoneBrush(axis: AxisBounds, range: GlucoseRange, palette: ClinicalColors, height: Float): Brush {
    val span = (axis.max - axis.min).coerceAtLeast(1f)
    fun stopAt(value: Float): Float = 1f - ((value.coerceIn(axis.min, axis.max) - axis.min) / span)
    val eps = 0.0008f
    val stops = ArrayList<Pair<Float, Color>>(10)
    var previous = 0f
    fun add(position: Float, color: Color) {
        previous = max(previous, position.coerceAtMost(1f))
        stops.add(previous to color)
    }
    add(0f, palette.veryHigh)
    add(stopAt(range.veryHighMgDl), palette.veryHigh)
    add(stopAt(range.veryHighMgDl) + eps, palette.high)
    add(stopAt(range.highMgDl), palette.high)
    add(stopAt(range.highMgDl) + eps, palette.inRange)
    add(stopAt(range.lowMgDl), palette.inRange)
    add(stopAt(range.lowMgDl) + eps, palette.low)
    add(stopAt(range.veryLowMgDl), palette.low)
    add(stopAt(range.veryLowMgDl) + eps, palette.veryLow)
    add(1f, palette.veryLow)
    return Brush.verticalGradient(colorStops = stops.toTypedArray(), startY = 0f, endY = height)
}

val AlertGraphDefaultHeight: Dp = 140.dp

private const val GAP_MILLIS = 25 * 60 * 1000L
private const val AREA_ALPHA = 0.12f
