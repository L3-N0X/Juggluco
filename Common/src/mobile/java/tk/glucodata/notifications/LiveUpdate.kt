package tk.glucodata.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.widgets.WidgetPalette
import tk.glucodata.widgets.WidgetSnapshot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android 16 Live Updates: the glucose notification promoted to a chip in the status bar, with
 * the notification itself at the top of the shade and on the lock screen.
 *
 * The system only promotes notifications built from its own templates, so in this mode the
 * glucose notification drops the drawn content for a standard one: value and trend as the title,
 * change and time as the text, and [Notification.ProgressStyle] as a gauge through the glucose
 * ranges with a marker at the current value. The chip shows the small icon and
 * [Notification.Builder.setShortCriticalText].
 */
object LiveUpdate {
    /** Notification.EXTRA_REQUEST_PROMOTED_ONGOING, public from API 36.1; the key works from 36. */
    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS, public from API 36.1. */
    private const val ACTION_MANAGE_PROMOTED = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

    private const val SCALE_LOW = 40f

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    /** Whether the user lets Juggluco post Live Updates (on by default, switchable per app). */
    fun allowed(context: Context): Boolean = supported && try {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canPostPromotedNotifications()
    } catch (_: Throwable) {
        false
    }

    /** The system page where Live Updates are allowed for this app. */
    fun settingsIntent(context: Context): Intent {
        val promoted = Intent(ACTION_MANAGE_PROMOTED)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (promoted.resolveActivity(context.packageManager) != null) return promoted
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    fun title(context: Context, config: NotificationConfig, snapshot: WidgetSnapshot, input: StatusIconInput): String {
        if (!snapshot.hasReading) return context.getString(R.string.novalue)
        val parts = ArrayList<String>(3)
        parts += snapshot.unit.format(snapshot.currentMgDl)
        if (config.showUnit) parts += context.getString(snapshot.unit.labelRes)
        if (config.showArrow) input.arrow?.let { parts += it.symbol }
        return parts.joinToString(" ")
    }

    fun text(context: Context, config: NotificationConfig, snapshot: WidgetSnapshot, input: StatusIconInput, time: String): String? {
        val parts = ArrayList<String>(3)
        if (config.showDelta) input.deltaText?.let { parts += "Δ $it" }
        if (config.showTime || snapshot.isStale) parts += time
        if (config.showTimeInRange) snapshot.stats(config.statsPeriod)?.let {
            parts += context.getString(R.string.widget_in_range) + " " + (it.inRange * 100f).roundToInt() + "%"
        }
        return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    fun chipText(config: NotificationConfig, input: StatusIconInput): String? {
        val value = input.valueText ?: return null
        val arrow = input.arrow?.symbol
        return when (config.liveChipText) {
            LiveChipText.VALUE -> value
            LiveChipText.VALUE_ARROW -> if (arrow != null) "$value $arrow" else value
            LiveChipText.VALUE_DELTA -> input.deltaText?.let { "$value $it" } ?: value
            LiveChipText.DELTA -> input.deltaText
            LiveChipText.NONE -> null
        }
    }

    /** Chip and accent color: the range of the reading, or the app's accent. */
    fun color(config: NotificationConfig, snapshot: WidgetSnapshot, palette: WidgetPalette): Int {
        val status = snapshot.status
        return if (config.rangeColors && status != null && !snapshot.isStale) palette.rangeColor(status) else palette.accent
    }

    /** Glucose ranges along the gauge, as (from, to, range) in mg/dL. */
    fun zones(snapshot: WidgetSnapshot): List<Triple<Float, Float, GlucoseStatus>> {
        val range = snapshot.range
        val high = max(300f, range.veryHighMgDl + 50f)
        return listOf(
            Triple(SCALE_LOW, range.veryLowMgDl, GlucoseStatus.VERY_LOW),
            Triple(range.veryLowMgDl, range.lowMgDl, GlucoseStatus.LOW),
            Triple(range.lowMgDl, range.highMgDl, GlucoseStatus.IN_RANGE),
            Triple(range.highMgDl, range.veryHighMgDl, GlucoseStatus.HIGH),
            Triple(range.veryHighMgDl, high, GlucoseStatus.VERY_HIGH)
        ).filter { it.second > it.first }
    }

    /** Position of [mgDl] on the gauge, 0 to 1. */
    fun position(snapshot: WidgetSnapshot, mgDl: Float): Float {
        val zones = zones(snapshot)
        val low = zones.first().first
        val high = zones.last().second
        return ((mgDl.coerceIn(low, high) - low) / (high - low)).coerceIn(0f, 1f)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun style(
        context: Context,
        builder: Notification.Builder,
        config: NotificationConfig,
        snapshot: WidgetSnapshot,
        valueText: String,
        palette: WidgetPalette
    ) {
        val input = GlucoseNotificationStyler.iconInput(context, snapshot, valueText)
        GlucoseNotificationStyler.icon(context, config.liveChipIcon, config, input)?.let { builder.setSmallIcon(it) }
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(snapshot.currentTime))
        builder.setContentTitle(title(context, config, snapshot, input))
            .setContentText(text(context, config, snapshot, input, time))
            .setShortCriticalText(chipText(config, input))
            .setColor(color(config, snapshot, palette))
            .setColorized(false)
            .setOngoing(true)
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        builder.setStyle(if (config.liveRangeBar && snapshot.hasReading) rangeStyle(context, snapshot, palette) else null)
        if (config.sparkline && snapshot.hasReading) {
            val renderer = NotificationRenderer(context)
            builder.setLargeIcon(Icon.createWithBitmap(renderer.graphIcon(config, snapshot, 64f, NotificationRenderer.pxPerDp(context), palette)))
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun rangeStyle(context: Context, snapshot: WidgetSnapshot, palette: WidgetPalette): Notification.ProgressStyle {
        // Whole mg/dL as units, so every zone keeps its true share of the bar.
        val segments = zones(snapshot).mapNotNull { (from, to, zone) ->
            val length = (to - from).roundToInt()
            if (length <= 0) null
            else Notification.ProgressStyle.Segment(length).setColor(palette.rangeColor(zone))
        }
        val total = segments.sumOf { it.length }
        val status = snapshot.status ?: GlucoseStatus.IN_RANGE
        val marker = if (snapshot.isStale) palette.contentVariant else palette.rangeColor(status)
        return Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgressSegments(segments)
            .setProgress((position(snapshot, snapshot.currentMgDl) * total).roundToInt())
            .setProgressTrackerIcon(Icon.createWithBitmap(tracker(context, marker)))
    }

    /** Knob on the gauge: the range color in a white ring, so it stands out on any segment. */
    private fun tracker(context: Context, color: Int): Bitmap {
        val size = (24f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x33000000
        canvas.drawCircle(c, c + size * 0.03f, c * 0.96f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(c, c, c * 0.92f, paint)
        paint.color = color
        canvas.drawCircle(c, c, c * 0.62f, paint)
        return bitmap
    }
}
