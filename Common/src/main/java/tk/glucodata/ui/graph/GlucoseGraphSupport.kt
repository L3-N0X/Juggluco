package tk.glucodata.ui.graph

import android.content.Context
import android.graphics.Paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseRange
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.theme.ClinicalColors
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal val HISTORY_COLOR = Color(0xFF818CF8)
internal val CALIBRATED_COLOR = Color(0xFF06B6D4)
internal val SCAN_COLOR = Color(0xFFF43F5E)
internal const val AXIS_MIN = 40f

/** Reusable native paints; text sizes resolved once per density/colour change. */
internal class GraphPaints(density: Density, textColor: Color, clinicalColors: ClinicalColors) {
    private val axisTextSize = with(density) { 10.5.sp.toPx() }
    private val timeTextSize = with(density) { 10.sp.toPx() }
    private val eventTextSize = with(density) { 9.sp.toPx() }

    val axis = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
    }
    val target = Paint().apply {
        isAntiAlias = true
        color = clinicalColors.inRange.toArgb()
        textSize = axisTextSize
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    val time = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = timeTextSize
        textAlign = Paint.Align.CENTER
    }
    val dayLabel = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = timeTextSize
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val event = Paint().apply {
        isAntiAlias = true
        textSize = eventTextSize
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** Vertical offset that centres axis text on its grid line. */
    val axisBaselineOffset = axisTextSize * 0.36f
    val timeLabelOffset = with(density) { 16.dp.toPx() }

    private val locale = Locale.getDefault()
    val timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
    val dayFormat: SimpleDateFormat = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
}

internal class ZoneBrushes(val line: Brush, val area: Brush)

/**
 * How far a non-primary sensor's curve is darkened. Dark enough to read as "not the current
 * sensor" at a glance, light enough that its range colouring is still recognisable.
 */
private const val SENSOR_DIM_FACTOR = 0.45f

/** Darkens [this] towards black; used to push an older sensor behind the primary one. */
private fun Color.dimmed(dimmed: Boolean): Color =
    if (!dimmed) this else Color(red * SENSOR_DIM_FACTOR, green * SENSOR_DIM_FACTOR, blue * SENSOR_DIM_FACTOR, alpha)

/**
 * Per-frame scratch space: paths, envelope column buffers and the cached value-zone gradients.
 * Nothing here is allocated while panning unless the chart geometry or palette actually changed.
 */
internal class GraphScratch {
    val linePath = Path()
    val areaPath = Path()
    val dashedPath = Path()
    val markerPath = Path()

    var columnMin = FloatArray(0)
        private set
    var columnMax = FloatArray(0)
        private set
    var columnSum = FloatArray(0)
        private set
    var columnCount = IntArray(0)
        private set

    /** True per column that saw at least one reading of the primary sensor, so runs can be dimmed. */
    var columnPrimary = BooleanArray(0)
        private set

    fun prepareColumns(count: Int) {
        if (columnMin.size < count) {
            columnMin = FloatArray(count)
            columnMax = FloatArray(count)
            columnSum = FloatArray(count)
            columnCount = IntArray(count)
            columnPrimary = BooleanArray(count)
        }
        java.util.Arrays.fill(columnCount, 0, count, 0)
        java.util.Arrays.fill(columnPrimary, 0, count, false)
    }

    private var brushes: ZoneBrushes? = null
    private var dimBrushes: ZoneBrushes? = null
    private var brushTop = Float.NaN
    private var brushBottom = Float.NaN
    private var brushMin = Float.NaN
    private var brushMax = Float.NaN
    private var brushRange: GlucoseRange? = null
    private var brushPalette: ClinicalColors? = null
    private var brushDimmed = false

    /**
     * Gradient that paints the curve in the colour of the range it passes through, built from the
     * current axis mapping. Rebuilt only when the mapping or the palette changes.
     *
     * [dimmed] darkens every stop, which is how an older sensor running next to the primary one
     * is told apart without introducing a second palette.
     */
    fun zoneBrushes(
        chart: ChartTransform,
        colors: ClinicalColors,
        range: GlucoseRange,
        dimmed: Boolean = false
    ): ZoneBrushes {
        val top = chart.metrics.chartTop
        val bottom = chart.metrics.chartBottom
        val cached = if (dimmed) dimBrushes else brushes
        if (cached != null && brushTop == top && brushBottom == bottom &&
            brushMin == chart.minValue && brushMax == chart.maxValue &&
            brushRange == range && brushPalette === colors && brushDimmed == dimmed
        ) {
            return cached
        }

        fun stopAt(value: Float): Float =
            ((chart.y(value) - top) / (bottom - top).coerceAtLeast(1f)).coerceIn(0f, 1f)

        val eps = 0.0008f
        val raw = ArrayList<Pair<Float, Color>>(10)
        raw.add(0f to colors.veryHigh.dimmed(dimmed))
        raw.add(stopAt(range.veryHighMgDl) to colors.veryHigh.dimmed(dimmed))
        raw.add((stopAt(range.veryHighMgDl) + eps) to colors.high.dimmed(dimmed))
        raw.add(stopAt(range.highMgDl) to colors.high.dimmed(dimmed))
        raw.add((stopAt(range.highMgDl) + eps) to colors.inRange.dimmed(dimmed))
        raw.add(stopAt(range.lowMgDl) to colors.inRange.dimmed(dimmed))
        raw.add((stopAt(range.lowMgDl) + eps) to colors.low.dimmed(dimmed))
        raw.add(stopAt(range.veryLowMgDl) to colors.low.dimmed(dimmed))
        raw.add((stopAt(range.veryLowMgDl) + eps) to colors.veryLow.dimmed(dimmed))
        raw.add(1f to colors.veryLow.dimmed(dimmed))

        var previous = 0f
        val stops = Array(raw.size) { index ->
            val (position, color) = raw[index]
            val monotonic = max(previous, min(1f, position))
            previous = monotonic
            monotonic to color
        }
        val areaStops = Array(stops.size) { index ->
            val (position, color) = stops[index]
            position to color.copy(alpha = 0.16f)
        }

        val result = ZoneBrushes(
            line = Brush.verticalGradient(colorStops = stops, startY = top, endY = bottom),
            area = Brush.verticalGradient(colorStops = areaStops, startY = top, endY = bottom)
        )
        if (dimmed) dimBrushes = result else brushes = result
        brushTop = top
        brushBottom = bottom
        brushMin = chart.minValue
        brushMax = chart.maxValue
        brushRange = range
        brushPalette = colors
        brushDimmed = dimmed
        return result
    }

    companion object {
        const val MAX_COLUMNS = 2048
    }
}

internal fun statusColor(ordinal: Int, colors: ClinicalColors): Color = when (ordinal) {
    0 -> colors.veryLow
    1 -> colors.low
    2 -> colors.inRange
    3 -> colors.high
    else -> colors.veryHigh
}

internal fun eventLabel(context: Context, record: LogRecord, unit: GlucoseUnit): String = when (record.type) {
    LogType.RAPID_INSULIN ->
        context.getString(R.string.value_with_unit, "${record.value.roundToInt()}", context.getString(R.string.unit_insulin_short))
    LogType.BASAL_INSULIN ->
        context.getString(R.string.value_with_unit, "${record.value.roundToInt()}", context.getString(R.string.unit_basal_short))
    LogType.CARBS, LogType.MEAL ->
        context.getString(R.string.value_with_unit, "${record.value.roundToInt()}", context.getString(R.string.unit_carbs_short))
    LogType.BLOOD_GLUCOSE -> unit.format(record.value)
    LogType.CUSTOM -> if (record.value != 0f) LogRecord.formatCustomAmount(record.value) else "•"
}

/** Coarse ladder for the top of the value axis, so the scale stays put while panning. */
internal fun axisCeiling(highest: Float): Float {
    val ladder = floatArrayOf(200f, 240f, 280f, 320f, 360f, 400f, 450f, 500f, 600f)
    val wanted = highest + 20f
    for (step in ladder) if (wanted <= step) return step
    return ladder.last()
}
