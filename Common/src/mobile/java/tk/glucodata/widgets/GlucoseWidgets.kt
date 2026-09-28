package tk.glucodata.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject

/** Shared provider logic; every widget kind is a subclass so launchers list them separately. */
abstract class BaseGlucoseWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val result = goAsync()
        WidgetUpdater.update(context, kind, appWidgetIds) { result.finish() }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val result = goAsync()
        WidgetUpdater.update(context, kind, intArrayOf(appWidgetId)) { result.finish() }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetConfigStore.delete(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (from, to) -> WidgetConfigStore.move(context, from, to) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PINNED) {
            // A widget added from the app: store the settings chosen there for the new id.
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val json = intent.getStringExtra(EXTRA_CONFIG)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && json != null) {
                try {
                    WidgetConfigStore.save(context, id, WidgetConfig.fromJson(JSONObject(json), kind.defaultConfig()))
                } catch (_: Throwable) {
                }
                val result = goAsync()
                WidgetUpdater.update(context, kind, intArrayOf(id)) { result.finish() }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_PINNED = "tk.glucodata.widgets.PINNED"
        const val EXTRA_CONFIG = "tk.glucodata.widgets.CONFIG"
    }
}

class MinimalGlucoseWidget : BaseGlucoseWidget(WidgetKind.MINIMAL)
class CompactGlucoseWidget : BaseGlucoseWidget(WidgetKind.COMPACT)
class TrendGlucoseWidget : BaseGlucoseWidget(WidgetKind.TREND)
class DialGlucoseWidget : BaseGlucoseWidget(WidgetKind.DIAL)
class RangeGlucoseWidget : BaseGlucoseWidget(WidgetKind.RANGE)
