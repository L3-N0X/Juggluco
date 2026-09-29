package tk.glucodata.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.widgets.WidgetFont
import tk.glucodata.widgets.WidgetRenderer
import kotlin.math.min
import kotlin.math.roundToInt

/** What the status bar icons are drawn from. */
class StatusIconInput(
    /** Value as the icon shows it, e.g. "128", "5.4" or "40>". */
    val valueText: String?,
    val arrow: TrendArrow?,
    /** Signed change, e.g. "+3", or null when there is none. */
    val deltaText: String?,
    val stale: Boolean
)

/**
 * Draws the status bar icons.
 *
 * The status bar only uses an icon's alpha, so everything is drawn in white. Text is sized by the
 * height of its digits and centred on their actual outline rather than on the font's ascent and
 * descent, so "95", "128" and "5.4" all sit in the middle of the icon. Digits take at most
 * [DIGIT_SHARE] of the icon's height and [TEXT_WIDTH] of its width: large enough to read at a
 * glance, small enough to sit in line with the system's own icons.
 */
object StatusIconRenderer {
    private const val DIGIT_SHARE = 0.62f
    private const val TEXT_WIDTH = 0.9f

    /** Bitmap size close to the status bar's own, so the icon is drawn crisp. */
    fun sizePx(context: Context): Int =
        (24f * context.resources.displayMetrics.density).roundToInt().coerceIn(48, 144)

    /** Null for [StatusIconKind.APP], which uses the app's drawable instead. */
    fun render(kind: StatusIconKind, input: StatusIconInput, config: NotificationConfig, size: Int): Bitmap? {
        if (kind == StatusIconKind.APP) return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()
        val scale = config.iconScale / 100f
        val alpha = if (input.stale) 0x8C else 0xFF
        val typeface = WidgetRenderer.typeface(WidgetFont.MODERN, config.iconWeight.weight)
        val box = RectF(0f, 0f, s, s)
        when (kind) {
            StatusIconKind.VALUE -> drawText(canvas, input.valueText ?: DASH, typeface, box, s * DIGIT_SHARE * scale, s * TEXT_WIDTH * scale, alpha)
            StatusIconKind.DELTA -> drawText(canvas, input.deltaText ?: DASH, typeface, box, s * DIGIT_SHARE * scale, s * TEXT_WIDTH * scale, alpha)
            StatusIconKind.ARROW -> {
                val arrow = input.arrow
                if (arrow == null) drawText(canvas, DASH, typeface, box, s * DIGIT_SHARE, s, alpha)
                else drawArrow(canvas, s / 2f, s / 2f, s * min(1.05f, scale), arrow, config.iconWeight, alpha)
            }
            StatusIconKind.VALUE_ARROW -> {
                // Arrow over the value, the pair centred as one block.
                val digit = s * 0.4f * min(scale, 1.15f)
                val arrowBox = s * 0.46f * min(scale, 1.1f)
                val gap = s * 0.04f
                val top = (s - arrowBox - gap - digit) / 2f
                val arrow = input.arrow
                if (arrow != null) drawArrow(canvas, s / 2f, top + arrowBox / 2f, arrowBox, arrow, config.iconWeight, alpha)
                val valueBox = RectF(0f, top + arrowBox + gap, s, top + arrowBox + gap + digit)
                drawText(canvas, input.valueText ?: DASH, typeface, if (arrow != null) valueBox else box, digit, s, alpha)
            }
            StatusIconKind.APP -> Unit
        }
        return bitmap
    }

    private const val DASH = "–"

    /** Text whose digits are [digitHeight] tall and at most [maxWidth] wide, centred in [box]. */
    private fun drawText(canvas: Canvas, text: String, typeface: android.graphics.Typeface, box: RectF, digitHeight: Float, maxWidth: Float, alpha: Int) {
        val ratio = WidgetRenderer.digitRatio(typeface)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            color = Color.argb(alpha, 255, 255, 255)
            textSize = digitHeight / ratio
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val width = min(maxWidth, box.width())
        if (bounds.width() > width) {
            paint.textSize *= width / bounds.width()
            paint.getTextBounds(text, 0, text.length, bounds)
        }
        val digit = ratio * paint.textSize
        canvas.drawText(text, box.centerX() - bounds.width() / 2f - bounds.left, box.centerY() + digit / 2f, paint)
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, box: Float, arrow: TrendArrow, weight: StatusIconWeight, alpha: Int) {
        val stroke = box * when (weight) {
            StatusIconWeight.REGULAR -> 0.12f
            StatusIconWeight.BOLD -> 0.15f
            StatusIconWeight.HEAVY -> 0.185f
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(alpha, 255, 255, 255)
        }
        // The stroke's round caps reach past the path; keep them inside the icon.
        val inner = box - stroke
        WidgetRenderer.drawTrendArrow(canvas, paint, cx, cy, inner, arrow)
    }
}
