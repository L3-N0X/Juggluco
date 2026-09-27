package tk.glucodata.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.theme.ClinicalColors
import tk.glucodata.ui.theme.LocalClinicalColors
import kotlin.math.max
import kotlin.math.min

/**
 * Sparkline for the home screen.
 *
 * Backed by the same prepared [WearSeries] as the full graph, which fixes the other half of the
 * duplicate-reading problem: this used to plot the raw *and* the calibrated value for every
 * timestamp, so the sparkline was a picket fence rather than a trend line.
 *
 * It draws with the same conventions as the full graph and the phone graph rather than inventing its
 * own: the curve is a midpoint-smoothed spline that breaks on a data gap instead of bridging a
 * dropped sensor, it is filled with the same low-alpha area pass, and - the part that matters most on
 * a two-hour view - it is painted with the same vertical value-zone gradient as [WearGraphChart] and
 * `GlucoseGraph`. That gradient is what makes a line red at 250 and amber at 190 and green at 120;
 * the horizontal `inRange` fade this used to paint was green everywhere, so an out-of-range trend
 * looked perfectly healthy while sitting three zones outside the target band.
 *
 * The [Path]s, the stroke and the gradient brush are remembered rather than rebuilt inside the draw
 * lambda, so redrawing costs geometry only.
 */
@Composable
fun WearMiniGraph(
    readings: List<GlucosePoint>,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(72.dp),
    hoursToShow: Int = 2,
    surfaceColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    val clinical = LocalClinicalColors.current
    val series by rememberWearSeries(readings)

    val now = remember(series) { series.lastTime }
    val windowMillis = remember(hoursToShow) { hoursToShow * 3600 * 1000L }
    val windowStart = now - windowMillis

    val fromIndex = remember(series, windowStart) { series.firstIndexAtOrAfter(windowStart) }
    val toIndex = remember(series, now) { series.lastIndexAtOrBefore(now) }

    // Scale to the data instead of a fixed 40..260, which used to flatten anything above 260 against
    // the top edge and made a spike indistinguishable from a plateau.
    val minGl = AXIS_MIN_MGDL
    val maxGl = remember(series, windowStart, now, targetHigh) {
        val highest = if (series.isEmpty || toIndex < fromIndex) 0f
        else series.maxValueBetween(windowStart, now)
        axisCeiling(max(highest, targetHigh))
    }

    val linePath = remember { Path() }
    val areaPath = remember { Path() }
    val brushCache = remember { ZoneBrushCache() }
    val density = LocalDensity.current
    val lineStroke = remember(density) {
        Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val glowRadius = remember(density) { with(density) { 6.dp.toPx() } }
    val ringRadius = remember(density) { with(density) { 3.2.dp.toPx() } }
    val coreRadius = remember(density) { with(density) { 2.2.dp.toPx() } }
    val targetStrokePx = remember(density) { with(density) { 1.2.dp.toPx() } }
    val targetDash = remember(density) {
        PathEffect.dashPathEffect(
            floatArrayOf(with(density) { 5.dp.toPx() }, with(density) { 3.dp.toPx() }),
            0f
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        if (series.isEmpty || toIndex < fromIndex) return@Canvas

        val valueRange = (maxGl - minGl).coerceAtLeast(1f)
        val span = (now - windowStart).coerceAtLeast(1L).toFloat()

        fun yFor(value: Float): Float {
            val clamped = if (value < minGl) minGl else if (value > maxGl) maxGl else value
            return height - ((clamped - minGl) / valueRange) * height
        }

        // 1. Shaded target range band
        val yTargetLow = yFor(targetLow)
        val yTargetHigh = yFor(targetHigh)
        val bandTop = yTargetHigh.coerceIn(0f, height)
        val bandBottom = yTargetLow.coerceIn(0f, height)

        drawRect(
            color = clinical.targetRangeShade,
            topLeft = Offset(0f, bandTop),
            size = Size(width, (bandBottom - bandTop).coerceAtLeast(0f))
        )

        // 2. Target range boundaries, dashed in the in-range token so they read as the target rather
        //    than as grid lines.
        val targetLineColor = clinical.inRange.copy(alpha = 0.65f)
        drawLine(
            color = targetLineColor,
            start = Offset(0f, yTargetHigh),
            end = Offset(width, yTargetHigh),
            strokeWidth = targetStrokePx,
            pathEffect = targetDash
        )
        drawLine(
            color = targetLineColor,
            start = Offset(0f, yTargetLow),
            end = Offset(width, yTargetLow),
            strokeWidth = targetStrokePx,
            pathEffect = targetDash
        )

        // 3. Trend line: midpoint-smoothed spline with the same 25 minute gap break as the full
        //    graph, so a stale stretch shows as a break instead of a straight diagonal across it.
        val times = series.times
        val values = series.values
        val line = linePath.apply { reset() }
        val area = areaPath.apply { reset() }
        var hasSegment = false
        var previousX = 0f
        var previousY = 0f
        var segmentStartX = 0f
        var previousTime = Long.MIN_VALUE
        var lastX = 0f
        var lastY = 0f

        for (i in fromIndex..toIndex) {
            val time = times[i]
            val progress = ((time - windowStart).toFloat() / span).coerceIn(0f, 1f)
            val x = progress * width
            val y = yFor(values[i])
            if (!hasSegment || time - previousTime > GAP_MILLIS) {
                if (hasSegment) closeArea(area, previousX, segmentStartX, height)
                line.moveTo(x, y)
                area.moveTo(x, height)
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
            lastX = x
            lastY = y
        }
        if (hasSegment) closeArea(area, previousX, segmentStartX, height)

        val brush = zoneBrush(brushCache, minGl, maxGl, targetLow, targetHigh, clinical, height)
        if (hasSegment) {
            drawPath(path = area, brush = brush, alpha = AREA_ALPHA)
        }
        drawPath(path = line, brush = brush, style = lineStroke)

        // 4. Highlight the latest reading: glow, surface ring, core, matching the full graph.
        val dotColor = wearStatusColor(series.statusAt(toIndex), clinical)
        val center = Offset(lastX, lastY)
        drawCircle(color = dotColor.copy(alpha = 0.2f), radius = glowRadius, center = center)
        drawCircle(color = surfaceColor, radius = ringRadius, center = center)
        drawCircle(color = dotColor, radius = coreRadius, center = center)
    }
}

private fun closeArea(area: Path, lastX: Float, startX: Float, bottom: Float) {
    area.lineTo(lastX, bottom)
    area.lineTo(startX, bottom)
    area.close()
}

/**
 * Gradient that paints the curve in the colour of the range it passes through, rebuilt only when the
 * axis mapping or the palette changes. Same ten stops and same hard `eps` transitions as
 * [WearGraphChartRenderer] and `GraphScratch.zoneBrushes`, so a value sits in the same colour here
 * as it does on the phone and on the watch's full graph.
 */
private fun DrawScope.zoneBrush(
    cache: ZoneBrushCache,
    minGl: Float,
    maxGl: Float,
    targetLow: Float,
    targetHigh: Float,
    palette: ClinicalColors,
    height: Float
): Brush {
    val cached = cache.brush
    if (cached != null && cache.height == height && cache.min == minGl && cache.max == maxGl &&
        cache.low == targetLow && cache.high == targetHigh && cache.palette === palette
    ) {
        return cached
    }

    val top = 0f
    val bottom = height
    val span = max(1f, bottom - top)
    val eps = 0.0008f

    fun stopAt(value: Float): Float {
        val clamped = if (value < minGl) minGl else if (value > maxGl) maxGl else value
        val y = bottom - ((clamped - minGl) / (maxGl - minGl).coerceAtLeast(1f)) * span
        return ((y - top) / span).coerceIn(0f, 1f)
    }

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
    append(stopAt(targetHigh), palette.high)
    append(stopAt(targetHigh) + eps, palette.inRange)
    append(stopAt(targetLow), palette.inRange)
    append(stopAt(targetLow) + eps, palette.low)
    append(stopAt(54f), palette.low)
    append(stopAt(54f) + eps, palette.veryLow)
    append(1f, palette.veryLow)

    val colorStops = Array(slot) { index -> positions[index] to requireNotNull(stopColors[index]) }
    val brush = Brush.verticalGradient(colorStops = colorStops, startY = top, endY = bottom)
    cache.brush = brush
    cache.height = height
    cache.min = minGl
    cache.max = maxGl
    cache.low = targetLow
    cache.high = targetHigh
    cache.palette = palette
    return brush
}

/** Reuses one brush across frames instead of allocating ten stops and a shader per redraw. */
private class ZoneBrushCache {
    var brush: Brush? = null
    var height = Float.NaN
    var min = Float.NaN
    var max = Float.NaN
    var low = Float.NaN
    var high = Float.NaN
    var palette: ClinicalColors? = null
}

/** Same ladder as the full graph, so a value can never be scaled differently on the two graphs. */
private fun axisCeiling(highest: Float): Float {
    val wanted = highest + 20f
    for (candidate in AXIS_LADDER) {
        if (candidate >= wanted) return candidate
    }
    return AXIS_LADDER[AXIS_LADDER.size - 1]
}

private const val AXIS_MIN_MGDL = 40f
private const val GAP_MILLIS = 25 * 60 * 1000L
private const val AREA_ALPHA = 0.12f
private const val ZONE_STOP_COUNT = 10

private val AXIS_LADDER = floatArrayOf(200f, 240f, 280f, 320f, 360f, 420f, 500f, 600f)
