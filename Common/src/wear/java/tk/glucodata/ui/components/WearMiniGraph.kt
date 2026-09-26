package tk.glucodata.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.theme.LocalClinicalColors

/**
 * Sparkline for the home screen.
 *
 * Backed by the same prepared [WearSeries] as the full graph, which fixes the other half of the
 * duplicate-reading problem: this used to plot the raw *and* the calibrated value for every
 * timestamp, so the sparkline was a picket fence rather than a trend line.
 *
 * The [Path] and the stroke brush are remembered rather than built inside the draw lambda, so
 * redrawing costs geometry only.
 */
@Composable
fun WearMiniGraph(
    readings: List<GlucosePoint>,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(72.dp),
    hoursToShow: Int = 2
) {
    val clinical = LocalClinicalColors.current
    val series by rememberWearSeries(readings)

    val now = remember(series) { series.lastTime }
    val windowMillis = remember(hoursToShow) { hoursToShow * 3600 * 1000L }
    val windowStart = now - windowMillis

    val fromIndex = remember(series, windowStart) { series.firstIndexAtOrAfter(windowStart) }
    val toIndex = remember(series, now) { series.lastIndexAtOrBefore(now) }

    val path = remember { Path() }
    val density = LocalDensity.current
    val lineStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round)
    }
    val glowRadius = remember(density) { with(density) { 6.dp.toPx() } }
    val coreRadius = remember(density) { with(density) { 3.5.dp.toPx() } }
    val lineBrush = remember(clinical) {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to clinical.inRange.copy(alpha = 0.5f),
                1f to clinical.inRange
            )
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        if (series.isEmpty || toIndex < fromIndex) return@Canvas

        val minGl = 40f
        val maxGl = 260f
        val rangeGl = maxGl - minGl
        val span = (now - windowStart).coerceAtLeast(1L).toFloat()

        // 1. Shaded target range band
        val yTargetLow = height - ((targetLow.coerceIn(minGl, maxGl) - minGl) / rangeGl) * height
        val yTargetHigh = height - ((targetHigh.coerceIn(minGl, maxGl) - minGl) / rangeGl) * height
        val bandTop = if (yTargetHigh < 0f) 0f else yTargetHigh
        val bandBottom = if (yTargetLow > height) height else yTargetLow

        drawRect(
            color = clinical.targetRangeShade,
            topLeft = Offset(0f, bandTop),
            size = Size(width, (bandBottom - bandTop).coerceAtLeast(0f))
        )

        // 2. Target guideline boundaries
        drawLine(
            color = clinical.graphGrid,
            start = Offset(0f, yTargetHigh),
            end = Offset(width, yTargetHigh),
            strokeWidth = 1f
        )
        drawLine(
            color = clinical.graphGrid,
            start = Offset(0f, yTargetLow),
            end = Offset(width, yTargetLow),
            strokeWidth = 1f
        )

        // 3. Trend line
        val times = series.times
        val values = series.values
        path.reset()
        var lastX = 0f
        var lastY = 0f
        for (i in fromIndex..toIndex) {
            val progress = ((times[i] - windowStart).toFloat() / span).coerceIn(0f, 1f)
            val x = progress * width
            val value = values[i].coerceIn(minGl, maxGl)
            val y = height - ((value - minGl) / rangeGl) * height
            if (i == fromIndex) path.moveTo(x, y) else path.lineTo(x, y)
            lastX = x
            lastY = y
        }
        drawPath(path = path, brush = lineBrush, style = lineStroke)

        // 4. Highlight the latest reading
        val dotColor = when (series.statusAt(toIndex)) {
            GlucoseStatus.IN_RANGE -> clinical.inRange
            GlucoseStatus.LOW, GlucoseStatus.HIGH -> clinical.low
            GlucoseStatus.VERY_LOW, GlucoseStatus.VERY_HIGH -> clinical.veryLow
        }
        val center = Offset(lastX, lastY)
        drawCircle(color = dotColor.copy(alpha = 0.35f), radius = glowRadius, center = center)
        drawCircle(color = dotColor, radius = coreRadius, center = center)
    }
}
