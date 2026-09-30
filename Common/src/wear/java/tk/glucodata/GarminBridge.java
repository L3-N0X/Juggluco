package tk.glucodata;

import java.util.Collections;
import java.util.List;

import tk.glucodata.ui.model.GarminLibre3Result;
import tk.glucodata.ui.model.GarminShortcut;
import tk.glucodata.ui.model.GarminShortcutError;
import tk.glucodata.ui.model.GarminStatus;

/**
 * The watch has no Garmin transport at all, so every call reports that there is
 * nothing to do. See the phone's GarminBridge for what each call means.
 */
public final class GarminBridge {
    private GarminBridge() {}

    public static boolean isSupported() {
        return false;
    }

    public static GarminStatus read() {
        return new GarminStatus();
    }

    public static void refreshDevices() {
    }

    public static void setEnabled(boolean enabled) {
        // There is no transport to start or stop, but the preference is still
        // stored, so the setting reads the same everywhere.
        try {
            Natives.setusegarmin(enabled);
        } catch (Throwable ignored) {
        }
    }

    public static boolean setActive(long peerId, boolean active) {
        return false;
    }

    public static boolean setGlucose(long peerId, boolean enabled) {
        return false;
    }

    public static GarminLibre3Result setLibre3Direct(long peerId, boolean enabled) {
        return GarminLibre3Result.NO_DEVICE;
    }

    public static boolean setNumbersDevice(long peerId, boolean enabled) {
        return false;
    }

    public static void setTransportMode(int mode) {
    }

    public static void sync(long peerId) {
    }

    public static void sendNextMessage(long peerId) {
    }

    public static void reinit(long peerId) {
    }

    public static void restartTransport() {
    }

    public static void setDarkMode(long peerId, boolean black) {
    }

    public static boolean saveAppId(String id) {
        return false;
    }

    public static List<GarminShortcut> shortcuts() {
        return Collections.emptyList();
    }

    public static GarminShortcutError saveShortcuts(List<GarminShortcut> shortcuts) {
        return null;
    }

    public static String helpHtml(String name) {
        return null;
    }
}
