package tk.glucodata.alerts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import tk.glucodata.Applic
import tk.glucodata.R
import tk.glucodata.Log
import tk.glucodata.Natives

/**
 * Persistent home of the alert configuration. Rules and settings live in their own
 * SharedPreferences file as JSON; the fast-changing per-alert runtime state is kept
 * in a second file so editing an alert never races with it firing.
 */
object AlertStore {
    private const val LOG_ID = "AlertStore"
    private const val PREFS = "glucose_alerts"
    private const val RUNTIME_PREFS = "glucose_alerts_runtime"
    private const val KEY_RULES = "rules"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_RUNTIME = "runtime"

    private val _rules = MutableStateFlow<List<AlertRule>>(emptyList())
    val rules: StateFlow<List<AlertRule>> = _rules.asStateFlow()

    private val _settings = MutableStateFlow(AlertSettings())
    val settings: StateFlow<AlertSettings> = _settings.asStateFlow()

    private val _runtime = MutableStateFlow<Map<String, AlertRuntime>>(emptyMap())
    val runtime: StateFlow<Map<String, AlertRuntime>> = _runtime.asStateFlow()

    /** Called after the user changed rules or shared settings on this device. */
    @Volatile
    var onConfigChanged: (() -> Unit)? = null

    /** Watches only vibrate by default; the phone next to them does the ringing. */
    private val deviceDefaults: AlertSettings
        get() = AlertSettings(soundOnThisDevice = !Applic.isWearable)

    @Volatile
    private var loaded = false
    private lateinit var prefs: SharedPreferences
    private lateinit var runtimePrefs: SharedPreferences

    fun ensureLoaded(context: Context = Applic.app) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val app = context.applicationContext
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            runtimePrefs = app.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            val stored = AlertRule.listFromJson(prefs.getString(KEY_RULES, null))
            val initial = stored ?: (migrateFromNative() ?: defaultRules()).also {
                prefs.edit().putString(KEY_RULES, AlertRule.listToJson(it)).apply()
            }
            _rules.value = initial.sortedBy { it.priority }
            _settings.value = AlertSettings.fromJson(prefs.getString(KEY_SETTINGS, null), deviceDefaults)
            _runtime.value = readRuntime()
            loaded = true
        }
        AlertSync.install()
    }

    fun rule(id: String): AlertRule? {
        ensureLoaded()
        return _rules.value.firstOrNull { it.id == id }
    }

    fun upsert(rule: AlertRule) {
        ensureLoaded()
        synchronized(this) {
            val current = _rules.value
            val updated = if (current.any { it.id == rule.id }) {
                current.map { if (it.id == rule.id) rule else it }
            } else {
                current + rule
            }
            saveRules(updated)
        }
    }

    fun delete(id: String) {
        ensureLoaded()
        synchronized(this) {
            saveRules(_rules.value.filterNot { it.id == id })
            updateRuntime(id) { null }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        rule(id)?.let { upsert(it.copy(enabled = enabled)) }
    }

    fun resetToDefaults() {
        ensureLoaded()
        synchronized(this) {
            saveRules(defaultRules())
            _runtime.value = emptyMap()
            runtimePrefs.edit().remove(KEY_RUNTIME).apply()
        }
    }

    fun updateSettings(transform: (AlertSettings) -> AlertSettings) {
        ensureLoaded()
        val changed = synchronized(this) {
            val before = _settings.value
            val updated = transform(before)
            _settings.value = updated
            prefs.edit().putString(KEY_SETTINGS, updated.toJson().toString()).apply()
            before.snoozeOptions != updated.snoozeOptions
        }
        if (changed) onConfigChanged?.invoke()
    }

    /**
     * Replaces the alert list with one received from the phone. Runtime state stays,
     * so alerts keep their repeat timing, and device-local settings are untouched.
     */
    fun applyRemoteConfig(rules: List<AlertRule>, snoozeOptions: List<Int>) {
        ensureLoaded()
        synchronized(this) {
            val sorted = rules.sortedBy { it.priority }
            _rules.value = sorted
            prefs.edit().putString(KEY_RULES, AlertRule.listToJson(sorted)).apply()
            val updated = _settings.value.copy(snoozeOptions = snoozeOptions.ifEmpty { _settings.value.snoozeOptions })
            _settings.value = updated
            prefs.edit().putString(KEY_SETTINGS, updated.toJson().toString()).apply()
            val ids = sorted.map { it.id }.toSet()
            if (_runtime.value.keys.any { it !in ids }) {
                _runtime.value.keys.filter { it !in ids }.forEach { id -> updateRuntime(id) { null } }
            }
        }
    }

    fun runtimeOf(id: String): AlertRuntime {
        ensureLoaded()
        return _runtime.value[id] ?: AlertRuntime()
    }

    /** Applies [transform] to one alert's runtime state; returning null removes it. */
    fun updateRuntime(id: String, transform: (AlertRuntime) -> AlertRuntime?) {
        ensureLoaded()
        synchronized(this) {
            val map = _runtime.value.toMutableMap()
            val next = transform(map[id] ?: AlertRuntime())
            if (next == null) map.remove(id) else map[id] = next
            _runtime.value = map
            val json = JSONObject()
            map.forEach { (key, value) -> json.put(key, value.toJson()) }
            runtimePrefs.edit().putString(KEY_RUNTIME, json.toString()).apply()
        }
    }

    fun snoozeRule(id: String, minutes: Int) {
        val until = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        updateRuntime(id) { it.copy(snoozedUntil = until) }
    }

    /** Snoozes every alert here and on connected devices; 0 minutes resumes them. */
    fun snoozeAll(minutes: Int) {
        val until = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        setSnoozeAllUntil(until)
        AlertSync.sendSnoozeAll(until)
    }

    /** Applies a snooze-all end time without telling other devices. */
    fun setSnoozeAllUntil(until: Long) {
        updateSettings { it.copy(snoozeAllUntil = until) }
    }

    private fun saveRules(rules: List<AlertRule>) {
        val sorted = rules.sortedBy { it.priority }
        _rules.value = sorted
        prefs.edit().putString(KEY_RULES, AlertRule.listToJson(sorted)).apply()
        onConfigChanged?.invoke()
    }

    private fun readRuntime(): Map<String, AlertRuntime> {
        val text = runtimePrefs.getString(KEY_RUNTIME, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(text)
            json.keys().asSequence().associateWith { AlertRuntime.fromJson(json.getJSONObject(it)) }
        }.getOrDefault(emptyMap())
    }

    /** The starter set for new users: the safety-critical alerts on, the noisy ones off. */
    fun defaultRules(context: Context = Applic.getContext()): List<AlertRule> = listOf(
        AlertRule(
            name = context.getString(R.string.loc_alert_default_urgent_low), kind = AlertKind.LOW, thresholdMgdl = 55f,
            output = AlertOutput.ALARM, overrideDnd = true,
            vibrationPattern = VibrationPattern.URGENT,
            repeatMinutes = 5, playDurationSec = 0, fullScreen = true
        ),
        AlertRule(
            name = context.getString(R.string.loc_alert_default_low), kind = AlertKind.LOW, thresholdMgdl = 70f,
            output = AlertOutput.ALARM, vibrationPattern = VibrationPattern.PULSE,
            soundDelaySec = 30, rampUpSec = 20, skipWhenRecovering = true,
            repeatMinutes = 15, playDurationSec = 120, fullScreen = true
        ),
        AlertRule(
            name = context.getString(R.string.loc_alert_default_going_low), kind = AlertKind.LOW, thresholdMgdl = 70f, forecastMinutes = 20,
            enabled = false, output = AlertOutput.NOTIFICATION,
            vibrationPattern = VibrationPattern.GENTLE, skipWhenRecovering = true,
            repeatMinutes = 30, playDurationSec = 30
        ),
        AlertRule(
            name = context.getString(R.string.loc_alert_default_high), kind = AlertKind.HIGH, thresholdMgdl = 180f,
            output = AlertOutput.NONE, vibrationPattern = VibrationPattern.GENTLE,
            skipWhenRecovering = true, repeatMinutes = 60, playDurationSec = 15
        ),
        AlertRule(
            name = context.getString(R.string.loc_alert_default_very_high), kind = AlertKind.HIGH, thresholdMgdl = 250f,
            output = AlertOutput.ALARM, vibrationPattern = VibrationPattern.WAVE,
            rampUpSec = 20, skipWhenRecovering = true, repeatMinutes = 30, playDurationSec = 60
        ),
        AlertRule.template(AlertKind.FALLING).copy(enabled = false, thresholdMgdl = 150f),
        AlertRule.template(AlertKind.RISING).copy(enabled = false),
        AlertRule.template(AlertKind.SIGNAL_LOSS)
    )

    /**
     * Converts the four fixed native alarms (plus pre-alarms and signal loss) of an
     * existing installation into rules, so upgrading users keep their levels. Returns
     * null for a fresh install, which then gets [defaultRules].
     */
    private fun migrateFromNative(): List<AlertRule>? = try {
        val mmol = Natives.getunit() == 1
        fun mg(value: Float): Float = if (mmol) value * 18f else value
        // Only an installation that actually used alarms is migrated; everyone else,
        // including fresh installs, starts from the default set.
        val anyEnabled = Natives.hasalarmlow() || Natives.hasalarmhigh() || Natives.hasalarmloss() ||
            Natives.hasalarmverylow() || Natives.hasalarmveryhigh() ||
            Natives.hasalarmprelow() || Natives.hasalarmprehigh()
        if (!anyEnabled) {
            null
        } else {
            val output = when (Natives.getalarmSoundType()) {
                1 -> AlertOutput.NOTIFICATION
                2 -> AlertOutput.MEDIA
                else -> AlertOutput.ALARM
            }

            // Native kinds: 0 low, 1 high, 4 loss, 5 very low, 6 very high, 7 pre low, 8 pre high.
            fun fromNative(base: AlertRule, kind: Int, enabled: Boolean, level: Float): AlertRule {
                val ring = Natives.readring(kind)
                return base.copy(
                    enabled = enabled,
                    thresholdMgdl = if (level > 0f) mg(level) else base.thresholdMgdl,
                    output = if (Natives.alarmhassound(kind)) output else AlertOutput.NONE,
                    vibrate = Natives.alarmhasvibration(kind),
                    flash = Natives.alarmhasflash(kind),
                    overrideDnd = Natives.getalarmdisturb(kind),
                    soundUri = ring?.takeIf { it.isNotEmpty() },
                    repeatMinutes = Natives.readalarmsuspension(kind).toInt().coerceAtLeast(1),
                    playDurationSec = Natives.readalarmduration(kind).coerceAtLeast(5),
                    announce = Natives.speakalarms()
                )
            }

            val defaults = defaultRules()
            val urgent = defaults.first { it.thresholdMgdl == 55f && it.forecastMinutes == 0 }
            val low = defaults.first { it.thresholdMgdl == 70f && it.forecastMinutes == 0 }
            val goingLow = defaults.first { it.thresholdMgdl == 70f && it.forecastMinutes > 0 }
            val high = defaults.first { it.thresholdMgdl == 180f }
            val veryHigh = defaults.first { it.thresholdMgdl == 250f }
            val loss = defaults.first { it.kind == AlertKind.SIGNAL_LOSS }
            listOf(
                fromNative(urgent, 5, true, Natives.alarmverylow()),
                fromNative(low, 0, Natives.hasalarmlow(), Natives.alarmlow()),
                fromNative(goingLow, 7, Natives.hasalarmprelow(), Natives.alarmprelow())
                    .copy(forecastMinutes = 20),
                fromNative(high, 1, Natives.hasalarmhigh(), Natives.alarmhigh()),
                fromNative(veryHigh, 6, Natives.hasalarmveryhigh(), Natives.alarmveryhigh()),
                fromNative(
                    AlertRule(name = Applic.getContext().getString(R.string.loc_alert_default_going_high), kind = AlertKind.HIGH, thresholdMgdl = 170f, forecastMinutes = 20),
                    8, Natives.hasalarmprehigh(), Natives.alarmprehigh()
                ).copy(forecastMinutes = 20),
                defaults.first { it.kind == AlertKind.FALLING },
                defaults.first { it.kind == AlertKind.RISING },
                fromNative(loss, 4, Natives.hasalarmloss(), 0f).copy(
                    lossMinutes = Natives.readalarmsuspension(4).toInt().coerceAtLeast(5),
                    repeatMinutes = loss.repeatMinutes
                )
            )
        }
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "migrateFromNative", th)
        null
    }
}
