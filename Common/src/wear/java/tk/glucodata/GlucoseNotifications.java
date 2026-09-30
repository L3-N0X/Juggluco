package tk.glucodata;

import android.app.Notification;
import android.graphics.drawable.Icon;

/** The watch keeps its plain glucose notification; see the phone's GlucoseNotifications. */
final class GlucoseNotifications {
    private GlucoseNotifications() {}

    static Icon valueIcon(String text) {
        return null;
    }

    static boolean style(Notification.Builder builder, notGlucose glucose, float displayValue, String valueText) {
        return false;
    }

    static void statusIcons(notGlucose glucose, float displayValue, String valueText) {
    }

    static boolean stale(Notification.Builder builder) {
        return false;
    }
}
