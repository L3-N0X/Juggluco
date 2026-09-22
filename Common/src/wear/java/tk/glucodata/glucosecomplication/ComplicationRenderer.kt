/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Modern Material 3 Complication Renderer for Wear OS                          */

package tk.glucodata.glucosecomplication

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.Notify
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.TrendArrow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Layout styles for glucose complications on Wear OS.
 */
enum class ComplicationLayout {
    ARROW_TOP,      // Small trend arrow above glucose value
    ARROW_BOTTOM,   // Glucose value above small trend arrow
    ARROW_BESIDE,   // Glucose value and trend arrow side-by-side
    VALUE_ONLY,     // Clean centered glucose value
    ARROW_ONLY      // Clean centered trend arrow
}

/**
 * Color appearance styles:
 * - SIGNAL: Uses Material 3 clinical target range tokens (sage green, amber, coral red).
 * - MONOCHROME: Crisp white/tintable monochrome respecting watch face themes.
 */
enum class ComplicationColorStyle {
    SIGNAL,
    MONOCHROME
}

/**
 * Snapshot of glucose data needed for complication rendering.
 */
data class ComplicationGlucose(
    val value: String,
    val isOld: Boolean,
    val rate: Float,
    val status: GlucoseStatus,
    val time: Long,
    val isMmol: Boolean
)

object ComplicationRenderer {
    /**
     * Bitmap canvas edge length in pixels.
     *
     * Best practice: keep custom-drawn SMALL_IMAGE / MONOCHROMATIC_IMAGE rasters
     * small (well below full-screen sizes). 192px is crisp on all densities
     * (~64dp @ 3x) while using ~40% less memory than 256px.
     */
    const val CANVAS_SIZE = 192
    private const val CX = CANVAS_SIZE / 2f
    private const val CY = CANVAS_SIZE / 2f

    /** Icon bitmap edge length for SHORT_TEXT icons (matches ~32dp @ 3x). */
    const val ICON_SIZE = 96

    // Material 3 Dark Clinical Tokens (from tk.glucodata.ui.theme.DarkClinicalColors)
    const val COLOR_IN_RANGE = 0xFF7CB69D.toInt()        // Soft sage green, never neon
    const val COLOR_WARNING = 0xFFF59E0B.toInt()         // Radiant amber (Low & High)
    const val COLOR_CRITICAL = 0xFFF87171.toInt()        // Crisp coral red (Very Low & Very High)
    const val COLOR_WHITE = 0xFFFFFFFF.toInt()           // Pure white for active monochrome
    const val COLOR_TEXT_SECONDARY = 0xFFDFE3E6.toInt()  // OnSurfaceDark
    const val COLOR_TEXT_MUTED = 0xFF8A9297.toInt()      // Muted gray for stale/no readings

    // Scale factor relative to CANVAS_SIZE=256 baseline the geometry below was tuned on.
    private const val S = CANVAS_SIZE / 256f

    /**
     * System default sans-serif with explicit weights keeps numbers metrically
     * stable across devices. Bold (700) for the value, medium (500) for time.
     * Never use condensed/italic/decorative typefaces here: they distort
     * digit widths at small complication sizes.
     */
    private val numberTypeface: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, 700, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val secondaryTypeface: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, 500, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    /**
     * Retrieves the current glucose state from native storage.
     */
    fun getLatestGlucose(): ComplicationGlucose {
        val strGl = Natives.lastglucose()
        val now = System.currentTimeMillis()
        val isMmol = Applic.unit == 1
        val defaultVal = if (isMmol) "5.6" else "108"

        if (strGl == null || strGl.time <= 0) {
            return ComplicationGlucose(
                value = defaultVal,
                isOld = true,
                rate = Float.NaN,
                status = GlucoseStatus.IN_RANGE,
                time = now,
                isMmol = isMmol
            )
        }

        val timeMs = strGl.time * 1000L
        val isOld = (now - timeMs) >= Notify.glucosetimeout
        val rawVal = try {
            strGl.value.replace(',', '.').toFloat()
        } catch (_: Throwable) {
            0f
        }
        val valMgDl = if (isMmol) rawVal * 18.0182f else rawVal

        val targetLow = Natives.targetlow().let { if (it > 0f) it else 70f }
        val targetHigh = Natives.targethigh().let { if (it > 0f) it else 180f }
        val status = GlucoseStatus.fromValue(valMgDl, targetLow, targetHigh)

        return ComplicationGlucose(
            value = if (isOld) "---" else strGl.value,
            isOld = isOld,
            rate = strGl.rate,
            status = status,
            time = timeMs,
            isMmol = isMmol
        )
    }

    /**
     * Sample glucose data used for complication previews in the watch face customizer.
     */
    fun getPreviewGlucose(): ComplicationGlucose {
        val isMmol = Applic.unit == 1
        return ComplicationGlucose(
            value = if (isMmol) "5.6" else "108",
            isOld = false,
            rate = 0.8f,
            status = GlucoseStatus.IN_RANGE,
            time = System.currentTimeMillis(),
            isMmol = isMmol
        )
    }

    /**
     * Resolves the primary content color (value & arrow) based on style and glucose status.
     */
    fun resolveColor(style: ComplicationColorStyle, status: GlucoseStatus, isOld: Boolean): Int {
        if (isOld) return COLOR_TEXT_MUTED
        return when (style) {
            ComplicationColorStyle.MONOCHROME -> COLOR_WHITE
            ComplicationColorStyle.SIGNAL -> when (status) {
                GlucoseStatus.IN_RANGE -> COLOR_IN_RANGE
                GlucoseStatus.LOW, GlucoseStatus.HIGH -> COLOR_WARNING
                GlucoseStatus.VERY_LOW, GlucoseStatus.VERY_HIGH -> COLOR_CRITICAL
            }
        }
    }

    /**
     * Shrink-to-fit text sizing: starts at [baseSize] and only ever shrinks so
     * the text fits [maxWidth]. Small values are never stretched up, which keeps
     * digit sizes consistent ("55" renders at the same size as "108").
     */
    private fun fitTextSize(paint: Paint, text: String, baseSize: Float, maxWidth: Float): Float {
        var size = baseSize
        paint.textSize = size
        val minSize = baseSize * 0.55f
        while (size > minSize && paint.measureText(text) > maxWidth) {
            size *= 0.94f
            paint.textSize = size
        }
        return size
    }

    /** Draws [text] horizontally centered with true optical vertical centering. */
    private fun drawCenteredText(canvas: Canvas, text: String, paint: Paint, centerY: Float) {
        val fm = paint.fontMetrics
        val baseline = centerY - (fm.descent + fm.ascent) / 2f
        canvas.drawText(text, CX, baseline, paint)
    }

    /**
     * Renders a complication into a [CANVAS_SIZE]x[CANVAS_SIZE] ARGB_8888 bitmap.
     */
    fun renderComplication(
        layout: ComplicationLayout,
        style: ComplicationColorStyle,
        glucose: ComplicationGlucose,
        showTime: Boolean = Natives.gettimeOnComplication()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: transparent by default for seamless watch face integration;
        // respects custom background if explicitly set by user.
        val bg = Natives.getComplicationBackgroundColor()
        if ((bg and -0x1000000) != 0) {
            canvas.drawColor(bg)
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        val primaryColor = resolveColor(style, glucose.status, glucose.isOld)
        val trend = if (glucose.isOld) TrendArrow.UNKNOWN else TrendArrow.fromRate(glucose.rate)

        // User font-size preference from the legacy color config screen (0..1, default 1).
        val userScale = try {
            GlucoseValue.fontFraction.coerceIn(0.5f, 1f)
        } catch (_: Throwable) {
            1f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = numberTypeface
            color = primaryColor
            textAlign = Paint.Align.CENTER
        }

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = primaryColor
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = secondaryTypeface
            color = COLOR_TEXT_MUTED
            textAlign = Paint.Align.CENTER
            textSize = 22f * S
        }

        val timeStr = if (showTime && !glucose.isOld && glucose.time > 0) {
            tk.glucodata.NumberView.minhourstr(glucose.time)
        } else null

        // Keep glyphs inside the inner ~84% so circular slots never clip digits.
        val safeWidth = CANVAS_SIZE * 0.84f

        when (layout) {
            ComplicationLayout.ARROW_TOP -> {
                val cyVal = if (timeStr != null) 152f * S else 158f * S
                val cyArrow = 70f * S
                val baseSize = (if (glucose.isMmol) 68f else 72f) * S * userScale
                fitTextSize(textPaint, glucose.value, baseSize, safeWidth)
                drawCenteredText(canvas, glucose.value, textPaint, cyVal)

                drawTrendArrow(
                    canvas = canvas,
                    paint = arrowPaint,
                    centerX = CX,
                    centerY = cyArrow,
                    hw = 17f * S,
                    headSize = 13f * S,
                    strokeWidth = 5.5f * S,
                    trend = trend,
                    color = primaryColor
                )

                if (timeStr != null) {
                    drawCenteredText(canvas, timeStr, timePaint, 215f * S)
                }
            }

            ComplicationLayout.ARROW_BOTTOM -> {
                val cyVal = if (timeStr != null) 104f * S else 98f * S
                val cyArrow = 186f * S
                val baseSize = (if (glucose.isMmol) 68f else 72f) * S * userScale
                fitTextSize(textPaint, glucose.value, baseSize, safeWidth)
                drawCenteredText(canvas, glucose.value, textPaint, cyVal)

                drawTrendArrow(
                    canvas = canvas,
                    paint = arrowPaint,
                    centerX = CX,
                    centerY = cyArrow,
                    hw = 17f * S,
                    headSize = 13f * S,
                    strokeWidth = 5.5f * S,
                    trend = trend,
                    color = primaryColor
                )

                if (timeStr != null) {
                    drawCenteredText(canvas, timeStr, timePaint, 44f * S)
                }
            }

            ComplicationLayout.ARROW_BESIDE -> {
                val baseSize = (if (glucose.isMmol) 64f else 68f) * S * userScale
                textPaint.textSize = baseSize

                val hw = 15f * S
                val headSize = 12f * S
                val strokeWidth = 5f * S
                val spacing = 10f * S
                val arrowWidth = 2 * hw

                // Shrink the value (never the arrow) so value + arrow stay centered.
                var size = baseSize
                var textWidth = textPaint.measureText(glucose.value)
                val minSize = baseSize * 0.55f
                while (size > minSize && textWidth + spacing + arrowWidth > safeWidth) {
                    size *= 0.94f
                    textPaint.textSize = size
                    textWidth = textPaint.measureText(glucose.value)
                }

                val groupWidth = textWidth + spacing + arrowWidth
                val startX = CX - groupWidth / 2f

                val cyVal = if (timeStr != null) 120f * S else CY
                val fm = textPaint.fontMetrics
                val baseline = cyVal - (fm.descent + fm.ascent) / 2f

                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(glucose.value, startX, baseline, textPaint)

                val arrowCenterX = startX + textWidth + spacing + hw
                drawTrendArrow(
                    canvas = canvas,
                    paint = arrowPaint,
                    centerX = arrowCenterX,
                    centerY = cyVal,
                    hw = hw,
                    headSize = headSize,
                    strokeWidth = strokeWidth,
                    trend = trend,
                    color = primaryColor
                )

                if (timeStr != null) {
                    textPaint.textAlign = Paint.Align.CENTER
                    drawCenteredText(canvas, timeStr, timePaint, 215f * S)
                }
            }

            ComplicationLayout.VALUE_ONLY -> {
                val cyVal = if (timeStr != null) 120f * S else CY
                val baseSize = (if (glucose.isMmol) 84f else 92f) * S * userScale
                fitTextSize(textPaint, glucose.value, baseSize, safeWidth)
                drawCenteredText(canvas, glucose.value, textPaint, cyVal)

                if (timeStr != null) {
                    drawCenteredText(canvas, timeStr, timePaint, 215f * S)
                }
            }

            ComplicationLayout.ARROW_ONLY -> {
                val cyArrow = if (timeStr != null) 120f * S else CY
                drawTrendArrow(
                    canvas = canvas,
                    paint = arrowPaint,
                    centerX = CX,
                    centerY = cyArrow,
                    hw = 44f * S,
                    headSize = 34f * S,
                    strokeWidth = 9.5f * S,
                    trend = trend,
                    color = primaryColor
                )

                if (timeStr != null) {
                    drawCenteredText(canvas, timeStr, timePaint, 215f * S)
                }
            }
        }

        return bitmap
    }

    /**
     * Renders a standalone trend arrow icon bitmap (e.g. for SHORT_TEXT complication icon).
     *
     * Follows the 24dp-guardrail practice: the glyph occupies the inner ~64%
     * with transparent padding so tight circular slots never clip the arrowhead.
     */
    fun renderArrowIcon(
        style: ComplicationColorStyle,
        glucose: ComplicationGlucose,
        size: Int = ICON_SIZE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val color = resolveColor(style, glucose.status, glucose.isOld)
        val trend = if (glucose.isOld) TrendArrow.UNKNOWN else TrendArrow.fromRate(glucose.rate)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }

        val half = size / 2f
        val hw = size * 0.30f
        val headSize = size * 0.23f
        val strokeWidth = size * 0.085f

        drawTrendArrow(
            canvas = canvas,
            paint = paint,
            centerX = half,
            centerY = half,
            hw = hw,
            headSize = headSize,
            strokeWidth = strokeWidth,
            trend = trend,
            color = color
        )

        return bitmap
    }

    /**
     * Draws a modern Material 3 rounded geometric trend arrow.
     * Accurately supports all 7 trend states + double chevron for rapid rise/fall.
     */
    private fun drawTrendArrow(
        canvas: Canvas,
        paint: Paint,
        centerX: Float,
        centerY: Float,
        hw: Float,
        headSize: Float,
        strokeWidth: Float,
        trend: TrendArrow,
        color: Int
    ) {
        paint.strokeWidth = strokeWidth
        paint.color = color

        if (trend == TrendArrow.UNKNOWN) {
            // Clean horizontal dash for unknown / stale
            canvas.drawLine(centerX - hw, centerY, centerX + hw, centerY, paint)
            return
        }

        canvas.save()
        canvas.rotate(trend.angleDegrees, centerX, centerY)

        val tipX = centerX + hw
        val tailX = centerX - hw

        // Arrow shaft
        canvas.drawLine(tailX, centerY, tipX, centerY, paint)

        // Arrowhead chevron
        val wingAngleRad = Math.toRadians(38.0)
        val headDx = (headSize * cos(wingAngleRad)).toFloat()
        val headDy = (headSize * sin(wingAngleRad)).toFloat()

        val headPath = Path().apply {
            moveTo(tipX - headDx, centerY - headDy)
            lineTo(tipX, centerY)
            lineTo(tipX - headDx, centerY + headDy)
        }
        canvas.drawPath(headPath, paint)

        // Rapid rate of change: draw second parallel chevron
        if (trend == TrendArrow.RAPIDLY_RISING || trend == TrendArrow.RAPIDLY_FALLING) {
            val offset = headSize * 0.65f
            val headPath2 = Path().apply {
                moveTo(tipX - offset - headDx, centerY - headDy)
                lineTo(tipX - offset, centerY)
                lineTo(tipX - offset - headDx, centerY + headDy)
            }
            canvas.drawPath(headPath2, paint)
        }

        canvas.restore()
    }

    /**
     * Constructs the Wear OS ComplicationData for a given request or preview.
     *
     * Best practices applied:
     * - SHORT_TEXT text stays within the 7-character limit and provides BOTH
     *   a tintable monochromatic image and a full-color small image, so single-
     *   color and full-color watch faces each pick the right asset.
     * - SMALL_IMAGE uses [SmallImageType.ICON] (transparent, never cropped)
     *   instead of PHOTO, so digits are never clipped by circular masks.
     * - MONOCHROMATIC_IMAGE is always single-color white on transparent so the
     *   watch face can tint it for ambient / burn-in-safe rendering.
     */
    fun buildComplicationData(
        type: ComplicationType,
        layout: ComplicationLayout,
        style: ComplicationColorStyle,
        isPreview: Boolean,
        pendingIntent: PendingIntent?
    ): ComplicationData? {
        val glucose = if (isPreview) getPreviewGlucose() else getLatestGlucose()
        val trend = if (glucose.isOld) TrendArrow.UNKNOWN else TrendArrow.fromRate(glucose.rate)
        val descText = PlainComplicationText.Builder(
            "Glucose ${glucose.value}, ${trend.label}"
        ).build()
        val valueText = PlainComplicationText.Builder(glucose.value).build()

        // Monochrome arrow asset any watch face can tint; signal arrow asset
        // for full-color watch faces. Both share identical geometry.
        val monoArrowIcon = Icon.createWithBitmap(
            renderArrowIcon(ComplicationColorStyle.MONOCHROME, glucose)
        )
        val signalArrowIcon = Icon.createWithBitmap(
            renderArrowIcon(ComplicationColorStyle.SIGNAL, glucose)
        )

        // Optional time title for text slots ("13:42"); null when stale/hidden.
        val showTime = Natives.gettimeOnComplication()
        val titleText = if (showTime && !glucose.isOld && glucose.time > 0) {
            PlainComplicationText.Builder(
                tk.glucodata.NumberView.minhourstr(glucose.time)
            ).build()
        } else null

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = valueText,
                    contentDescription = descText
                )
                    .setTitle(titleText)
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(monoArrowIcon).build()
                    )
                    .setSmallImage(
                        SmallImage.Builder(signalArrowIcon, SmallImageType.ICON).build()
                    )
                    .setTapAction(pendingIntent)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = valueText,
                    contentDescription = descText
                )
                    .setTitle(titleText)
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(monoArrowIcon).build()
                    )
                    .setSmallImage(
                        SmallImage.Builder(signalArrowIcon, SmallImageType.ICON).build()
                    )
                    .setTapAction(pendingIntent)
                    .build()
            }

            ComplicationType.SMALL_IMAGE -> {
                val bitmap = renderComplication(layout, style, glucose)
                val image = Icon.createWithBitmap(bitmap)
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(image, SmallImageType.ICON).build(),
                    contentDescription = descText
                )
                    .setTapAction(pendingIntent)
                    .build()
            }

            ComplicationType.PHOTO_IMAGE -> {
                val bitmap = renderComplication(layout, style, glucose)
                val image = Icon.createWithBitmap(bitmap)
                PhotoImageComplicationData.Builder(
                    photoImage = image,
                    contentDescription = descText
                )
                    .setTapAction(pendingIntent)
                    .build()
            }

            ComplicationType.MONOCHROMATIC_IMAGE -> {
                val bitmap = renderComplication(layout, ComplicationColorStyle.MONOCHROME, glucose)
                val image = Icon.createWithBitmap(bitmap)
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(image).build(),
                    contentDescription = descText
                )
                    .setTapAction(pendingIntent)
                    .build()
            }

            else -> null
        }
    }

    /** Bounds helper for legacy callers that still measure text manually. */
    @JvmStatic
    fun measureTextWidth(text: String, textSize: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = numberTypeface
            this.textSize = textSize
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        return bounds.width().toFloat()
    }
}
