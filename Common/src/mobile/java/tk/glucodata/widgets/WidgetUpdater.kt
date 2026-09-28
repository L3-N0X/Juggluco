package tk.glucodata.widgets

import android.app.PendingIntent
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.widget.RemoteViews
import tk.glucodata.Log
import tk.glucodata.MainActivity
import tk.glucodata.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Keeps the home screen widgets current.
 *
 * All work runs on one background thread: a new reading arrives on the Bluetooth thread and must
 * not wait for bitmaps to be drawn. Requests that come in while an update is queued collapse into
 * that update.
 */
object WidgetUpdater {
    private const val LOG_ID = "WidgetUpdater"
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "widgets").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val pending = AtomicBoolean(false)
    private val listening = AtomicBoolean(false)

    /** Redraws every widget, e.g. after a new reading. Cheap to call often. */
    fun requestUpdateAll(context: Context) {
        val app = context.applicationContext
        if (!pending.compareAndSet(false, true)) return
        executor.execute {
            pending.set(false)
            try {
                val manager = AppWidgetManager.getInstance(app)
                val targets = WidgetKind.entries.associateWith { kind ->
                    manager.getAppWidgetIds(ComponentName(app, kind.provider))
                }.filterValues { it.isNotEmpty() }
                if (targets.isNotEmpty()) render(app, manager, targets)
            } catch (th: Throwable) {
                Log.stack(LOG_ID, "requestUpdateAll", th)
            }
        }
    }

    /** Redraws specific widgets, finishing [done] (from `goAsync`) afterwards. */
    fun update(context: Context, kind: WidgetKind, appWidgetIds: IntArray, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            try {
                render(app, AppWidgetManager.getInstance(app), mapOf(kind to appWidgetIds))
            } catch (th: Throwable) {
                Log.stack(LOG_ID, "update", th)
            } finally {
                done?.invoke()
            }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, targets: Map<WidgetKind, IntArray>) {
        listenForThemeChanges(context)
        val configs = targets.mapValues { (kind, ids) -> ids.map { it to WidgetConfigStore.load(context, it, kind) } }
        val now = System.currentTimeMillis()
        var historyMillis = 30 * 60_000L
        configs.forEach { (kind, list) ->
            list.forEach { (_, config) ->
                if (kind.hasGraph) historyMillis = maxOf(historyMillis, config.graphHours * 3_600_000L)
                if (kind.hasStats) historyMillis = maxOf(historyMillis, now - WidgetSnapshot.periodStart(config.statsPeriod, now))
            }
        }
        val snapshot = WidgetDataSource.load(context, historyMillis)
        val renderer = WidgetRenderer(context)
        val description = renderer.describe(snapshot)
        val systemDark = WidgetPalette.systemIsDark()
        val wallpaperDark = WidgetPalette.wallpaperIsDark(context)
        for ((kind, list) in configs) {
            for ((id, config) in list) {
                try {
                    val palette = WidgetPalette.resolve(context, config, snapshot.status, systemDark, wallpaperDark)
                    val views = buildViews(context, manager, kind, id) { widthDp, heightDp, pxPerDp ->
                        remoteViews(context, id, renderer.render(kind, config, snapshot, widthDp, heightDp, pxPerDp, palette), description)
                    }
                    manager.updateAppWidget(id, views)
                } catch (th: Throwable) {
                    Log.stack(LOG_ID, "render $kind $id", th)
                }
            }
        }
    }

    /**
     * One bitmap per size the launcher may show the widget at, so it is pixel exact in portrait
     * and landscape alike, scaled down if needed to stay within what RemoteViews may carry.
     */
    private fun buildViews(
        context: Context,
        manager: AppWidgetManager,
        kind: WidgetKind,
        appWidgetId: Int,
        build: (Float, Float, Float) -> RemoteViews
    ): RemoteViews {
        val sizes = widgetSizes(manager.getAppWidgetOptions(appWidgetId))
            .ifEmpty { listOf(SizeF(kind.defaultWidthDp, kind.defaultHeightDp)) }
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        // RemoteViews bitmaps may use up to 1.5 screens of ARGB memory in total; stay well below.
        val budget = metrics.widthPixels.toFloat() * metrics.heightPixels * 0.9f
        val totalPixels = sizes.sumOf { (it.width * it.height * density * density).toDouble() }.toFloat()
        val pxPerDp = if (totalPixels > budget) density * sqrt(budget / totalPixels) else density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
            return RemoteViews(sizes.associateWith { build(it.width, it.height, pxPerDp) })
        }
        if (sizes.size >= 2) {
            val portrait = sizes.first()
            val landscape = sizes.last()
            return RemoteViews(
                build(landscape.width, landscape.height, pxPerDp),
                build(portrait.width, portrait.height, pxPerDp)
            )
        }
        return build(sizes.first().width, sizes.first().height, pxPerDp)
    }

    private fun widgetSizes(options: Bundle?): List<SizeF> {
        if (options == null) return emptyList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                return sizes.filter { it.width > 0f && it.height > 0f }.distinct().take(4)
            }
        }
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (minWidth <= 0 || maxHeight <= 0) return emptyList()
        // Launchers report portrait as min width by max height and landscape as the reverse.
        return listOf(
            SizeF(minWidth.toFloat(), maxHeight.toFloat()),
            SizeF(maxOf(maxWidth, minWidth).toFloat(), maxOf(1, min(minHeight, maxHeight)).toFloat())
        ).distinct()
    }

    private fun remoteViews(context: Context, appWidgetId: Int, bitmap: android.graphics.Bitmap, description: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.home_widget).apply {
            setImageViewBitmap(R.id.home_widget_image, bitmap)
            setContentDescription(R.id.home_widget_image, description)
            setOnClickPendingIntent(R.id.home_widget_root, openAppIntent(context, appWidgetId))
        }

    private fun openAppIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or immutable)
    }

    /**
     * Drawn widgets do not follow a theme switch by themselves, so redraw on dark mode and
     * wallpaper changes for as long as the process lives. Widgets are redrawn with every reading
     * anyway, so a missed change only lasts until the next one.
     */
    private fun listenForThemeChanges(context: Context) {
        if (!listening.compareAndSet(false, true)) return
        Handler(Looper.getMainLooper()).post {
            var nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            context.registerComponentCallbacks(object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    val mode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (mode != nightMode) {
                        nightMode = mode
                        requestUpdateAll(context)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() {
                }
            })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    WallpaperManager.getInstance(context).addOnColorsChangedListener(
                        { _, _ -> requestUpdateAll(context) },
                        Handler(Looper.getMainLooper())
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }
}
