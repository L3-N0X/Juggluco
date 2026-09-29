package tk.glucodata.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import tk.glucodata.Log
import tk.glucodata.Notify
import tk.glucodata.R
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.widgets.WidgetDataSource
import tk.glucodata.widgets.WidgetPalette
import tk.glucodata.widgets.WidgetRenderer
import tk.glucodata.widgets.WidgetSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Builds the glucose notification's content and icon, and keeps the extra status bar icons in step.
 *
 * Called from [Notify] on whatever thread delivered the reading. The history behind one reading is
 * read once and shared by the glucose notification and the extra icons.
 */
object GlucoseNotificationStyler {
    private const val LOG_ID = "GlucoseNotification"
    const val STATUS_ICON_CHANNEL = "statusBarIcons"
    private val STATUS_ICON_IDS = intArrayOf(81441, 81442)

    private class Cached(val key: String, val made: Long, val snapshot: WidgetSnapshot)

    private var cached: Cached? = null
    private var channelCreated = false

    @Synchronized
    fun snapshot(context: Context, config: NotificationConfig, time: Long, mgDl: Float, rate: Float): WidgetSnapshot {
        val now = System.currentTimeMillis()
        val history = config.historyMillis(now)
        val key = "$time:$mgDl:$rate:$history"
        cached?.let { if (it.key == key && now - it.made < 20_000L) return it.snapshot }
        return WidgetDataSource.load(context, history, time, mgDl, rate).also { cached = Cached(key, now, it) }
    }

    fun iconInput(context: Context, snapshot: WidgetSnapshot, valueText: String?): StatusIconInput {
        val stale = snapshot.isStale
        return StatusIconInput(
            valueText = valueText ?: if (snapshot.hasReading) iconValueText(snapshot) else null,
            arrow = if (stale) null else TrendArrow.fromRate(snapshot.rate).takeIf { it != TrendArrow.UNKNOWN },
            deltaText = WidgetRenderer(context).deltaText(snapshot),
            stale = stale
        )
    }

    /** "6" rather than "6.0", as the status bar icon always showed whole mmol/L values. */
    fun iconValueText(snapshot: WidgetSnapshot): String {
        val text = snapshot.unit.format(snapshot.currentMgDl)
        return if (text.length > 2 && text.endsWith("0") && !text[text.length - 2].isDigit()) text.dropLast(2) else text
    }

    fun icon(context: Context, kind: StatusIconKind, config: NotificationConfig, input: StatusIconInput): Icon? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        if (kind == StatusIconKind.APP) return Icon.createWithResource(context, R.drawable.novalue)
        return StatusIconRenderer.render(kind, input, config, StatusIconRenderer.sizePx(context))?.let { Icon.createWithBitmap(it) }
    }

    /** Icon, collapsed and expanded content of the glucose notification. */
    fun style(context: Context, builder: Notification.Builder, config: NotificationConfig, snapshot: WidgetSnapshot, valueText: String) {
        NotificationRefresher.listenForThemeChanges(context)
        if (config.liveUpdate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && LiveUpdate.allowed(context)) {
            LiveUpdate.style(context, builder, config, snapshot, valueText, WidgetPalette.forNotification(context))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            icon(context, config.mainIcon, config, iconInput(context, snapshot, valueText))?.let { builder.setSmallIcon(it) }
        }
        val renderer = NotificationRenderer(context)
        val pxPerDp = NotificationRenderer.pxPerDp(context)
        val palette = WidgetPalette.forNotification(context)
        val description = renderer.describe(snapshot)
        val collapsed = RemoteViews(context.packageName, R.layout.notification_glucose).apply {
            setImageViewBitmap(
                R.id.notification_glucose_image,
                renderer.collapsed(config, snapshot, NotificationRenderer.collapsedWidthDp(context), pxPerDp, palette)
            )
            setContentDescription(R.id.notification_glucose_image, description)
        }
        val expanded = renderer.expanded(config, snapshot, NotificationRenderer.expandedWidthDp(context), pxPerDp, palette)?.let { bitmap ->
            RemoteViews(context.packageName, R.layout.notification_glucose_expanded).apply {
                setImageViewBitmap(R.id.notification_glucose_image, bitmap)
                setContentDescription(R.id.notification_glucose_image, description)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setStyle(Notification.DecoratedCustomViewStyle())
            builder.setCustomContentView(collapsed)
            builder.setCustomBigContentView(expanded)
        } else {
            @Suppress("DEPRECATION")
            builder.setContent(collapsed)
        }
    }

    /**
     * Posts the extra status bar icons for [snapshot], or with a null [snapshot] only takes away
     * the ones that are switched off.
     */
    fun postStatusIcons(context: Context, config: NotificationConfig, snapshot: WidgetSnapshot?, valueText: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canShow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        val input = if (snapshot != null && snapshot.hasReading && !snapshot.isStale) iconInput(context, snapshot, valueText) else null
        config.extraIcons.forEachIndexed { slot, kind ->
            val id = STATUS_ICON_IDS[slot]
            if (kind == null || !canShow) {
                manager.cancel(id)
                return@forEachIndexed
            }
            if (input == null || snapshot == null) return@forEachIndexed
            try {
                val icon = icon(context, kind, config, input) ?: return@forEachIndexed
                ensureChannel(context, manager)
                @Suppress("DEPRECATION")
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, STATUS_ICON_CHANNEL)
                else Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT)
                builder.setSmallIcon(icon)
                    .setContentTitle(statusIconTitle(context, kind, snapshot, input))
                    .setContentIntent(Notify.mkpending())
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(true)
                    // A millisecond apart, so the system keeps the icons in the order of the slots.
                    .setWhen(snapshot.currentTime - slot)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    // Its own group each, or Android bundles the icons into one.
                    .setGroup("juggluco_status_icon_$slot")
                    // Watches get the glucose notification already.
                    .setLocalOnly(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setTimeoutAfter(Notify.glucosetimeout)
                manager.notify(id, builder.build())
            } catch (th: Throwable) {
                Log.stack(LOG_ID, "postStatusIcons", th)
            }
        }
    }

    private fun statusIconTitle(context: Context, kind: StatusIconKind, snapshot: WidgetSnapshot, input: StatusIconInput): String {
        val unit = context.getString(snapshot.unit.labelRes)
        val trend = context.getString((input.arrow ?: TrendArrow.UNKNOWN).labelRes)
        val value = "${snapshot.unit.format(snapshot.currentMgDl)} $unit"
        return when (kind) {
            StatusIconKind.VALUE, StatusIconKind.APP -> value
            StatusIconKind.VALUE_ARROW -> "$value · $trend"
            StatusIconKind.ARROW -> trend
            StatusIconKind.DELTA -> input.deltaText?.let { "Δ $it $unit" } ?: value
        }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (channelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            STATUS_ICON_CHANNEL,
            context.getString(R.string.notif_channel_status_icons),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_status_icons_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
        channelCreated = true
    }
}

/**
 * Redraws the glucose notification after a settings or theme change. Requests are collapsed, so a
 * slider being dragged redraws once it settles.
 */
object NotificationRefresher {
    private const val LOG_ID = "NotificationRefresher"
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "notification").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val listening = AtomicBoolean(false)
    private val refresh = Runnable {
        executor.execute {
            try {
                Notify.refreshGlucoseNotification()
            } catch (th: Throwable) {
                Log.stack(LOG_ID, "refresh", th)
            }
        }
    }

    fun request(context: Context) {
        listenForThemeChanges(context)
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 350L)
    }

    /** The notification is drawn for light or dark, so it is redrawn when the system switches. */
    fun listenForThemeChanges(context: Context) {
        if (!listening.compareAndSet(false, true)) return
        val app = context.applicationContext
        handler.post {
            var nightMode = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            app.registerComponentCallbacks(object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    val mode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (mode != nightMode) {
                        nightMode = mode
                        request(app)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() {
                }
            })
        }
    }
}
