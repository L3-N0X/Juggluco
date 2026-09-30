package tk.glucodata

import tk.glucodata.nums.AllData
import tk.glucodata.ui.model.GarminLibre3Result
import tk.glucodata.ui.model.GarminShortcut
import tk.glucodata.ui.model.GarminShortcutError
import tk.glucodata.ui.model.GarminShortcutField
import tk.glucodata.ui.model.GarminStatus
import tk.glucodata.ui.model.GarminWatchState
import java.util.ArrayList

/**
 * The Compose Garmin settings screen talking to the native Garmin transport.
 *
 * The transport (tk.glucodata.nums.AllData) only exists in the phone build, so
 * the watch builds get a stub with the same name and no watches to show.
 * Everything crossing this boundary is a plain model from
 * tk.glucodata.ui.model, never a native type.
 */
object GarminBridge {
    private const val LOG_ID = "GarminBridge"

    private fun transport(): AllData? = try {
        if (Applic.isWearable) null else Applic.app.numdata
    } catch (_: Throwable) {
        null
    }

    /** The watch builds have no Garmin transport at all. */
    @JvmStatic
    fun isSupported(): Boolean = transport() != null

    /**
     * Everything the status screen shows, read in one go.
     *
     * The native English state strings are passed through untouched; the screen
     * maps them onto localized text, the way the native Garmin status does.
     */
    @JvmStatic
    fun read(): GarminStatus {
        val all = transport() ?: return GarminStatus()
        return try {
            val context = AllData.getApplication()
            val watches = all.garminDeviceInfos.map { info ->
                GarminWatchState(
                    id = info.id,
                    name = info.name,
                    connected = info.connected,
                    direct = info.direct,
                    active = info.active,
                    glucose = info.glucose,
                    libre3Direct = info.libre3Direct,
                    libre3Installed = info.libre3Installed,
                    numbers = info.numbers,
                    darkMode = all.getKerfstokBlack(context, info.id),
                    communicationStatus = info.communicationStatus ?: "",
                    lastError = info.lastError,
                    transportResult = info.lastStatus?.name,
                    lastAcknowledged = info.lastAcknowledged,
                    lastGlucoseAcknowledged = info.lastGlucoseAcknowledged,
                    acknowledgedGlucoseTime = info.acknowledgedGlucoseTime,
                    timestampedGlucoseAck = info.timestampedGlucoseAck,
                    lastSend = info.lastSend,
                    lastReceived = info.lastReceived,
                    lastStatusTime = info.lastStatusTime,
                    waiting = all.waiting(info.id)
                )
            }
            GarminStatus(
                supported = true,
                watches = watches,
                transportMode = all.getGarminTransportMode(context),
                numbersDeviceName = watches.firstOrNull { it.numbers }?.name.orEmpty(),
                sending = all.isSending,
                sendGlucoseAvailable = all.usewatch,
                appInstalled = AllData.appmissing < 0,
                appId = Natives.getgarminid().orEmpty(),
                defaultAppId = Natives.getdefaultid().orEmpty()
            )
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "read", th)
            GarminStatus(supported = true)
        }
    }

    /**
     * Re-reads the paired Garmin Connect devices. Pairing happens in the Android
     * Bluetooth settings, so this is how a watch paired after start-up appears.
     * Never launches Kerfstok, which is why the status screen calls it on entry
     * only and then just reads.
     */
    @JvmStatic
    fun refreshDevices() {
        val all = transport() ?: return
        try {
            all.refreshGarminDevices(AllData.getApplication())
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "refreshDevices", th)
        }
    }

    /**
     * Turning Garmin on or off has to run the transport lifecycle, not just store
     * the preference: switching on hands the transport permission to launch
     * Kerfstok, switching off tears the transport down.
     */
    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        val all = transport() ?: return
        try {
            if (Natives.getusegarmin() == enabled) return
            Natives.setusegarmin(enabled)
            val context = AllData.getApplication()
            if (enabled) {
                all.reinit(context)
            } else {
                Natives.sethasgarmin(false)
                all.stop()
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setEnabled", th)
        }
    }

    @JvmStatic
    fun setActive(peerId: Long, active: Boolean): Boolean {
        val all = transport() ?: return false
        return try {
            all.setGarminActive(AllData.getApplication(), peerId, active)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setActive", th)
            false
        }
    }

    @JvmStatic
    fun setGlucose(peerId: Long, enabled: Boolean): Boolean {
        val all = transport() ?: return false
        return try {
            all.setGarminGlucose(AllData.getApplication(), peerId, enabled)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setGlucose", th)
            false
        }
    }

    /**
     * Hands the active Libre 3 sensor to the watch that takes over reading it.
     * The phone only lets go once the watch really has the sensor, so a missing
     * sensor is undone and reported instead of leaving a watch that cannot read.
     */
    @JvmStatic
    fun setLibre3Direct(peerId: Long, enabled: Boolean): GarminLibre3Result {
        val all = transport() ?: return GarminLibre3Result.NO_DEVICE
        return try {
            val context = AllData.getApplication()
            if (!all.setGarminLibre3Direct(context, peerId, enabled)) {
                GarminLibre3Result.NO_DEVICE
            } else if (enabled && !GarminLibre3.switchCurrentSensor()) {
                all.setGarminLibre3Direct(context, peerId, false)
                GarminLibre3Result.NO_SENSOR
            } else {
                GarminLibre3Result.APPLIED
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setLibre3Direct", th)
            GarminLibre3Result.NO_DEVICE
        }
    }

    /**
     * Hands the numbers role to this watch, or takes it away again. Only one
     * watch can hold it, and only the watch that holds it can give it up.
     */
    @JvmStatic
    fun setNumbersDevice(peerId: Long, enabled: Boolean): Boolean {
        val all = transport() ?: return false
        return try {
            all.setNumbersDeviceEnabled(AllData.getApplication(), peerId, enabled)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setNumbersDevice", th)
            false
        }
    }

    @JvmStatic
    fun setTransportMode(mode: Int) {
        val all = transport() ?: return
        try {
            all.setGarminTransportMode(AllData.getApplication(), mode)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setTransportMode", th)
        }
    }

    @JvmStatic
    fun sync(peerId: Long) {
        val all = transport() ?: return
        try {
            all.sync(peerId)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "sync", th)
        }
    }

    @JvmStatic
    fun sendNextMessage(peerId: Long) {
        val all = transport() ?: return
        try {
            all.nextmessage(peerId)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "sendNextMessage", th)
        }
    }

    @JvmStatic
    fun reinit(peerId: Long) {
        val all = transport() ?: return
        try {
            all.reinit(AllData.getApplication(), peerId)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "reinit", th)
        }
    }

    /**
     * Restarts the whole transport without a watch in mind, which is what a
     * changed application id needs: every watch has to be talked to again.
     */
    @JvmStatic
    fun restartTransport() {
        val all = transport() ?: return
        try {
            all.restartGarmin(AllData.getApplication())
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "restartTransport", th)
        }
    }

    @JvmStatic
    fun setDarkMode(peerId: Long, black: Boolean) {
        val all = transport() ?: return
        try {
            all.setcolor(AllData.getApplication(), peerId, black)
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setDarkMode", th)
        }
    }

    /**
     * Stores the ConnectIQ application id the watch has to be running. A null id
     * means the default one. Returns false when the native side refused the id.
     */
    @JvmStatic
    fun saveAppId(id: String?): Boolean = try {
        Natives.setgarminid(id)
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "saveAppId", th)
        false
    }

    @JvmStatic
    fun shortcuts(): List<GarminShortcut> = try {
        Natives.getShortcuts().mapNotNull { entry ->
            if (entry != null && entry.size >= 2) {
                GarminShortcut(entry[0]?.toString().orEmpty(), entry[1]?.toString().orEmpty())
            } else {
                null
            }
        }
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "shortcuts", th)
        emptyList()
    }

    /**
     * Writes the whole list and pushes it to the watch, which is the only place
     * these amounts are stored. The count is raised before the entries are
     * written, because the native side refuses an index it does not have yet.
     */
    @JvmStatic
    fun saveShortcuts(shortcuts: List<GarminShortcut>): GarminShortcutError? = try {
        val stored = Natives.getShortcuts()
        var error: GarminShortcutError? = null
        if (shortcuts.size > stored.size) Natives.setnrshortcuts(shortcuts.size)
        shortcuts.forEachIndexed { index, shortcut ->
            when (Natives.setShortcut(index, shortcut.label, shortcut.value)) {
                -1 -> Unit
                0 -> error = GarminShortcutError(index, GarminShortcutField.LABEL)
                1 -> error = GarminShortcutError(index, GarminShortcutField.VALUE)
                else -> error = GarminShortcutError(index)
            }
        }
        Natives.setnrshortcuts(shortcuts.size)
        if (error == null) sendShortcutsToWatch(shortcuts)
        error
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "saveShortcuts", th)
        GarminShortcutError(0)
    }

    private fun sendShortcutsToWatch(shortcuts: List<GarminShortcut>) {
        val all = transport() ?: return
        val list = ArrayList<ArrayList<Any>>(shortcuts.size)
        shortcuts.forEach { shortcut ->
            val entry = ArrayList<Any>(2)
            entry.add(shortcut.label)
            entry.add(shortcut.value)
            list.add(entry)
        }
        all.sendshortcuts(list)
    }

    /**
     * The native help pages live in the phone resources only, so the screen asks
     * for them by name and gets nothing on a build that has none.
     */
    @JvmStatic
    fun helpHtml(name: String): String? = try {
        val context = AllData.getApplication()
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id == 0) null else context.getString(id)
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "helpHtml", th)
        null
    }
}
