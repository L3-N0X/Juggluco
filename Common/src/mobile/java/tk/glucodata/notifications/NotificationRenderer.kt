package tk.glucodata.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import tk.glucodata.R
import tk.glucodata.widgets.WidgetPalette
import tk.glucodata.widgets.WidgetRenderer
import tk.glucodata.widgets.WidgetSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the glucose notification with the widgets' building blocks, so value, arrow and graph look
 * exactly like they do on the home screen.
 *
 * The collapsed view is one row: the value large, the arrow right after it, then the change (with
 * a Δ) over the time of the reading, and optionally a small graph filling the rest. The expanded
 * view repeats that row, so expanding reads as the graph sliding in underneath it.
 */
class NotificationRenderer(private val context: Context) {
    private val widgets = WidgetRenderer(context)

    fun describe(snapshot: WidgetSnapshot): String = widgets.describe(snapshot)

    fun collapsed(
        config: NotificationConfig,
        snapshot: WidgetSnapshot,
        widthDp: Float,
        pxPerDp: Float,
        palette: WidgetPalette = WidgetPalette.forNotification(context)
    ): Bitmap {
        val (bitmap, frame) = frame(config, snapshot, widthDp, ROW_HEIGHT_DP, pxPerDp, palette)
        drawRow(frame, config, RectF(0f, 0f, frame.w, frame.h), config.sparkline)
        return bitmap
    }

    /** Null when the expanded view would add nothing. */
    fun expanded(
        config: NotificationConfig,
        snapshot: WidgetSnapshot,
        widthDp: Float,
        pxPerDp: Float,
        palette: WidgetPalette = WidgetPalette.forNotification(context)
    ): Bitmap? {
        if (!config.hasExpandedView) return null
        val graphDp = if (config.expandedGraph) config.graphHeight.dp else 0f
        val statsDp = if (config.expandedStats) STATS_HEIGHT_DP else 0f
        var heightDp = ROW_HEIGHT_DP + BOTTOM_DP
        if (graphDp > 0f) heightDp += GAP_DP + graphDp
        if (statsDp > 0f) heightDp += GAP_DP + statsDp
        val (bitmap, frame) = frame(config, snapshot, widthDp, heightDp, pxPerDp, palette)
        var y = frame.dp(ROW_HEIGHT_DP)
        drawRow(frame, config, RectF(0f, 0f, frame.w, y), false)
        if (graphDp > 0f) {
            y += frame.dp(GAP_DP)
            // Room on the right for the halo around the latest reading.
            frame.drawGraph(RectF(0f, y, frame.w - frame.dp(GRAPH_END_DP), y + frame.dp(graphDp)), config.graphHours)
            y += frame.dp(graphDp)
        }
        if (statsDp > 0f) {
            y += frame.dp(GAP_DP)
            drawStats(frame, config, RectF(0f, y, frame.w, y + frame.dp(statsDp)))
        }
        return bitmap
    }

    /** The graph alone, square, as the large icon of the Live Update. */
    fun graphIcon(
        config: NotificationConfig,
        snapshot: WidgetSnapshot,
        sizeDp: Float,
        pxPerDp: Float,
        palette: WidgetPalette = WidgetPalette.forNotification(context)
    ): Bitmap {
        val (bitmap, frame) = frame(config, snapshot, sizeDp, sizeDp, pxPerDp, palette)
        val inset = frame.dp(GRAPH_END_DP)
        frame.drawGraph(RectF(0f, inset, frame.w - inset, frame.h - inset), config.sparklineHours)
        return bitmap
    }

    private fun frame(
        config: NotificationConfig,
        snapshot: WidgetSnapshot,
        widthDp: Float,
        heightDp: Float,
        pxPerDp: Float,
        palette: WidgetPalette
    ): Pair<Bitmap, WidgetRenderer.Frame> {
        val width = (widthDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val height = (heightDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val frame = widgets.Frame(Canvas(bitmap), width.toFloat(), height.toFloat(), pxPerDp, config.widgetConfig(), snapshot, palette)
        return bitmap to frame
    }

    private fun drawRow(frame: WidgetRenderer.Frame, config: NotificationConfig, area: RectF, sparkline: Boolean) = with(frame) {
        val ratio = WidgetRenderer.digitRatio(valueTypeface)
        val labelRatio = WidgetRenderer.digitRatio(labelTypeface)
        val delta = if (config.showDelta) widgets.deltaText(snapshot) else null
        val unit = if (config.showUnit && snapshot.hasReading) unitText else null
        val first = listOfNotNull(delta, unit).joinToString(" ").takeIf { it.isNotEmpty() }
        val time = timeLine()
        val inRange = if (config.showTimeInRange && snapshot.hasReading) snapshot.stats(config.statsPeriod)?.let {
            context.getString(R.string.widget_in_range) + " " + (it.inRange * 100f).roundToInt() + "%"
        } else null
        val second = listOfNotNull(time, inRange).joinToString(SEPARATOR).takeIf { it.isNotEmpty() }
        val lines = listOfNotNull(first, second)

        var digit = (area.height() * 0.6f * config.textScale / 100f).coerceAtMost(area.height() * 0.86f)
        fun firstCap() = digit * if (lines.size > 1) 0.36f else 0.4f
        fun secondCap() = digit * if (lines.size > 1) 0.3f else 0.36f
        fun widthOf(line: String?, cap: Float, prefix: String = "") =
            if (line == null) 0f else measure(prefix + line, labelTypeface, cap / labelRatio)
        val deltaPrefix = if (delta != null) "$DELTA " else ""
        fun detailsWidth(): Float = when {
            lines.isEmpty() -> 0f
            lines.size == 1 && first != null -> widthOf(first, firstCap(), deltaPrefix)
            lines.size == 1 -> widthOf(second, secondCap())
            else -> max(widthOf(first, firstCap(), deltaPrefix), widthOf(second, secondCap()))
        }
        fun valueWidth() = measure(valueText, valueTypeface, digit / ratio)
        fun arrowWidth() = if (trend != null) digit * 0.18f + digit * 0.8f else 0f
        fun detailsGap() = if (lines.isNotEmpty()) digit * 0.5f else 0f
        fun needed() = valueWidth() + arrowWidth() + detailsGap() + detailsWidth()

        val minSpark = dp(56f)
        var withSpark = sparkline && snapshot.hasReading
        if (withSpark && needed() + dp(16f) + minSpark + dp(GRAPH_END_DP) > area.width()) withSpark = false
        if (needed() > area.width()) digit *= area.width() / needed()

        val baseline = area.centerY() + digit / 2f
        var x = area.left
        canvas.drawText(valueText, x, baseline, textPaint(valueTypeface, valueColor, digit / ratio))
        x += valueWidth()
        trend?.let {
            val box = digit * 0.8f
            drawArrow(x + digit * 0.18f + box / 2f, baseline - digit / 2f, box, it, arrowColor)
        }
        x += arrowWidth() + detailsGap()

        if (lines.isNotEmpty()) {
            val cap1 = if (first != null) firstCap() else 0f
            val cap2 = if (second != null) secondCap() else 0f
            val lineGap = if (lines.size > 1) digit * 0.2f else 0f
            val top = area.centerY() - (cap1 + lineGap + cap2) / 2f
            if (first != null) {
                val paint = textPaint(labelTypeface, palette.content, cap1 / labelRatio)
                var lx = x
                if (delta != null) {
                    paint.color = palette.contentVariant
                    canvas.drawText(deltaPrefix, lx, top + cap1, paint)
                    lx += paint.measureText(deltaPrefix)
                    paint.color = if (snapshot.isStale) palette.contentVariant else palette.content
                } else {
                    paint.color = palette.contentVariant
                }
                canvas.drawText(first, lx, top + cap1, paint)
            }
            if (second != null) {
                val paint = textPaint(labelTypeface, palette.contentVariant, cap2 / labelRatio)
                val baseline2 = top + cap1 + lineGap + cap2
                if (time != null) {
                    paint.color = timeColor
                    canvas.drawText(time, x, baseline2, paint)
                }
                if (inRange != null) {
                    val rest = if (time != null) SEPARATOR + inRange else inRange
                    paint.color = palette.contentVariant
                    canvas.drawText(rest, x + if (time != null) paint.measureText(time) else 0f, baseline2, paint)
                }
            }
            x += detailsWidth()
        }

        if (withSpark) {
            val left = x + dp(16f)
            val inset = area.height() * 0.12f
            if (area.right - dp(GRAPH_END_DP) - left >= minSpark) {
                drawGraph(RectF(left, area.top + inset, area.right - dp(GRAPH_END_DP), area.bottom - inset), config.sparklineHours)
            }
        }
    }

    /** Range bar over one line of shares, for the chosen period. */
    private fun drawStats(frame: WidgetRenderer.Frame, config: NotificationConfig, area: RectF) = with(frame) {
        val stats = snapshot.stats(config.statsPeriod)
        val barHeight = dp(8f)
        drawRangeBar(RectF(area.left, area.top, area.right, area.top + barHeight), stats, config.rangeColors)
        val size = dp(12.5f)
        val cap = capHeight(labelTypeface, size)
        val paint = textPaint(labelTypeface, palette.contentVariant, size)
        val period = context.getString(config.statsPeriod.labelRes)
        val text = if (stats == null) period + SEPARATOR + context.getString(R.string.novalue) else listOf(
            period,
            context.getString(R.string.widget_in_range) + " " + (stats.inRange * 100f).roundToInt() + "%",
            context.getString(R.string.widget_stat_low, "${((stats.veryLow + stats.low) * 100f).roundToInt()}%"),
            context.getString(R.string.widget_stat_high, "${((stats.high + stats.veryHigh) * 100f).roundToInt()}%"),
            context.getString(R.string.widget_stat_average, snapshot.unit.format(stats.averageMgDl))
        ).joinToString(SEPARATOR)
        canvas.drawText(ellipsize(text, paint, area.width()), area.left, area.top + barHeight + dp(7f) + cap, paint)
    }

    companion object {
        const val ROW_HEIGHT_DP = 48f
        private const val GAP_DP = 10f
        private const val BOTTOM_DP = 4f
        private const val STATS_HEIGHT_DP = 26f
        private const val GRAPH_END_DP = 4f
        private const val SEPARATOR = "  ·  "
        private const val DELTA = "Δ"

        /** Bitmaps are drawn at most this dense, which keeps a full width graph a reasonable size to hand to the system. */
        private const val MAX_PX_PER_DP = 2.75f

        fun pxPerDp(context: Context): Float = min(context.resources.displayMetrics.density, MAX_PX_PER_DP)

        private fun screenWidthDp(context: Context): Float {
            val metrics = context.resources.displayMetrics
            return min(metrics.widthPixels, metrics.heightPixels) / metrics.density
        }

        /**
         * Room for the collapsed content: the notification's width less its margins, the icon on
         * the left and, from Android 12, the expand button on the right. The picture keeps its
         * proportions, so a guess that is a little off only leaves space at the end.
         */
        fun collapsedWidthDp(context: Context): Float {
            val chrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 132f else 56f
            return (screenWidthDp(context) - chrome).coerceIn(180f, 520f)
        }

        fun expandedWidthDp(context: Context): Float {
            val chrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 88f else 56f
            return (screenWidthDp(context) - chrome).coerceIn(200f, 560f)
        }
    }
}
