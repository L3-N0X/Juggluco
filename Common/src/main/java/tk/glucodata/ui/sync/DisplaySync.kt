package tk.glucodata.ui.sync

import android.content.Context
import org.json.JSONObject
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.MessageSender
import tk.glucodata.Natives
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.data.NativeLabels
import tk.glucodata.ui.model.DeltaCalculation

/**
 * Keeps the shared display settings in step between the phone and the Juggluco watch app over
 * the Wear message channel ([PATH]):
 *
 * - `delta`: the delta window this device uses, tagged with the moment it was chosen.
 * - `labels`: the labels bolus and basal are saved under. Native sends the labels themselves from
 *   the device that owns them to the one that mirrors them, so this follows the same direction:
 *   only a device that can edit its labels sends, and only one that mirrors them applies.
 * - `request`: the other device asks for the values this one has.
 *
 * Every value travels with the moment it was chosen, so the most recent change wins. A device
 * that never had the setting changed sends no moment at all, which marks it as having no choice
 * of its own: the other device then keeps the one its user actually made instead. Received values
 * are applied without being sent back on, so nothing loops.
 *
 * Watches take their time from the phone, so the timestamps of both devices are directly
 * comparable.
 */
object DisplaySync {
    private const val LOG_ID = "DisplaySync"
    const val PATH = "/displaysettings"
    private const val PREFS = "display_sync"
    private const val KEY_DELTA_TIMESTAMP = "delta_calculation_timestamp"

    @Volatile
    private var repository: GlucoseRepository? = null

    /** Hands the sync its way back into the state the screens render. */
    fun install(repository: GlucoseRepository) {
        this.repository = repository
    }

    // --- Outgoing ---------------------------------------------------------------------

    /**
     * The user picked a new delta window here: stamp it as the newest decision and hand it to the
     * other device.
     */
    fun onLocalDeltaChange(calculation: DeltaCalculation) {
        val now = System.currentTimeMillis()
        writeDeltaTimestamp(now)
        push(calculation, now)
    }

    /** The user picked another bolus or basal label here. */
    fun onLocalLabelChange() {
        pushLabels()
    }

    /** Asks the other device for its delta window; used when this device starts. */
    fun request() {
        send(JSONObject().put("t", "request"))
    }

    /**
     * Sends this device's current delta window. A device that never had the setting changed sends
     * it without a timestamp, which tells the other side it has no choice of its own to defend.
     */
    private fun push(calculation: DeltaCalculation) {
        push(calculation, readDeltaTimestamp())
    }

    private fun push(calculation: DeltaCalculation, timestamp: Long) {
        send(
            JSONObject()
                .put("t", "delta")
                .put("minutes", calculation.minutes)
                .put("ts", timestamp)
        )
    }

    private fun pushLabels() {
        if (labelsMirrored()) return
        val (bolus, basal) = NativeLabels.storedInsulinLabels()
        if (bolus < 0 && basal < 0) return
        send(JSONObject().put("t", "labels").put("bolus", bolus).put("basal", basal))
    }

    private fun labelsMirrored(): Boolean = try { Natives.staticnum() } catch (_: Throwable) { false }

    private fun send(json: JSONObject) {
        try {
            MessageSender.sendDisplaySettings(json.toString().toByteArray(Charsets.UTF_8))
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "send", th)
        }
    }

    // --- Incoming ---------------------------------------------------------------------

    @JvmStatic
    fun receive(data: ByteArray) {
        try {
            val json = JSONObject(String(data, Charsets.UTF_8))
            when (json.optString("t")) {
                "delta" -> receiveDelta(json)
                "labels" -> receiveLabels(json)
                "request" -> {
                    push(repository?.displayConfig?.value?.deltaCalculation ?: DeltaCalculation.ONE_MINUTE)
                    pushLabels()
                }
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "receive", th)
        }
    }

    private fun receiveDelta(json: JSONObject) {
        val minutes = json.optInt("minutes", 0)
        if (minutes <= 0) return
        val remote = DeltaCalculation.fromMinutes(minutes)
        val localTimestamp = readDeltaTimestamp()
        // Without a repository there is no local value to compare against, so nothing to do.
        val current = repository?.displayConfig?.value?.deltaCalculation ?: return
        val remoteTimestamp = json.optLong("ts", 0L)
        if (remoteTimestamp <= 0L) {
            // The other device never had the setting changed, so it only has its default. This
            // device may still hold a choice the user actually made, and that one wins.
            if (localTimestamp > 0L) push(current, localTimestamp)
            return
        }
        if (remoteTimestamp < localTimestamp) {
            // Our own change is the newer one, so the other device has not seen it yet.
            Log.i(LOG_ID, "Sending the newer local delta window back: $current")
            push(current, localTimestamp)
            return
        }
        writeDeltaTimestamp(remoteTimestamp)
        if (current == remote) return
        Log.i(LOG_ID, "Applying the delta window from the other device: $remote")
        repository?.setDeltaCalculation(remote, fromRemote = true)
    }

    private fun receiveLabels(json: JSONObject) {
        if (!labelsMirrored()) return
        val bolus = json.optInt("bolus", -1)
        val basal = json.optInt("basal", -1)
        if (NativeLabels.applyMirroredInsulinLabels(bolus, basal)) {
            Log.i(LOG_ID, "Applying the bolus and basal labels of the other device: $bolus, $basal")
            repository?.refreshLabels()
        }
    }

    // --- Persistence ------------------------------------------------------------------

    private fun preferences() = try {
        Applic.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "preferences", th)
        null
    }

    private fun readDeltaTimestamp(): Long =
        try {
            preferences()?.getLong(KEY_DELTA_TIMESTAMP, 0L) ?: 0L
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "readDeltaTimestamp", th)
            0L
        }

    private fun writeDeltaTimestamp(timestamp: Long) {
        try {
            preferences()?.edit()?.putLong(KEY_DELTA_TIMESTAMP, timestamp)?.apply()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "writeDeltaTimestamp", th)
        }
    }
}
