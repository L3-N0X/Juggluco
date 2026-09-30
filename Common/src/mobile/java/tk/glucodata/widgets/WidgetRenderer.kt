package tk.glucodata.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.format.DateFormat
import androidx.core.graphics.ColorUtils
import tk.glucodata.R
import tk.glucodata.StaleReading
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.TrendArrow
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the widgets.
 *
 * Widgets are drawn rather than inflated because RemoteViews cannot pick fonts, weights, shapes or
 * graphs at runtime, and because the configuration screen can then show the exact same picture
 * the home screen will. Layouts are proportional: every size comes from the space available, so a
 * widget reads well from one cell up to a full screen width.
 */
class WidgetRenderer(private val context: Context) {

    fun render(
        kind: WidgetKind,
        config: WidgetConfig,
        snapshot: WidgetSnapshot,
        widthDp: Float,
        heightDp: Float,
        pxPerDp: Float,
        palette: WidgetPalette = WidgetPalette.resolve(context, config, snapshot.status)
    ): Bitmap {
        val width = (widthDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val height = (heightDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Frame(Canvas(bitmap), width.toFloat(), height.toFloat(), pxPerDp, config, snapshot, palette).draw(kind)
        return bitmap
    }

    /** Spoken description, since the widget itself is a picture. */
    fun describe(snapshot: WidgetSnapshot): String {
        if (!snapshot.hasReading) return context.getString(R.string.novalue)
        val parts = ArrayList<String>()
        parts += "${snapshot.unit.format(snapshot.currentMgDl)} ${context.getString(snapshot.unit.labelRes)}"
        if (snapshot.isStale) {
            parts += context.getString(R.string.nonewvalue).trim() + " " + timeText(snapshot)
            return parts.joinToString(", ")
        }
        TrendArrow.fromRate(snapshot.rate).takeIf { it != TrendArrow.UNKNOWN }?.let { parts += context.getString(it.labelRes) }
        deltaText(snapshot)?.let { parts += it }
        parts += timeText(snapshot)
        return parts.joinToString(", ")
    }

    internal fun timeText(snapshot: WidgetSnapshot): String =
        DateFormat.getTimeFormat(context).format(Date(snapshot.currentTime))

    internal fun deltaText(snapshot: WidgetSnapshot): String? {
        val delta = snapshot.deltaMgDl ?: return null
        if (snapshot.isStale) return null
        val shown = snapshot.unit.toDisplay(delta)
        val digits = if (snapshot.unit == GlucoseUnit.MG_DL) {
            String.format(Locale.getDefault(), "%.0f", abs(shown))
        } else {
            String.format(Locale.getDefault(), "%.1f", abs(shown))
        }
        val sign = when {
            digits.all { it == '0' || it == '.' || it == ',' } -> "±"
            shown > 0f -> "+"
            else -> "−"
        }
        return sign + digits
    }

    /** One drawing pass. Also used by the glucose notification, which lays out the same pieces differently. */
    internal inner class Frame(
        val canvas: Canvas,
        val w: Float,
        val h: Float,
        val px: Float,
        val config: WidgetConfig,
        val snapshot: WidgetSnapshot,
        val palette: WidgetPalette
    ) {
        val hasBackground = android.graphics.Color.alpha(palette.background) > 0
        val status: GlucoseStatus? = snapshot.status
        val stale = snapshot.isStale
        val trend: TrendArrow? = if (!snapshot.hasReading || stale || !config.showArrow) null
        else TrendArrow.fromRate(snapshot.rate).takeIf { it != TrendArrow.UNKNOWN }

        val valueTypeface: Typeface = typeface(config.font, config.font.weight)
        val labelTypeface: Typeface = when (config.font) {
            WidgetFont.MODERN, WidgetFont.LIGHT, WidgetFont.HEAVY -> typeface(WidgetFont.MODERN, 500)
            else -> typeface(config.font, if (config.font == WidgetFont.CONDENSED) 500 else 400)
        }

        val valueText: String = if (snapshot.hasReading) snapshot.unit.format(snapshot.currentMgDl) else "—"
        val unitText: String = context.getString(snapshot.unit.labelRes)

        /** Value in the content color while in range, in the range color when it needs attention. */
        val valueColor: Int = when {
            !snapshot.hasReading -> palette.contentVariant
            stale -> ColorUtils.setAlphaComponent(palette.content, 0x73)
            config.rangeColors && status != null && status != GlucoseStatus.IN_RANGE -> palette.rangeColor(status)
            else -> palette.content
        }
        val arrowColor: Int = if (config.rangeColors && status != null) palette.rangeColor(status) else palette.content
        val chipColor: Int = if (config.rangeColors && status != null && config.background != WidgetBackground.RANGE)
            palette.rangeContainer(status) else palette.chip
        val onChipColor: Int = if (config.rangeColors && status != null && config.background != WidgetBackground.RANGE)
            palette.onRangeContainer(status) else palette.onChip

        fun dp(value: Float) = value * px

        fun draw(kind: WidgetKind) {
            drawBackground()
            when (kind) {
                WidgetKind.MINIMAL -> drawMinimal()
                WidgetKind.COMPACT -> drawCompact()
                WidgetKind.TREND -> drawTrend()
                WidgetKind.DIAL -> drawDial()
                WidgetKind.RANGE -> drawRange()
            }
        }

        // ---- Building blocks -------------------------------------------------------------------

        fun cornerRadius(): Float {
            val radius = when (config.shape) {
                WidgetShape.SYSTEM -> dp(systemRadiusDp())
                WidgetShape.ROUND -> dp(32f)
                WidgetShape.PILL -> min(w, h) / 2f
                WidgetShape.SQUARE -> dp(6f)
            }
            return radius.coerceAtMost(min(w, h) / 2f)
        }

        fun drawBackground() {
            if (!hasBackground) return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background }
            val radius = cornerRadius()
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, paint)
        }

        /** Inner padding: follows the corner so content never crowds a round edge. */
        fun padding(base: Float = 16f): Float {
            if (!hasBackground) return dp(4f)
            val byCorner = cornerRadius() * 0.42f
            return max(min(dp(base), min(w, h) * 0.14f), min(byCorner, min(w, h) * 0.22f))
        }

        fun textPaint(typeface: Typeface, color: Int, size: Float): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.typeface = typeface
                this.color = color
                textSize = size
                if (palette.shadow) setShadowLayer(max(1f, size * 0.07f).coerceAtMost(dp(3f)), 0f, dp(0.6f), 0x66000000)
            }

        fun strokePaint(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
            if (palette.shadow) setShadowLayer(dp(1.5f), 0f, dp(0.5f), 0x4D000000)
        }

        fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        /** Largest text size whose digits are [digitHeight] tall and whose width stays within [maxWidth]. */
        fun fitSize(text: String, typeface: Typeface, digitHeight: Float, maxWidth: Float): Float {
            val ratio = digitRatio(typeface)
            var size = digitHeight / ratio
            val width = measure(text, typeface, size)
            if (width > maxWidth && width > 0f) size *= maxWidth / width
            return size
        }

        fun measure(text: String, typeface: Typeface, size: Float): Float =
            Paint().apply { this.typeface = typeface; textSize = size }.measureText(text)

        fun capHeight(typeface: Typeface, size: Float) = digitRatio(typeface) * size

        /** The value; an old reading is struck through so it never passes for a current one. */
        fun drawValue(x: Float, baseline: Float, paint: Paint, digitHeight: Float) {
            if (stale && snapshot.hasReading) StaleReading.drawStruck(canvas, valueText, x, baseline, paint, digitHeight)
            else canvas.drawText(valueText, x, baseline, paint)
        }

        fun drawArrow(cx: Float, cy: Float, box: Float, arrow: TrendArrow, color: Int) {
            drawTrendArrow(canvas, strokePaint(color, box * 0.14f), cx, cy, box, arrow)
        }

        fun drawChip(cx: Float, cy: Float, diameter: Float, arrow: TrendArrow) {
            canvas.drawCircle(cx, cy, diameter / 2f, fillPaint(chipColor))
            drawArrow(cx, cy, diameter * 0.56f, arrow, onChipColor)
        }

        /** "+3 mg/dL", "+3", "mg/dL" or null, depending on what is switched on. */
        fun deltaLine(): String? {
            val parts = ArrayList<String>(2)
            if (config.showDelta) deltaText(snapshot)?.let { parts += it }
            if (config.showUnit && snapshot.hasReading) parts += unitText
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

        fun timeLine(): String? = when {
            !snapshot.hasReading -> context.getString(R.string.novalue)
            config.showTime || stale -> timeText(snapshot)
            else -> null
        }

        val timeColor: Int get() = if (stale && snapshot.hasReading) palette.warning else palette.contentVariant

        /**
         * Value (with arrow) over one line of details, fitted into [area]. The standalone layout of
         * the minimal widget, and the left half of wide layouts.
         */
        fun drawStack(area: RectF, alignment: WidgetAlignment, maxDetailSize: Float = dp(20f)) {
            val details = listOfNotNull(deltaLine(), timeLine()).joinToString("  ·  ").takeIf { it.isNotEmpty() }
            val ratio = digitRatio(valueTypeface)
            val valueUnitWidth = measure(valueText, valueTypeface, 1f)
            val arrowUnit = if (trend != null) ratio * 1.02f else 0f
            val detailShare = if (details != null) 0.25f else 0f
            val heightUnits = ratio + if (details != null) ratio * 0.3f + detailShare * 0.78f else 0f
            val fit = min(area.height() / heightUnits, area.width() / (valueUnitWidth + arrowUnit))
            var size = min(fit, fit * 0.9f * config.textScale / 100f)
            var detailSize = (size * detailShare).coerceIn(dp(10f), maxDetailSize)
            val detailPaint = details?.let { textPaint(labelTypeface, timeColor, detailSize) }
            if (details != null && detailPaint != null) {
                val detailWidth = detailPaint.measureText(details)
                if (detailWidth > area.width()) {
                    detailSize *= area.width() / detailWidth
                    detailPaint.textSize = detailSize
                }
                val total = ratio * size * 1.3f + capHeight(labelTypeface, detailSize)
                if (total > area.height()) size *= (area.height() - capHeight(labelTypeface, detailSize)) / (ratio * size * 1.3f)
            }
            val digitHeight = ratio * size
            val gap = digitHeight * 0.3f
            val detailCap = if (details != null) capHeight(labelTypeface, detailSize) else 0f
            val blockHeight = digitHeight + if (details != null) gap + detailCap else 0f
            val top = area.centerY() - blockHeight / 2f
            val baseline = top + digitHeight

            val valuePaint = textPaint(valueTypeface, valueColor, size)
            val valueWidth = valuePaint.measureText(valueText)
            val arrowBox = digitHeight * 0.82f
            val arrowGap = digitHeight * 0.2f
            val lineWidth = valueWidth + if (trend != null) arrowGap + arrowBox else 0f
            val lineX = if (alignment == WidgetAlignment.CENTER) area.centerX() - lineWidth / 2f else area.left
            drawValue(lineX, baseline, valuePaint, digitHeight)
            trend?.let { drawArrow(lineX + valueWidth + arrowGap + arrowBox / 2f, baseline - digitHeight / 2f, arrowBox, it, arrowColor) }

            if (details != null && detailPaint != null) {
                val detailWidth = detailPaint.measureText(details)
                val x = if (alignment == WidgetAlignment.CENTER) area.centerX() - detailWidth / 2f else area.left
                drawDetails(details, x, baseline + gap + detailCap, detailPaint)
            }
        }

        /** An old reading has no delta, so the whole line is the time and takes the warning color. */
        fun drawDetails(text: String, x: Float, baseline: Float, paint: Paint) {
            paint.color = timeColor
            canvas.drawText(text, x, baseline, paint)
        }

        /** Value and arrow on the left, delta and time stacked on the right. Headers of the larger widgets. */
        fun drawHeaderRow(area: RectF) {
            val delta = deltaLine()
            val time = timeLine()
            val ratio = digitRatio(valueTypeface)
            val digitHeight = area.height() * 0.78f * config.textScale / 100f
            val detailSize = (area.height() * 0.28f).coerceIn(dp(10f), dp(16f))
            val detailPaint = textPaint(labelTypeface, palette.contentVariant, detailSize)
            val detailWidth = max(delta?.let { detailPaint.measureText(it) } ?: 0f, time?.let { detailPaint.measureText(it) } ?: 0f)
            val arrowBox = if (trend != null) digitHeight * 0.8f else 0f
            val maxValueWidth = (area.width() - detailWidth - arrowBox - dp(16f)).coerceAtLeast(dp(24f))
            val size = fitSize(valueText, valueTypeface, digitHeight.coerceAtMost(area.height() * 0.95f), maxValueWidth)
            val valuePaint = textPaint(valueTypeface, valueColor, size)
            val realDigit = ratio * size
            val baseline = area.centerY() + realDigit / 2f
            drawValue(area.left, baseline, valuePaint, realDigit)
            val valueWidth = valuePaint.measureText(valueText)
            trend?.let {
                val box = realDigit * 0.8f
                drawArrow(area.left + valueWidth + realDigit * 0.2f + box / 2f, baseline - realDigit / 2f, box, it, arrowColor)
            }
            val cap = capHeight(labelTypeface, detailSize)
            val lines = listOfNotNull(delta?.let { it to palette.contentVariant }, time?.let { it to timeColor })
            if (lines.isEmpty()) return
            val lineGap = cap * 0.75f
            val blockHeight = lines.size * cap + (lines.size - 1) * lineGap
            var y = area.centerY() - blockHeight / 2f + cap
            detailPaint.textAlign = Paint.Align.RIGHT
            for ((text, color) in lines) {
                detailPaint.color = color
                canvas.drawText(text, area.right, y, detailPaint)
                y += cap + lineGap
            }
        }

        // ---- Minimal ---------------------------------------------------------------------------

        fun drawMinimal() {
            val pad = padding(14f)
            drawStack(RectF(pad, pad, w - pad, h - pad), config.alignment, maxDetailSize = dp(22f))
        }

        // ---- Compact ---------------------------------------------------------------------------

        fun drawCompact() {
            val padV = (h * 0.16f).coerceIn(dp(6f), dp(18f))
            val padH = if (hasBackground) max(padV, min(cornerRadius() * 0.55f, h * 0.3f)) else dp(4f)
            val inner = h - 2 * padV
            val delta = deltaLine()
            val time = timeLine()
            val lines = listOfNotNull(delta?.let { it to palette.contentVariant }, time?.let { it to timeColor })
            var detailSize = (inner * (if (lines.size > 1) 0.25f else 0.3f)).coerceIn(dp(10f), dp(18f))
            val available = w - 2 * padH
            var digitHeight = inner * 0.6f * config.textScale / 100f
            val ratio = digitRatio(valueTypeface)

            /** The chip belongs to the value, so it is sized from it and not from the full height. */
            fun chipFor(digits: Float) = if (trend != null) min(digits * 1.3f, inner * 0.92f) else 0f

            fun valueWidth(digits: Float) = measure(valueText, valueTypeface, digits / ratio)

            fun detailWidth(details: Float) =
                lines.maxOfOrNull { measure(it.first, labelTypeface, details) } ?: 0f

            fun rowWidth(digits: Float, details: Float, withDetails: Boolean) =
                chipFor(digits) * 1.32f + valueWidth(digits) +
                    (if (withDetails) inner * 0.3f + detailWidth(details) else 0f)

            // Fit the row by scaling it as a whole, so the arrow never costs the value or the
            // details their place; the details go first if that is still not enough.
            var showDetails = lines.isNotEmpty()
            if (rowWidth(digitHeight, detailSize, showDetails) > available) {
                val scale = available / rowWidth(digitHeight, detailSize, showDetails)
                digitHeight *= scale.coerceAtLeast(0.6f)
                detailSize = (detailSize * scale).coerceAtLeast(dp(9f))
                if (rowWidth(digitHeight, detailSize, showDetails) > available) showDetails = false
            }
            if (rowWidth(digitHeight, detailSize, showDetails) > available) {
                digitHeight *= (available / rowWidth(digitHeight, detailSize, showDetails)).coerceAtLeast(0.4f)
            }

            val chip = chipFor(digitHeight)
            val cy = h / 2f
            // Without details to balance, the value and its chip sit in the middle of the pill.
            var x = if (showDetails) padH else (w - rowWidth(digitHeight, detailSize, false)) / 2f
            if (chip > 0f) {
                drawChip(x + chip / 2f, cy, chip, trend!!)
                x += chip * 1.32f
            }
            val valuePaint = textPaint(valueTypeface, valueColor, digitHeight / ratio)
            drawValue(x, cy + digitHeight / 2f, valuePaint, digitHeight)
            if (!showDetails) return
            val detailPaint = textPaint(labelTypeface, palette.contentVariant, detailSize)
            val cap = capHeight(labelTypeface, detailSize)
            val lineGap = cap * 0.7f
            val blockHeight = lines.size * cap + (lines.size - 1) * lineGap
            var y = cy - blockHeight / 2f + cap
            detailPaint.textAlign = Paint.Align.RIGHT
            for ((text, color) in lines) {
                detailPaint.color = color
                canvas.drawText(text, w - padH, y, detailPaint)
                y += cap + lineGap
            }
        }

        // ---- Trend -----------------------------------------------------------------------------

        fun drawTrend() {
            val pad = padding(16f)
            val area = RectF(pad, pad, w - pad, h - pad)
            if (area.height() < dp(84f) && area.width() > area.height() * 2.2f) {
                val split = area.left + area.width() * 0.36f
                drawStack(RectF(area.left, area.top, split, area.bottom), WidgetAlignment.START, maxDetailSize = dp(14f))
                drawGraph(RectF(split + dp(12f), area.top, area.right, area.bottom), config.graphHours)
            } else {
                val headerHeight = (area.height() * 0.3f).coerceIn(dp(28f), dp(60f))
                drawHeaderRow(RectF(area.left, area.top, area.right, area.top + headerHeight))
                drawGraph(RectF(area.left, area.top + headerHeight + dp(10f), area.right, area.bottom), config.graphHours)
            }
        }

        fun drawGraph(area: RectF, hours: Int) {
            if (area.width() < dp(24f) || area.height() < dp(16f)) return
            val end = snapshot.now
            val start = end - hours * 3_600_000L
            val labelSize = dp(10.5f)
            val axisHeight = if (config.showTimeAxis && area.height() > dp(60f)) labelSize + dp(6f) else 0f
            val dot = dp(4f)
            val plot = RectF(area.left, area.top + dot, area.right - dot, area.bottom - axisHeight - dot / 2f)

            var dataMin = Float.MAX_VALUE
            var dataMax = -Float.MAX_VALUE
            var first = -1
            for (i in snapshot.times.indices) {
                if (snapshot.times[i] < start) continue
                if (first < 0) first = i
                dataMin = min(dataMin, snapshot.values[i])
                dataMax = max(dataMax, snapshot.values[i])
            }
            val low = min(snapshot.range.veryLowMgDl - 20f, if (first >= 0) dataMin - 10f else Float.MAX_VALUE).coerceAtLeast(30f)
            val high = max(snapshot.range.veryHighMgDl + 30f, if (first >= 0) dataMax + 10f else 0f).coerceAtMost(430f)
            fun x(time: Long) = plot.left + (time - start).toFloat() / (end - start) * plot.width()
            fun y(value: Float) = plot.bottom - (value - low) / (high - low) * plot.height()

            if (config.showTargetBand) {
                val band = RectF(plot.left, y(snapshot.range.highMgDl), area.right, y(snapshot.range.lowMgDl))
                val bandColor = if (config.rangeColors && config.background != WidgetBackground.RANGE)
                    ColorUtils.setAlphaComponent(palette.rangeColor(GlucoseStatus.IN_RANGE), 0x24)
                else palette.subtle
                canvas.drawRoundRect(band, dp(6f), dp(6f), fillPaint(bandColor))
            }

            if (axisHeight > 0f) drawTimeAxis(plot, area.bottom, start, end, labelSize)

            if (first < 0) return
            val lineWidth = (area.height() * 0.022f).coerceIn(dp(2f), dp(3f))
            val useZones = config.rangeColors && config.background != WidgetBackground.RANGE
            val lineShader: Shader = if (useZones) zoneGradient(plot, low, high, 0xFF) else
                LinearGradient(0f, plot.top, 0f, plot.bottom, palette.accent, palette.accent, Shader.TileMode.CLAMP)

            // One point per couple of pixels is all a line this size can show, and it keeps a day of
            // one-minute readings from turning into a scribble.
            val bucket = dp(1.5f)
            val segments = ArrayList<Path>()
            val areas = ArrayList<Path>()
            var line: Path? = null
            var fill: Path? = null
            var bucketX = Float.NaN
            var bucketSum = 0f
            var bucketCount = 0
            var lastTime = 0L
            var lastX = 0f
            var lastY = 0f
            fun flushBucket() {
                if (bucketCount == 0) return
                val py = y(bucketSum / bucketCount)
                if (line == null) {
                    line = Path().apply { moveTo(bucketX, py) }
                    fill = Path().apply { moveTo(bucketX, plot.bottom); lineTo(bucketX, py) }
                } else {
                    line!!.lineTo(bucketX, py)
                    fill!!.lineTo(bucketX, py)
                }
                lastX = bucketX
                lastY = py
                bucketCount = 0
                bucketSum = 0f
            }
            fun closeSegment() {
                flushBucket()
                line?.let { segments += it }
                fill?.let { it.lineTo(lastX, plot.bottom); it.close(); areas += it }
                line = null
                fill = null
            }
            for (i in first until snapshot.times.size) {
                val time = snapshot.times[i]
                if (lastTime != 0L && time - lastTime > 16 * 60_000L) closeSegment()
                val pointX = x(time)
                if (bucketCount > 0 && pointX - bucketX >= bucket) flushBucket()
                if (bucketCount == 0) bucketX = pointX
                bucketSum += snapshot.values[i]
                bucketCount++
                lastTime = time
            }
            closeSegment()

            val fadeTop = y(dataMax)
            val fade = LinearGradient(0f, fadeTop, 0f, plot.bottom, 0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = ComposeShader(
                    if (useZones) zoneGradient(plot, low, high, 0xFF) else LinearGradient(0f, 0f, 0f, 1f, palette.accent, palette.accent, Shader.TileMode.CLAMP),
                    fade,
                    PorterDuff.Mode.DST_IN
                )
            }
            areas.forEach { canvas.drawPath(it, areaPaint) }
            val linePaint = strokePaint(0xFFFFFFFF.toInt(), lineWidth).apply { shader = lineShader }
            segments.forEach { canvas.drawPath(it, linePaint) }

            val lastValue = snapshot.values.last()
            val dotColor = if (useZones) palette.rangeColor(snapshot.statusOf(lastValue)) else palette.accent
            if (!stale) canvas.drawCircle(lastX, lastY, dot * 1.9f, fillPaint(ColorUtils.setAlphaComponent(dotColor, 0x40)))
            canvas.drawCircle(lastX, lastY, dot, fillPaint(if (stale) ColorUtils.setAlphaComponent(dotColor, 0x80) else dotColor))
        }

        /** Vertical gradient with hard stops at the range limits, so one path is colored by range. */
        fun zoneGradient(plot: RectF, low: Float, high: Float, alpha: Int): LinearGradient {
            fun at(value: Float) = ((high - value) / (high - low)).coerceIn(0f, 1f)
            fun color(status: GlucoseStatus) = ColorUtils.setAlphaComponent(palette.rangeColor(status), alpha)
            val veryHigh = at(snapshot.range.veryHighMgDl)
            val targetHigh = at(snapshot.range.highMgDl).coerceAtLeast(veryHigh)
            val targetLow = at(snapshot.range.lowMgDl).coerceAtLeast(targetHigh)
            val veryLow = at(snapshot.range.veryLowMgDl).coerceAtLeast(targetLow)
            return LinearGradient(
                0f, plot.top, 0f, plot.bottom,
                intArrayOf(
                    color(GlucoseStatus.VERY_HIGH), color(GlucoseStatus.VERY_HIGH),
                    color(GlucoseStatus.HIGH), color(GlucoseStatus.HIGH),
                    color(GlucoseStatus.IN_RANGE), color(GlucoseStatus.IN_RANGE),
                    color(GlucoseStatus.LOW), color(GlucoseStatus.LOW),
                    color(GlucoseStatus.VERY_LOW), color(GlucoseStatus.VERY_LOW)
                ),
                floatArrayOf(0f, veryHigh, veryHigh, targetHigh, targetHigh, targetLow, targetLow, veryLow, veryLow, 1f),
                Shader.TileMode.CLAMP
            )
        }

        fun drawTimeAxis(plot: RectF, bottom: Float, start: Long, end: Long, labelSize: Float) {
            val hours = (end - start) / 3_600_000f
            val step = intArrayOf(1, 2, 3, 4, 6, 12).firstOrNull { plot.width() / (hours / it) >= dp(46f) } ?: 24
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "j")
            val format = java.text.SimpleDateFormat(pattern, Locale.getDefault())
            val paint = textPaint(labelTypeface, palette.contentVariant, labelSize).apply {
                textAlign = Paint.Align.CENTER
                clearShadowLayer()
                if (palette.shadow) setShadowLayer(dp(1.5f), 0f, dp(0.5f), 0x66000000)
            }
            val grid = strokePaint(palette.subtle, dp(1f)).apply { clearShadowLayer(); strokeCap = Paint.Cap.BUTT }
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = start
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.HOUR_OF_DAY, 1)
                while (get(java.util.Calendar.HOUR_OF_DAY) % step != 0) add(java.util.Calendar.HOUR_OF_DAY, 1)
            }
            while (calendar.timeInMillis < end) {
                val x = plot.left + (calendar.timeInMillis - start).toFloat() / (end - start) * plot.width()
                canvas.drawLine(x, plot.top, x, plot.bottom, grid)
                val label = format.format(calendar.time)
                val half = paint.measureText(label) / 2f
                if (x - half >= plot.left && x + half <= plot.right + dp(4f)) canvas.drawText(label, x, bottom - dp(1f), paint)
                calendar.add(java.util.Calendar.HOUR_OF_DAY, step)
            }
        }

        // ---- Dial ------------------------------------------------------------------------------

        fun drawDial() {
            val side = min(w, h)
            val pad = if (hasBackground) side * 0.09f else dp(4f)
            val cx = w / 2f
            val cy = h / 2f
            val stroke = side * 0.075f
            val radius = side / 2f - pad - stroke * 0.95f
            if (radius <= 0f) return
            val scaleLow = 40f
            val scaleHigh = max(300f, snapshot.range.veryHighMgDl + 50f)
            fun angle(value: Float) = 135f + (value.coerceIn(scaleLow, scaleHigh) - scaleLow) / (scaleHigh - scaleLow) * 270f
            val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val degreesPerPx = (180f / Math.PI.toFloat()) / radius
            val gap = stroke * 0.8f * degreesPerPx
            val cap = stroke / 2f * degreesPerPx
            val useZones = config.rangeColors && config.background != WidgetBackground.RANGE
            val zones = listOf(
                Triple(scaleLow, snapshot.range.veryLowMgDl, GlucoseStatus.VERY_LOW),
                Triple(snapshot.range.veryLowMgDl, snapshot.range.lowMgDl, GlucoseStatus.LOW),
                Triple(snapshot.range.lowMgDl, snapshot.range.highMgDl, GlucoseStatus.IN_RANGE),
                Triple(snapshot.range.highMgDl, snapshot.range.veryHighMgDl, GlucoseStatus.HIGH),
                Triple(snapshot.range.veryHighMgDl, scaleHigh, GlucoseStatus.VERY_HIGH)
            ).filter { it.second > it.first }
            val arcPaint = strokePaint(0, stroke).apply { clearShadowLayer() }
            zones.forEachIndexed { index, (from, to, zone) ->
                val a0 = angle(from) + (if (index == 0) 0f else gap / 2f) + cap
                val a1 = angle(to) - (if (index == zones.lastIndex) 0f else gap / 2f) - cap
                arcPaint.color = when {
                    !useZones -> palette.track
                    zone == status && !stale -> ColorUtils.setAlphaComponent(palette.rangeColor(zone), 0xB3)
                    else -> ColorUtils.setAlphaComponent(palette.rangeColor(zone), 0x47)
                }
                if (a1 > a0) canvas.drawArc(oval, a0, a1 - a0, false, arcPaint)
                else {
                    val mid = Math.toRadians(((a0 + a1) / 2f).toDouble())
                    canvas.drawCircle(cx + radius * cos(mid).toFloat(), cy + radius * sin(mid).toFloat(), stroke / 2f, fillPaint(arcPaint.color))
                }
            }
            if (snapshot.hasReading) {
                val knobAngle = Math.toRadians(angle(snapshot.currentMgDl).toDouble())
                val kx = cx + radius * cos(knobAngle).toFloat()
                val ky = cy + radius * sin(knobAngle).toFloat()
                val knobColor = when {
                    useZones && status != null -> palette.rangeColor(status)
                    else -> palette.accent
                }.let { if (stale) ColorUtils.setAlphaComponent(it, 0x80) else it }
                if (hasBackground) {
                    // Cut the arc away around the knob, then refill with the background so a
                    // translucent widget shows the same shade there as everywhere else.
                    val ring = stroke * 0.95f + stroke * 0.28f
                    canvas.drawCircle(kx, ky, ring, fillPaint(0).apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
                    canvas.drawCircle(kx, ky, ring, fillPaint(palette.background))
                }
                canvas.drawCircle(kx, ky, stroke * 0.95f, fillPaint(knobColor).apply {
                    if (palette.shadow) setShadowLayer(dp(2f), 0f, dp(0.75f), 0x59000000)
                })
            }

            // Centre: optional unit, the value, then the arrow with the delta.
            val ratio = digitRatio(valueTypeface)
            val digitHeight = radius * 0.5f * config.textScale / 100f
            val size = fitSize(valueText, valueTypeface, digitHeight, radius * 1.35f)
            val realDigit = ratio * size
            val valuePaint = textPaint(valueTypeface, valueColor, size).apply { textAlign = Paint.Align.CENTER }
            val baseline = cy + realDigit * 0.42f
            drawValue(cx, baseline, valuePaint, realDigit)

            val smallSize = (realDigit * 0.34f / digitRatio(labelTypeface)).coerceIn(dp(9f), dp(18f))
            val smallCap = capHeight(labelTypeface, smallSize)
            if (config.showUnit && snapshot.hasReading) {
                val unitPaint = textPaint(labelTypeface, palette.contentVariant, smallSize).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText(unitText, cx, baseline - realDigit - smallCap * 1.1f, unitPaint)
            }
            val delta = if (config.showDelta) deltaText(snapshot) else null
            val rowBaseline = baseline + smallCap * 1.25f + smallCap
            val deltaPaint = textPaint(labelTypeface, palette.contentVariant, smallSize)
            val deltaWidth = delta?.let { deltaPaint.measureText(it) } ?: 0f
            val arrowBox = if (trend != null) smallCap * 1.6f else 0f
            val rowGap = if (trend != null && delta != null) smallCap * 0.4f else 0f
            var rx = cx - (arrowBox + rowGap + deltaWidth) / 2f
            trend?.let {
                drawArrow(rx + arrowBox / 2f, rowBaseline - smallCap / 2f, arrowBox, it, arrowColor)
                rx += arrowBox + rowGap
            }
            delta?.let { canvas.drawText(it, rx, rowBaseline, deltaPaint) }

            timeLine()?.let { time ->
                val timePaint = textPaint(labelTypeface, timeColor, smallSize * 0.92f).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText(time, cx, cy + radius * 0.93f + capHeight(labelTypeface, timePaint.textSize) / 2f, timePaint)
            }
        }

        // ---- Time in range ---------------------------------------------------------------------

        fun drawRange() {
            val pad = padding(16f)
            val area = RectF(pad, pad, w - pad, h - pad)
            if (area.width() >= area.height() * 1.7f) {
                val split = area.left + area.width() * 0.4f
                drawStack(RectF(area.left, area.top, split - dp(8f), area.bottom), WidgetAlignment.START, maxDetailSize = dp(14f))
                drawTimeInRange(RectF(split + dp(8f), area.top, area.right, area.bottom))
            } else {
                val headerHeight = (area.height() * 0.28f).coerceIn(dp(24f), dp(48f))
                drawHeaderRow(RectF(area.left, area.top, area.right, area.top + headerHeight))
                drawTimeInRange(RectF(area.left, area.top + headerHeight + dp(10f), area.right, area.bottom))
            }
        }

        fun drawTimeInRange(area: RectF) {
            val stats = snapshot.stats(config.statsPeriod)
            val labelSize = (area.height() * 0.13f).coerceIn(dp(10f), dp(14f))
            val labelCap = capHeight(labelTypeface, labelSize)
            val barHeight = (area.height() * 0.1f).coerceIn(dp(6f), dp(12f))
            val gap = labelCap * 0.9f
            val available = (area.height() - 2 * labelCap - barHeight - 3 * gap).coerceAtLeast(dp(12f))
            val percentDigit = (available * config.rangeScale / 100f).coerceIn(dp(12f), available)
            val percentText = stats?.let { "${(it.inRange * 100f).roundToInt()}%" } ?: "—"
            val percentSize = fitSize(percentText, valueTypeface, percentDigit.coerceAtMost(dp(56f)), area.width())
            val realPercent = capHeight(valueTypeface, percentSize)
            val block = labelCap + gap + realPercent + gap + barHeight + gap + labelCap
            var y = area.centerY() - block / 2f

            val title = context.getString(R.string.widget_in_range) + " · " + context.getString(config.statsPeriod.labelRes)
            val labelPaint = textPaint(labelTypeface, palette.contentVariant, labelSize)
            y += labelCap
            canvas.drawText(ellipsize(title, labelPaint, area.width()), area.left, y, labelPaint)
            y += gap + realPercent
            val useZones = config.rangeColors && config.background != WidgetBackground.RANGE
            val percentColor = if (useZones) palette.rangeColor(GlucoseStatus.IN_RANGE) else palette.content
            canvas.drawText(percentText, area.left, y, textPaint(valueTypeface, percentColor, percentSize))
            y += gap
            drawRangeBar(RectF(area.left, y, area.right, y + barHeight), stats, useZones)
            y += barHeight + gap + labelCap
            if (stats != null) {
                val lowShare = ((stats.veryLow + stats.low) * 100f).roundToInt()
                val highShare = ((stats.high + stats.veryHigh) * 100f).roundToInt()
                val legend = "$lowShare%  ·  $highShare%  ·  ${snapshot.unit.format(stats.averageMgDl)}"
                val legendPaint = textPaint(labelTypeface, palette.contentVariant, labelSize).apply {
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(ellipsize(legend, legendPaint, area.width()), area.centerX(), y, legendPaint)
            }
        }

        fun drawRangeBar(bar: RectF, stats: RangeStats?, useZones: Boolean) {
            val radius = bar.height() / 2f
            if (stats == null) {
                canvas.drawRoundRect(bar, radius, radius, fillPaint(palette.track))
                return
            }
            val parts = listOf(
                stats.veryLow to GlucoseStatus.VERY_LOW,
                stats.low to GlucoseStatus.LOW,
                stats.inRange to GlucoseStatus.IN_RANGE,
                stats.high to GlucoseStatus.HIGH,
                stats.veryHigh to GlucoseStatus.VERY_HIGH
            ).filter { it.first > 0.004f }
            val gap = dp(2.5f)
            val minimum = bar.height()
            val usable = bar.width() - gap * (parts.size - 1)
            // Small shares still get a visible dot; the rest is shared out by proportion.
            val reserved = parts.count { it.first * usable < minimum } * minimum
            val proportional = parts.filter { it.first * usable >= minimum }.sumOf { it.first.toDouble() }.toFloat()
            var x = bar.left
            for ((share, zone) in parts) {
                val width = if (share * usable < minimum) minimum
                else (usable - reserved) * share / proportional.coerceAtLeast(0.0001f)
                val color = when {
                    useZones -> palette.rangeColor(zone)
                    zone == GlucoseStatus.IN_RANGE -> palette.accent
                    zone == GlucoseStatus.LOW || zone == GlucoseStatus.HIGH -> ColorUtils.setAlphaComponent(palette.content, 0x66)
                    else -> ColorUtils.setAlphaComponent(palette.content, 0xA6)
                }
                canvas.drawRoundRect(RectF(x, bar.top, x + width, bar.bottom), radius, radius, fillPaint(color))
                x += width + gap
            }
        }

        fun ellipsize(text: String, paint: Paint, width: Float): String {
            if (paint.measureText(text) <= width) return text
            var end = text.length
            while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > width) end--
            return text.substring(0, end).trimEnd() + "…"
        }
    }

    private fun systemRadiusDp(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val resources = context.resources
                return resources.getDimension(android.R.dimen.system_app_widget_background_radius) / resources.displayMetrics.density
            } catch (_: Throwable) {
            }
        }
        return 20f
    }

    companion object {
        private val typefaces = HashMap<String, Typeface>()

        /** The trend arrow of the widgets, centred on [cx]/[cy] within a [box] wide square, stroked with [paint]. */
        fun drawTrendArrow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, box: Float, arrow: TrendArrow) {
            val reach = box * 0.36f
            val head = box * 0.27f
            val path = Path().apply {
                moveTo(cx - reach, cy)
                lineTo(cx + reach, cy)
                moveTo(cx + reach - head, cy - head)
                lineTo(cx + reach, cy)
                lineTo(cx + reach - head, cy + head)
                if (arrow == TrendArrow.RAPIDLY_RISING || arrow == TrendArrow.RAPIDLY_FALLING) {
                    val back = box * 0.24f
                    moveTo(cx + reach - head - back, cy - head)
                    lineTo(cx + reach - back, cy)
                    lineTo(cx + reach - head - back, cy + head)
                }
            }
            canvas.save()
            canvas.rotate(arrow.angleDegrees, cx, cy)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        private val digitRatios = HashMap<Typeface, Float>()

        fun typeface(font: WidgetFont, weight: Int): Typeface = synchronized(typefaces) {
            typefaces.getOrPut("${font.family}:$weight") {
                val base = Typeface.create(font.family, Typeface.NORMAL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(base, weight, false)
                } else when {
                    font.family != "sans-serif" -> Typeface.create(font.family, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
                    weight <= 300 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                    weight >= 800 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
                    weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    else -> base
                }
            }
        }

        /** Height of the digits relative to the text size, which differs quite a bit between fonts. */
        fun digitRatio(typeface: Typeface): Float = synchronized(digitRatios) {
            digitRatios.getOrPut(typeface) {
                val bounds = Rect()
                Paint().apply { this.typeface = typeface; textSize = 100f }.getTextBounds("0123456789", 0, 10, bounds)
                (bounds.height() / 100f).takeIf { it > 0.3f } ?: 0.72f
            }
        }
    }
}
