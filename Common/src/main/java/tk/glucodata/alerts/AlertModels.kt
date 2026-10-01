package tk.glucodata.alerts

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import tk.glucodata.Applic
import tk.glucodata.R

/** What an alert watches for. Glucose kinds compare against [AlertRule.thresholdMgdl]. */
enum class AlertKind {
    /** Glucose at or below the threshold (optionally predicted ahead). */
    LOW,
    /** Glucose at or above the threshold (optionally predicted ahead). */
    HIGH,
    /** Rate of change at or below -[AlertRule.rateMgdlPerMin]. */
    FALLING,
    /** Rate of change at or above [AlertRule.rateMgdlPerMin]. */
    RISING,
    /** No new reading for [AlertRule.lossMinutes]. */
    SIGNAL_LOSS,
    /** Time-based medication reminder, see [AlertRule.reminder]; never driven by readings. */
    REMINDER;

    val isGlucoseLevel: Boolean get() = this == LOW || this == HIGH
    val isRate: Boolean get() = this == FALLING || this == RISING
    val isReminder: Boolean get() = this == REMINDER
}

/**
 * Audio stream an alert plays on. The stream decides which volume slider and which
 * silent/Do Not Disturb rules apply; [NONE] plays no sound at all (vibrate only or silent).
 */
enum class AlertOutput(@StringRes val labelRes: Int) {
    ALARM(R.string.alarm),
    NOTIFICATION(R.string.notification),
    MEDIA(R.string.media),
    NONE(R.string.off)
}

/** Built-in vibration patterns; timings alternate off/on in milliseconds and loop. */
enum class VibrationPattern(@StringRes val labelRes: Int, val timings: LongArray) {
    URGENT(R.string.loc_vibration_urgent, longArrayOf(0, 1000, 400, 1000, 400, 1000, 1200)),
    PULSE(R.string.loc_vibration_pulse, longArrayOf(0, 700, 500, 700, 500, 700, 1500)),
    GENTLE(R.string.loc_vibration_gentle, longArrayOf(0, 400, 900, 400, 2000)),
    HEARTBEAT(R.string.loc_vibration_heartbeat, longArrayOf(0, 120, 120, 250, 1200)),
    RAPID(R.string.loc_vibration_rapid, longArrayOf(0, 100, 60, 100, 60, 100, 60, 100, 60, 100, 800)),
    WAVE(R.string.loc_vibration_wave, longArrayOf(0, 200, 100, 400, 100, 800, 1000)),
    SOS(R.string.loc_vibration_sos, longArrayOf(0, 150, 150, 150, 150, 150, 400, 500, 150, 500, 150, 500, 400, 150, 150, 150, 150, 150, 1500)),
    SHORT(R.string.loc_vibration_short, longArrayOf(0, 400, 200, 500, 3000)),
    CUSTOM(R.string.loc_vibration_custom, longArrayOf(0, 500, 500));

    companion object {
        fun fromKey(key: String?): VibrationPattern = entries.firstOrNull { it.name == key } ?: PULSE

        /** Parses "on,off,on,…" milliseconds as typed by the user; null when invalid. */
        fun parseCustom(text: String): LongArray? {
            val parts = text.split(',', ' ', ';').filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            val values = parts.map { it.trim().toLongOrNull() ?: return null }
            if (values.any { it < 0 || it > 10_000 } || values.sum() < 50) return null
            // Stored as on/off pairs, the waveform wants a leading off delay.
            return longArrayOf(0) + values.toLongArray()
        }
    }
}

/** Days and time window during which an alert may fire. */
data class AlertSchedule(
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = ALL_DAYS,
    val allDay: Boolean = true,
    /** Minutes after midnight; a window whose end is before its start wraps past midnight. */
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60
) {
    fun isActiveOn(day: DayOfWeek): Boolean = days and (1 shl (day.value - 1)) != 0

    fun isActive(now: LocalDateTime): Boolean {
        if (allDay || startMinute == endMinute) return isActiveOn(now.dayOfWeek)
        val minute = now.hour * 60 + now.minute
        return if (startMinute <= endMinute) {
            isActiveOn(now.dayOfWeek) && minute in startMinute until endMinute
        } else {
            // Overnight window: the part after midnight belongs to the day it started on.
            when {
                minute >= startMinute -> isActiveOn(now.dayOfWeek)
                minute < endMinute -> isActiveOn(now.dayOfWeek.minus(1))
                else -> false
            }
        }
    }

    val isAlways: Boolean get() = allDay && days == ALL_DAYS

    fun toJson(): JSONObject = JSONObject()
        .put("days", days)
        .put("allDay", allDay)
        .put("start", startMinute)
        .put("end", endMinute)

    companion object {
        const val ALL_DAYS = 0b111_1111
        const val WEEKDAYS = 0b001_1111
        const val WEEKEND = 0b110_0000

        fun fromJson(json: JSONObject?): AlertSchedule {
            if (json == null) return AlertSchedule()
            return AlertSchedule(
                days = json.optInt("days", ALL_DAYS),
                allDay = json.optBoolean("allDay", true),
                startMinute = json.optInt("start", 22 * 60).coerceIn(0, 24 * 60 - 1),
                endMinute = json.optInt("end", 7 * 60).coerceIn(0, 24 * 60 - 1)
            )
        }
    }
}

/** How a medication reminder recurs. */
enum class ReminderRepeat {
    /** On the days ticked in [AlertRule.schedule]. */
    DAYS_OF_WEEK,
    /** Every [ReminderSpec.intervalDays] days, counted from [ReminderSpec.startDay]. */
    EVERY_N_DAYS,
    /** Only on [ReminderSpec.startDay]. */
    ONCE
}

/**
 * What a medication reminder is about and when it is due. The logbook link carries over the
 * old numeric alarms: a reminder whose dose was already logged stays quiet, and confirming
 * one can log the dose.
 */
data class ReminderSpec(
    /** Free text such as "500 mg" or "2 tablets". */
    val dose: String = "",
    /** Free text such as "with food". */
    val note: String = "",
    /** Minutes after midnight, sorted and distinct. */
    val times: List<Int> = listOf(8 * 60),
    val repeat: ReminderRepeat = ReminderRepeat.DAYS_OF_WEEK,
    val intervalDays: Int = 2,
    /** Epoch day: the first day of [ReminderRepeat.EVERY_N_DAYS], the day of [ReminderRepeat.ONCE]. */
    val startDay: Long = LocalDate.now().toEpochDay(),
    /** Extra reminders while unconfirmed, [AlertRule.repeatMinutes] apart; [UNTIL_TAKEN] keeps going. */
    val maxRepeats: Int = 3,
    /** Native logbook label this medication is logged as; -1 when not linked. */
    val logLabel: Int = -1,
    /** Logged amount that counts as taken (0 = any amount), and the amount logged on "Taken". */
    val logAmount: Float = 0f,
    /** A matching logbook entry this long before the due time already counts as taken; 0 never skips. */
    val lookBackMinutes: Int = 120,
    /** Add [logAmount] of [logLabel] to the logbook when the reminder is confirmed. */
    val logWhenTaken: Boolean = false
) {
    val isLinked: Boolean get() = logLabel >= 0

    fun occursOn(date: LocalDate, days: Int): Boolean = when (repeat) {
        ReminderRepeat.DAYS_OF_WEEK -> days and (1 shl (date.dayOfWeek.value - 1)) != 0
        ReminderRepeat.EVERY_N_DAYS -> {
            val diff = date.toEpochDay() - startDay
            diff >= 0 && diff % intervalDays.coerceAtLeast(1) == 0L
        }
        ReminderRepeat.ONCE -> date.toEpochDay() == startDay
    }

    private fun millisOf(date: LocalDate, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli()

    /** First due time strictly after [after], or null when the reminder never comes again. */
    fun nextAfter(after: Long, days: Int, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (times.isEmpty()) return null
        val first = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        val horizon = 8 + intervalDays.coerceAtLeast(1)
        for (offset in 0..horizon) {
            val date = first.plusDays(offset.toLong())
            if (!occursOn(date, days)) continue
            times.sorted().forEach { minute ->
                val at = millisOf(date, minute, zone)
                if (at > after) return at
            }
        }
        return null
    }

    /** Latest due time in (`after`, `atOrBefore`], or null when there is none. */
    fun latestBetween(after: Long, atOrBefore: Long, days: Int, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (times.isEmpty() || atOrBefore <= after) return null
        val last = Instant.ofEpochMilli(atOrBefore).atZone(zone).toLocalDate()
        // Due times further back than a year are of no interest to anyone.
        val first = maxOf(Instant.ofEpochMilli(after).atZone(zone).toLocalDate(), last.minusDays(400))
        var date = last
        while (!date.isBefore(first)) {
            if (occursOn(date, days)) {
                times.sortedDescending().forEach { minute ->
                    val at = millisOf(date, minute, zone)
                    if (at <= atOrBefore) return at.takeIf { it > after }
                }
            }
            date = date.minusDays(1)
        }
        return null
    }

    /** True when a change to this spec moves the due times. */
    fun timingDiffers(other: ReminderSpec): Boolean =
        times != other.times || repeat != other.repeat || intervalDays != other.intervalDays || startDay != other.startDay

    fun toJson(): JSONObject = JSONObject()
        .put("dose", dose)
        .put("note", note)
        .put("times", JSONArray(times))
        .put("repeat", repeat.name)
        .put("interval", intervalDays)
        .put("startDay", startDay)
        .put("maxRepeats", maxRepeats)
        .put("logLabel", logLabel)
        .put("logAmount", logAmount.toDouble())
        .put("lookBack", lookBackMinutes)
        .put("logTaken", logWhenTaken)

    companion object {
        const val UNTIL_TAKEN = -1

        fun fromJson(json: JSONObject?): ReminderSpec {
            if (json == null) return ReminderSpec()
            val times = json.optJSONArray("times")?.let { array ->
                (0 until array.length()).map { array.optInt(it, -1) }.filter { it in 0 until 24 * 60 }
            }?.distinct()?.sorted()?.takeIf { it.isNotEmpty() } ?: listOf(8 * 60)
            return ReminderSpec(
                dose = json.optString("dose", ""),
                note = json.optString("note", ""),
                times = times,
                repeat = runCatching { ReminderRepeat.valueOf(json.getString("repeat")) }.getOrDefault(ReminderRepeat.DAYS_OF_WEEK),
                intervalDays = json.optInt("interval", 2).coerceIn(1, 365),
                startDay = json.optLong("startDay", LocalDate.now().toEpochDay()),
                maxRepeats = json.optInt("maxRepeats", 3).coerceAtLeast(UNTIL_TAKEN),
                logLabel = json.optInt("logLabel", -1),
                logAmount = json.optDouble("logAmount", 0.0).toFloat().coerceAtLeast(0f),
                lookBackMinutes = json.optInt("lookBack", 120).coerceIn(0, 24 * 60),
                logWhenTaken = json.optBoolean("logTaken", false)
            )
        }
    }
}

/**
 * One user-configurable alert. Every alert carries its own trigger, schedule and
 * delivery settings, so any number of them can exist side by side.
 */
data class AlertRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: AlertKind,
    val enabled: Boolean = true,

    // Trigger
    /** LOW/HIGH: the level. FALLING/RISING: optional guard (only below/above), 0 = any level. */
    val thresholdMgdl: Float = 70f,
    /** LOW/HIGH: also fire when the value predicted this many minutes ahead crosses the level. */
    val forecastMinutes: Int = 0,
    /** FALLING/RISING: absolute rate in mg/dL per minute. */
    val rateMgdlPerMin: Float = 2f,
    /** SIGNAL_LOSS: minutes without a reading. */
    val lossMinutes: Int = 20,
    /** Skip LOW while rising and HIGH while falling. */
    val skipWhenRecovering: Boolean = false,
    val schedule: AlertSchedule = AlertSchedule(),

    // Sound
    val output: AlertOutput = AlertOutput.ALARM,
    /** Sound URI; null uses the built-in default for this alert. */
    val soundUri: String? = null,
    /** Stream volume forced while ringing, in percent of the maximum; -1 keeps the current volume. */
    val volumePercent: Int = -1,
    val soundDelaySec: Int = 0,
    /** Seconds to fade from silent to full volume. */
    val rampUpSec: Int = 0,
    /** Ring and vibrate even while Do Not Disturb or silent mode is on. */
    val overrideDnd: Boolean = false,

    // Vibration
    val vibrate: Boolean = true,
    val vibrationPattern: VibrationPattern = VibrationPattern.PULSE,
    val customPattern: String = "",
    /** 1..100 percent of the motor's strength, where the device supports it. */
    val vibrationIntensity: Int = 100,
    val vibrationDelaySec: Int = 0,

    // Behaviour
    /** Re-alert interval while the condition persists; 0 alerts once per episode. */
    val repeatMinutes: Int = 15,
    /** How long sound and vibration run; 0 runs until dismissed. */
    val playDurationSec: Int = 120,
    val fullScreen: Boolean = false,
    val flash: Boolean = false,
    val announce: Boolean = false,
    /** Stop ringing on its own when a new reading no longer meets the condition. */
    val stopWhenResolved: Boolean = true,

    /** REMINDER: medication, times and logbook link. */
    val reminder: ReminderSpec = ReminderSpec()
) {
    val effectiveVibrationTimings: LongArray
        get() = if (vibrationPattern == VibrationPattern.CUSTOM) {
            VibrationPattern.parseCustom(customPattern) ?: VibrationPattern.PULSE.timings
        } else {
            vibrationPattern.timings
        }

    /**
     * Ordering used for the list and for picking between alerts that match at once:
     * the lowest lows first, then falling, the highest highs, rising and signal loss.
     */
    val priority: Float
        get() = when (kind) {
            AlertKind.LOW -> thresholdMgdl
            AlertKind.FALLING -> 1_000f - rateMgdlPerMin
            AlertKind.HIGH -> 2_000f - thresholdMgdl
            AlertKind.RISING -> 3_000f - rateMgdlPerMin
            AlertKind.SIGNAL_LOSS -> 4_000f + lossMinutes
            AlertKind.REMINDER -> 5_000f + (reminder.times.firstOrNull() ?: 0) / 10_000f
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("kind", kind.name)
        .put("enabled", enabled)
        .put("threshold", thresholdMgdl.toDouble())
        .put("forecast", forecastMinutes)
        .put("rate", rateMgdlPerMin.toDouble())
        .put("loss", lossMinutes)
        .put("skipRecovering", skipWhenRecovering)
        .put("schedule", schedule.toJson())
        .put("output", output.name)
        .put("sound", soundUri ?: JSONObject.NULL)
        .put("volume", volumePercent)
        .put("soundDelay", soundDelaySec)
        .put("rampUp", rampUpSec)
        .put("overrideDnd", overrideDnd)
        .put("vibrate", vibrate)
        .put("pattern", vibrationPattern.name)
        .put("customPattern", customPattern)
        .put("intensity", vibrationIntensity)
        .put("vibrationDelay", vibrationDelaySec)
        .put("repeat", repeatMinutes)
        .put("duration", playDurationSec)
        .put("fullScreen", fullScreen)
        .put("flash", flash)
        .put("announce", announce)
        .put("stopResolved", stopWhenResolved)
        .apply { if (kind == AlertKind.REMINDER) put("reminder", reminder.toJson()) }

    companion object {
        fun fromJson(json: JSONObject): AlertRule? {
            val kind = runCatching { AlertKind.valueOf(json.getString("kind")) }.getOrNull() ?: return null
            return AlertRule(
                id = json.optString("id").ifEmpty { UUID.randomUUID().toString() },
                name = json.optString("name", kind.name),
                kind = kind,
                enabled = json.optBoolean("enabled", true),
                thresholdMgdl = json.optDouble("threshold", 70.0).toFloat(),
                forecastMinutes = json.optInt("forecast", 0),
                rateMgdlPerMin = json.optDouble("rate", 2.0).toFloat(),
                lossMinutes = json.optInt("loss", 20),
                skipWhenRecovering = json.optBoolean("skipRecovering", false),
                schedule = AlertSchedule.fromJson(json.optJSONObject("schedule")),
                output = runCatching { AlertOutput.valueOf(json.getString("output")) }.getOrDefault(AlertOutput.ALARM),
                soundUri = if (json.isNull("sound")) null else json.optString("sound").ifEmpty { null },
                volumePercent = json.optInt("volume", -1),
                soundDelaySec = json.optInt("soundDelay", 0),
                rampUpSec = json.optInt("rampUp", 0),
                overrideDnd = json.optBoolean("overrideDnd", false),
                vibrate = json.optBoolean("vibrate", true),
                vibrationPattern = VibrationPattern.fromKey(json.optString("pattern")),
                customPattern = json.optString("customPattern", ""),
                vibrationIntensity = json.optInt("intensity", 100).coerceIn(1, 100),
                vibrationDelaySec = json.optInt("vibrationDelay", 0),
                repeatMinutes = json.optInt("repeat", 15),
                playDurationSec = json.optInt("duration", 120),
                fullScreen = json.optBoolean("fullScreen", false),
                flash = json.optBoolean("flash", false),
                announce = json.optBoolean("announce", false),
                stopWhenResolved = json.optBoolean("stopResolved", true),
                reminder = ReminderSpec.fromJson(json.optJSONObject("reminder"))
            )
        }

        fun listToJson(rules: List<AlertRule>): String =
            JSONArray().apply { rules.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String?): List<AlertRule>? {
            if (text.isNullOrEmpty()) return null
            return runCatching {
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
            }.getOrNull()
        }

        /** A fresh alert of [kind] with sensible starting values, used by "Add alert". */
        fun template(kind: AlertKind): AlertRule {
            val context = Applic.getContext()
            return when (kind) {
            AlertKind.LOW -> AlertRule(
                name = context.getString(R.string.loc_alert_choice_low), kind = kind, thresholdMgdl = 70f,
                output = AlertOutput.ALARM, vibrationPattern = VibrationPattern.PULSE,
                repeatMinutes = 15, playDurationSec = 120, skipWhenRecovering = true, fullScreen = true
            )
            AlertKind.HIGH -> AlertRule(
                name = context.getString(R.string.loc_alert_choice_high), kind = kind, thresholdMgdl = 180f,
                output = AlertOutput.NOTIFICATION, vibrationPattern = VibrationPattern.GENTLE,
                repeatMinutes = 60, playDurationSec = 30, skipWhenRecovering = true
            )
            AlertKind.FALLING -> AlertRule(
                name = context.getString(R.string.loc_alert_choice_falling), kind = kind, thresholdMgdl = 0f, rateMgdlPerMin = 2f,
                output = AlertOutput.NOTIFICATION, vibrationPattern = VibrationPattern.RAPID,
                repeatMinutes = 30, playDurationSec = 30
            )
            AlertKind.RISING -> AlertRule(
                name = context.getString(R.string.loc_alert_choice_rising), kind = kind, thresholdMgdl = 0f, rateMgdlPerMin = 2f,
                output = AlertOutput.NOTIFICATION, vibrationPattern = VibrationPattern.WAVE,
                repeatMinutes = 30, playDurationSec = 30
            )
            AlertKind.SIGNAL_LOSS -> AlertRule(
                name = context.getString(R.string.loc_alert_choice_loss), kind = kind, lossMinutes = 20,
                output = AlertOutput.NOTIFICATION, vibrationPattern = VibrationPattern.SHORT,
                repeatMinutes = 30, playDurationSec = 30
            )
            AlertKind.REMINDER -> AlertRule(
                name = context.getString(R.string.loc_reminder_default_name), kind = kind,
                output = AlertOutput.NOTIFICATION, vibrationPattern = VibrationPattern.GENTLE,
                repeatMinutes = 10, playDurationSec = 30, stopWhenResolved = false
            )
            }
        }
    }
}

/**
 * App-wide alert settings that are not tied to one alert. [snoozeAllUntil] and
 * [snoozeOptions] are shared with connected devices; the rest belongs to this device.
 */
data class AlertSettings(
    val enabled: Boolean = true,
    /** All alerts are quiet until this wall clock time (ms); 0 when not snoozed. */
    val snoozeAllUntil: Long = 0L,
    /** Snooze lengths offered on the notification and the full-screen alert, in minutes. */
    val snoozeOptions: List<Int> = listOf(15, 30, 60),
    /** Watch only: take the alert list from the phone instead of keeping its own. */
    val syncFromPhone: Boolean = true,
    /** When off, alerts on this device only vibrate. */
    val soundOnThisDevice: Boolean = true,
    /** Ring here when a connected phone or watch raises an alert. */
    val mirrorAlerts: Boolean = true,
    /**
     * Show full-screen alerts on the running screen too. Android only opens a full-screen
     * intent by itself while the device is locked or the screen is off, so this switch
     * makes every alert with a full-screen alert appear over whatever is in use.
     */
    val fullScreenOnActiveScreen: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("snoozeAllUntil", snoozeAllUntil)
        .put("snoozeOptions", JSONArray(snoozeOptions))
        .put("syncFromPhone", syncFromPhone)
        .put("soundHere", soundOnThisDevice)
        .put("mirror", mirrorAlerts)
        .put("fullScreenActive", fullScreenOnActiveScreen)

    fun isSnoozed(now: Long = System.currentTimeMillis()): Boolean = snoozeAllUntil > now

    companion object {
        fun fromJson(text: String?, defaults: AlertSettings = AlertSettings()): AlertSettings {
            if (text.isNullOrEmpty()) return defaults
            return runCatching {
                val json = JSONObject(text)
                val options = json.optJSONArray("snoozeOptions")?.let { array ->
                    (0 until array.length()).map { array.getInt(it) }.filter { it > 0 }
                }
                AlertSettings(
                    enabled = json.optBoolean("enabled", defaults.enabled),
                    snoozeAllUntil = json.optLong("snoozeAllUntil", 0L),
                    snoozeOptions = options?.takeIf { it.isNotEmpty() } ?: defaults.snoozeOptions,
                    syncFromPhone = json.optBoolean("syncFromPhone", defaults.syncFromPhone),
                    soundOnThisDevice = json.optBoolean("soundHere", defaults.soundOnThisDevice),
                    mirrorAlerts = json.optBoolean("mirror", defaults.mirrorAlerts),
                    fullScreenOnActiveScreen = json.optBoolean("fullScreenActive", defaults.fullScreenOnActiveScreen)
                )
            }.getOrDefault(defaults)
        }
    }
}

/** Per-alert state that changes while alerts fire; kept apart from the user's configuration. */
data class AlertRuntime(
    val lastFired: Long = 0L,
    val snoozedUntil: Long = 0L,
    /** True from the moment the alert fires until a reading no longer meets its condition. */
    val inEpisode: Boolean = false,

    // Reminders only
    /** Due time of the latest occurrence that was dealt with; later ones are still to come. */
    val handledDue: Long = 0L,
    /** Due time of the dose waiting for "Taken" or "Skip"; 0 when nothing is open. */
    val pendingDue: Long = 0L,
    /** How often the open dose has rung so far. */
    val ringCount: Int = 0,
    /** When a dose was last confirmed, by the user or by a matching logbook entry. */
    val lastTaken: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("lastFired", lastFired)
        .put("snoozedUntil", snoozedUntil)
        .put("inEpisode", inEpisode)
        .put("handledDue", handledDue)
        .put("pendingDue", pendingDue)
        .put("ringCount", ringCount)
        .put("lastTaken", lastTaken)

    companion object {
        fun fromJson(json: JSONObject): AlertRuntime = AlertRuntime(
            lastFired = json.optLong("lastFired", 0L),
            snoozedUntil = json.optLong("snoozedUntil", 0L),
            inEpisode = json.optBoolean("inEpisode", false),
            handledDue = json.optLong("handledDue", 0L),
            pendingDue = json.optLong("pendingDue", 0L),
            ringCount = json.optInt("ringCount", 0),
            lastTaken = json.optLong("lastTaken", 0L)
        )
    }
}

data class AlertEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val ruleId: String,
    val ruleName: String,
    val kind: AlertKind
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("timestamp", timestamp)
        .put("ruleId", ruleId)
        .put("ruleName", ruleName)
        .put("kind", kind.name)

    companion object {
        fun fromJson(json: JSONObject): AlertEvent? {
            val kind = runCatching { AlertKind.valueOf(json.getString("kind")) }.getOrNull() ?: return null
            val timestamp = json.optLong("timestamp", 0L)
            if (timestamp <= 0L) return null
            return AlertEvent(
                id = json.optString("id").ifEmpty { UUID.randomUUID().toString() },
                timestamp = timestamp,
                ruleId = json.optString("ruleId"),
                ruleName = json.optString("ruleName"),
                kind = kind
            )
        }

        fun listToJson(events: List<AlertEvent>): String =
            JSONArray().apply { events.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String?): List<AlertEvent> {
            if (text.isNullOrEmpty()) return emptyList()
            return runCatching {
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
                    .distinctBy { it.id }
                    .sortedBy { it.timestamp }
            }.getOrDefault(emptyList())
        }
    }
}
