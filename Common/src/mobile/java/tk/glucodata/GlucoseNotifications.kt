package tk.glucodata

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Build
import tk.glucodata.notifications.GlucoseNotificationStyler
import tk.glucodata.notifications.NotificationConfigStore
import tk.glucodata.notifications.StatusIconInput
import tk.glucodata.notifications.StatusIconKind
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.widgets.WidgetDataSource

/**
 * Entry points for [Notify] into the phone's glucose notification (tk.glucodata.notifications).
 * The watch builds have a stub with the same name.
 */
object GlucoseNotifications {
    private const val LOG_ID = "GlucoseNotifications"

    /** The glucose value as a status bar icon, drawn with the notification settings. */
    @JvmStatic
    fun valueIcon(text: String): Icon? = try {
        val context = Applic.app
        val config = NotificationConfigStore.load(context)
        GlucoseNotificationStyler.icon(context, StatusIconKind.VALUE, config, StatusIconInput(text, null, null, false))
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "valueIcon", th)
        null
    }

    /**
     * Gives the glucose notification its icon and content and updates the extra status bar icons.
     * Returns false when it could not, and the caller falls back to the plain notification.
     */
    @JvmStatic
    fun style(builder: Notification.Builder, glucose: notGlucose, displayValue: Float, valueText: String): Boolean = try {
        val context = Applic.app
        val config = NotificationConfigStore.load(context)
        val mgDl = GlucoseUnit.fromNative(Applic.unit).toMgDl(displayValue)
        val snapshot = GlucoseNotificationStyler.snapshot(context, config, glucose.time, mgDl, glucose.rate)
        GlucoseNotificationStyler.style(context, builder, config, snapshot, valueText)
        GlucoseNotificationStyler.postStatusIcons(context, config, snapshot, valueText)
        true
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "style", th)
        false
    }

    /**
     * Updates only the extra status bar icons, for when the glucose notification itself is off.
     * Without a [glucose] it only takes away the icons that are switched off.
     */
    @JvmStatic
    fun statusIcons(glucose: notGlucose?, displayValue: Float, valueText: String?) {
        try {
            val context = Applic.app
            val config = NotificationConfigStore.load(context)
            val snapshot = glucose?.let {
                val mgDl = GlucoseUnit.fromNative(Applic.unit).toMgDl(displayValue)
                GlucoseNotificationStyler.snapshot(context, config, it.time, mgDl, it.rate)
            }
            GlucoseNotificationStyler.postStatusIcons(context, config, snapshot, valueText)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "statusIcons", th)
        }
    }

    /**
     * Once readings stopped: gives [builder] the last reading, greyed and struck through, and shows
     * it on the extra status bar icons too. With a null [builder] only the icons are updated.
     * Returns false when there is no reading recent enough to show.
     */
    @JvmStatic
    fun stale(builder: Notification.Builder?): Boolean = try {
        val context = Applic.app
        val config = NotificationConfigStore.load(context)
        val snapshot = WidgetDataSource.load(context, config.historyMillis(System.currentTimeMillis()))
        if (!snapshot.hasRecentReading) {
            GlucoseNotificationStyler.postStatusIcons(context, config, null, null)
            false
        } else {
            val valueText = GlucoseNotificationStyler.iconValueText(snapshot)
            GlucoseNotificationStyler.postStatusIcons(context, config, snapshot, valueText)
            if (builder != null) {
                GlucoseNotificationStyler.style(context, builder, config, snapshot, valueText)
                builder.setWhen(snapshot.currentTime)
                // Goes once the reading is too old to show; by then the plain message has replaced it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setTimeoutAfter(snapshot.lastReadingShownFor)
            }
            builder != null
        }
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "stale", th)
        false
    }
}
