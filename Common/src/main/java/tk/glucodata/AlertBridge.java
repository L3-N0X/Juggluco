package tk.glucodata;

import static tk.glucodata.Applic.DontTalk;
import static tk.glucodata.Applic.isWearable;

import android.media.AudioAttributes;

import java.util.concurrent.TimeUnit;

/**
 * Entry points into package-private Juggluco internals for the Kotlin alert engine
 * in {@code tk.glucodata.alerts}.
 */
public final class AlertBridge {
    private static final String LOG_ID = "AlertBridge";

    private AlertBridge() {}

    public static void startFlash() {
        if (!isWearable) {
            try {
                Flash.start(Applic.app);
            } catch (Throwable th) {
                Log.stack(LOG_ID, "startFlash", th);
            }
        }
    }

    public static void stopFlash() {
        if (!isWearable) {
            try {
                Flash.stop();
            } catch (Throwable th) {
                Log.stack(LOG_ID, "stopFlash", th);
            }
        }
    }

    /** Forwards the alert to watches connected through WearOS integrations. */
    public static void alarmToWatches(String message) {
        if (!isWearable) {
            try {
                WearInt.alarm(message);
            } catch (Throwable th) {
                Log.stack(LOG_ID, "alarmToWatches", th);
            }
        }
    }

    /** Tells connected Juggluco instances (watch, mirrors) that the alert was dismissed here. */
    public static void stopAlarmOnPeers() {
        if (!isWearable) {
            try {
                var numdata = Applic.app.numdata;
                if (numdata != null) numdata.stopalarm();
            } catch (Throwable th) {
                Log.stack(LOG_ID, "stopAlarmOnPeers", th);
            }
        }
    }

    /** Speaks the glucose value, creating the text-to-speech engine when needed. */
    public static void speak(String value, AudioAttributes attributes) {
        if (DontTalk || value == null) return;
        try {
            if (SuperGattCallback.talker != null) {
                SuperGattCallback.talker.speak(value, attributes);
            } else {
                SuperGattCallback.newtalker(null);
                // The engine initialises asynchronously; give it a moment before speaking.
                Applic.scheduler.schedule(() -> {
                    var talker = SuperGattCallback.talker;
                    if (talker != null) talker.speak(value, attributes);
                }, 1500, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable th) {
            Log.stack(LOG_ID, "speak", th);
        }
    }

    /**
     * Uses the glucose value as the status bar icon, like the ongoing glucose notification.
     * Returns false when no icon was set.
     */
    public static boolean setGlucoseIcon(android.app.Notification.Builder builder, float displayValue) {
        var notify = Notify.onenot;
        var glucose = SuperGattCallback.previousglucose;
        if (notify == null || glucose == null || displayValue <= 0f) return false;
        try {
            notify.setIcon(builder, displayValue, glucose.sensorgen2);
            return true;
        } catch (Throwable th) {
            Log.stack(LOG_ID, "setGlucoseIcon", th);
            return false;
        }
    }

    /** Last reading shown in the app: formatted value, or null when there is none. */
    public static String lastGlucoseText() {
        var glucose = SuperGattCallback.previousglucose;
        return glucose == null ? null : glucose.value;
    }

    public static float lastGlucoseRate() {
        var glucose = SuperGattCallback.previousglucose;
        return glucose == null ? Float.NaN : glucose.rate;
    }
}
