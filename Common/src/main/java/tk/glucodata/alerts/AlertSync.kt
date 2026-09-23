package tk.glucodata.alerts

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.MessageSender

/**
 * Keeps alerts in step between the phone and the Juggluco watch app over the Wear
 * message channel ([PATH]):
 *
 * - `ring`: an alert started; the other device rings the same alert too.
 * - `stop`: dismissed on one device, so it stops everywhere.
 * - `snooze` / `snoozeAll`: snoozes travel with the dismissal.
 * - `config`: the phone's alert list, applied on a watch that uses the phone's alerts.
 * - `requestConfig`: a watch asks the phone for its alert list.
 *
 * Events that arrive are applied without being sent on, so nothing loops.
 */
object AlertSync {
    private const val LOG_ID = "AlertSync"
    const val PATH = "/alerts"
    private const val CONFIG_DEBOUNCE_MS = 1500L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val pushRunnable = Runnable { pushConfig() }

    /** Hooks store changes to config pushes; called once the store is loaded. */
    fun install() {
        if (!Applic.isWearable) {
            AlertStore.onConfigChanged = {
                mainHandler.removeCallbacks(pushRunnable)
                mainHandler.postDelayed(pushRunnable, CONFIG_DEBOUNCE_MS)
            }
        }
    }

    /** True when a watch with Juggluco is reachable over the Wear channel. */
    fun hasWearPeer(): Boolean =
        runCatching { MessageSender.getMessageSender()?.nodes?.isNotEmpty() == true }.getOrDefault(false)

    // --- Outgoing ---------------------------------------------------------------------

    fun sendRing(alert: AlertPlayer.ActiveAlert) {
        val json = JSONObject()
            .put("t", "ring")
            .put("rule", alert.rule.toJson())
            .put("lost", alert.lostMinutes)
            .put("test", alert.isTest)
        alert.reading?.let { reading ->
            json.put(
                "reading", JSONObject()
                    .put("mgdl", reading.mgdl.toDouble())
                    .put("rate", if (reading.rate.isFinite()) reading.rate.toDouble() else JSONObject.NULL)
                    .put("time", reading.timeMillis)
                    .put("display", reading.displayValue)
            )
        }
        send(json)
    }

    fun sendStop(ruleId: String?) = send(JSONObject().put("t", "stop").put("rule", ruleId ?: JSONObject.NULL))

    fun sendSnooze(rule: AlertRule, until: Long) =
        send(JSONObject().put("t", "snooze").put("rule", rule.id).put("kind", rule.kind.name).put("until", until))

    fun sendSnoozeAll(until: Long) = send(JSONObject().put("t", "snoozeAll").put("until", until))

    /** Phone: sends the alert list to connected watches. */
    fun pushConfig() {
        if (Applic.isWearable) return
        AlertStore.ensureLoaded()
        val json = JSONObject()
            .put("t", "config")
            .put("rules", JSONArray(AlertRule.listToJson(AlertStore.rules.value)))
            .put("snoozeOptions", JSONArray(AlertStore.settings.value.snoozeOptions))
        send(json)
    }

    /** Watch: asks the phone for its alert list when this watch uses it. */
    fun requestConfig() {
        if (!Applic.isWearable) return
        AlertStore.ensureLoaded()
        if (AlertStore.settings.value.syncFromPhone) send(JSONObject().put("t", "requestConfig"))
    }

    private fun send(json: JSONObject) {
        try {
            MessageSender.sendAlerts(json.toString().toByteArray(Charsets.UTF_8))
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "send", th)
        }
    }

    // --- Incoming ---------------------------------------------------------------------

    @JvmStatic
    fun receive(data: ByteArray) {
        try {
            AlertStore.ensureLoaded()
            val json = JSONObject(String(data, Charsets.UTF_8))
            Log.i(LOG_ID, "receive ${json.optString("t")}")
            when (json.optString("t")) {
                "ring" -> receiveRing(json)
                "stop" -> AlertPlayer.stop(AlertPlayer.StopReason.REMOTE)
                "snooze" -> receiveSnooze(json)
                "snoozeAll" -> {
                    val until = json.optLong("until", 0L)
                    AlertStore.setSnoozeAllUntil(until)
                    if (until > System.currentTimeMillis()) AlertPlayer.stop(AlertPlayer.StopReason.REMOTE)
                }
                "config" -> receiveConfig(json)
                "requestConfig" -> pushConfig()
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "receive", th)
        }
    }

    private fun receiveRing(json: JSONObject) {
        val remoteRule = AlertRule.fromJson(json.getJSONObject("rule")) ?: return
        val isTest = json.optBoolean("test", false)
        val settings = AlertStore.settings.value
        val now = System.currentTimeMillis()
        if (!settings.mirrorAlerts || !settings.enabled || (!isTest && settings.isSnoozed(now))) return

        // The same alert when the lists are synced; otherwise the local alerts of that kind.
        val local = AlertStore.rule(remoteRule.id)
        val related = if (local != null) listOf(local) else AlertStore.rules.value.filter { it.kind == remoteRule.kind }
        if (!isTest) {
            if (related.any { AlertStore.runtimeOf(it.id).snoozedUntil > now }) return
            // Counts as fired here as well, so this device's own engine does not ring it again.
            related.forEach { rule -> AlertStore.updateRuntime(rule.id) { it.copy(lastFired = now, inEpisode = true) } }
        }
        val rule = local ?: remoteRule
        val shown = AlertPlayer.active.value
        if (shown != null && shown.ringing && shown.rule.id == rule.id) return
        if (!isTest && !AlertPlayer.canPlay(rule)) return

        val reading = json.optJSONObject("reading")?.let { r ->
            AlertEngine.Reading(
                mgdl = r.optDouble("mgdl", 0.0).toFloat(),
                rate = if (r.isNull("rate")) Float.NaN else r.optDouble("rate").toFloat(),
                timeMillis = r.optLong("time", now),
                displayValue = r.optString("display")
            )
        }
        AlertPlayer.play(rule, reading, lostMinutes = json.optInt("lost", 0), isTest = isTest, remote = true)
    }

    private fun receiveSnooze(json: JSONObject) {
        val until = json.optLong("until", 0L)
        val id = json.optString("rule")
        val kind = runCatching { AlertKind.valueOf(json.optString("kind")) }.getOrNull()
        val targets = AlertStore.rule(id)?.let { listOf(it) }
            ?: AlertStore.rules.value.filter { it.kind == kind }
        targets.forEach { rule -> AlertStore.updateRuntime(rule.id) { it.copy(snoozedUntil = until) } }
        AlertPlayer.stop(AlertPlayer.StopReason.REMOTE)
    }

    private fun receiveConfig(json: JSONObject) {
        if (!Applic.isWearable || !AlertStore.settings.value.syncFromPhone) return
        val rules = AlertRule.listFromJson(json.optJSONArray("rules")?.toString()) ?: return
        val options = json.optJSONArray("snoozeOptions")?.let { array ->
            (0 until array.length()).map { array.getInt(it) }.filter { it > 0 }
        } ?: emptyList()
        AlertStore.applyRemoteConfig(rules, options)
    }
}
