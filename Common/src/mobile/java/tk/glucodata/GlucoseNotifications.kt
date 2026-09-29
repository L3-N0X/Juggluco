package tk.glucodata

import android.app.Notification
import android.graphics.drawable.Icon
import tk.glucodata.notifications.GlucoseNotificationStyler
import tk.glucodata.notifications.NotificationConfigStore
import tk.glucodata.notifications.StatusIconInput
import tk.glucodata.notifications.StatusIconKind
import tk.glucodata.ui.model.GlucoseUnit

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
}
