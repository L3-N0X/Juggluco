package tk.glucodata;

import java.util.ArrayList;
import java.util.List;

public class SensorBridge {

    public static class RawGattInfo {
        public String serial;
        public long dataptr;
        public long sensorptr;
        public int sensorgen;
        public String macAddress;
        public int rssi;
        public long startTime;
        public boolean isConnected;
        public boolean isStreaming;
        public String statusStr;
        public String handshakeStr;
        public String infoHtml;
        public int warmupMinutes;
        public int minWarmupMinutes;
        public boolean isHidden;
        public boolean hasCalibration;
    }

    public static List<RawGattInfo> getActiveGattSensors() {
        List<RawGattInfo> result = new ArrayList<>();
        try {
            ArrayList<SuperGattCallback> gatts = SensorBluetooth.mygatts();
            if (gatts != null) {
                for (SuperGattCallback gatt : gatts) {
                    if (gatt == null) continue;
                    RawGattInfo info = new RawGattInfo();
                    info.serial = gatt.SerialNumber != null ? gatt.SerialNumber : "Sensor";
                    info.dataptr = gatt.dataptr;
                    info.sensorptr = Natives.getsensorptr(gatt.dataptr);
                    info.sensorgen = gatt.sensorgen;
                    info.macAddress = gatt.mActiveDeviceAddress;
                    info.rssi = gatt.readrssi < 0 ? gatt.readrssi : -68;
                    info.startTime = gatt.sensorstartmsec > 0 ? gatt.sensorstartmsec : gatt.starttime;
                    info.isConnected = gatt.mBluetoothGatt != null;
                    info.isStreaming = gatt.streamingEnabled();
                    info.statusStr = gatt.constatstatusstr != null ? gatt.constatstatusstr : (info.isConnected ? "Connected" : "Disconnected");
                    info.handshakeStr = gatt.handshake != null ? gatt.handshake : "Keys exchanged";
                    info.infoHtml = gatt.getinfo() != null ? gatt.getinfo() : "";

                    if (info.sensorptr != 0L) {
                        info.warmupMinutes = Natives.getManualWarmupMinutes(info.sensorptr);
                        info.minWarmupMinutes = Natives.getMinimalWarmup(info.sensorptr);
                        info.isHidden = Natives.getHidefromSensorptr(info.sensorptr);
                        info.hasCalibration = Natives.calibrateNR(info.sensorptr, 0) > 0 || Natives.calibrateNR(info.sensorptr, 1) > 0;
                    } else {
                        info.warmupMinutes = 60;
                        info.minWarmupMinutes = 60;
                        info.isHidden = false;
                        info.hasCalibration = false;
                    }
                    result.add(info);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    public static void useAgain(MainActivity activity, long sensorptr) {
        try {
            if (Natives.useAgain(sensorptr)) {
                boolean res = SensorBluetooth.updateDevices();
                if (SuperGattCallback.glucosealarms != null) {
                    SuperGattCallback.glucosealarms.setLossAlarm();
                }
                if (activity != null) {
                    if (res) {
                        activity.finepermission();
                    } else {
                        activity.systemlocation();
                    }
                }
            }
            Applic.wakemirrors();
            if (activity != null) {
                activity.requestRender();
            }
        } catch (Throwable ignored) {}
    }

    public static void updateDevices() {
        try {
            SensorBluetooth.updateDevices();
        } catch (Throwable ignored) {}
    }

    public static void forgetDevice(String serial) {
        try {
            ArrayList<SuperGattCallback> gatts = SensorBluetooth.mygatts();
            if (gatts != null) {
                for (SuperGattCallback gatt : gatts) {
                    if (gatt != null && gatt.SerialNumber != null && gatt.SerialNumber.equals(serial)) {
                        gatt.searchforDeviceAddress();
                        gatt.close();
                        break;
                    }
                }
            }
            SensorBluetooth.startscan();
        } catch (Throwable ignored) {}
    }

    public static void setSystemUi(MainActivity activity, boolean fullscreen) {
        try {
            Natives.setsystemui(fullscreen);
            if (activity != null) {
                activity.selectionSystemUI();
            }
        } catch (Throwable ignored) {}
    }

    public static void initHealthConnect(MainActivity activity, boolean enable) {
        try {
            Natives.sethealthConnect(enable);
            if (activity != null) {
                if (enable) {
                    MainActivity.tryHealth = 5;
                    HealthConnection.Companion.init(activity);
                } else {
                    MainActivity.tryHealth = 0;
                    HealthConnection.Companion.stop();
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String[] getHostnames() {
        try {
            return Backup.gethostnames();
        } catch (Throwable ignored) {
            return new String[]{"null", "192.168.1.10", "null", "OK"};
        }
    }
}
