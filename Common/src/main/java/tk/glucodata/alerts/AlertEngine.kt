package tk.glucodata.alerts

import tk.glucodata.Log
import java.time.LocalDateTime

/**
 * Decides which alert, if any, a new reading or a missing reading should raise.
 *
 * Every enabled rule is checked against the reading. Of the rules that match and are
 * due, the most severe one (see [AlertRule.priority]) rings; the milder rules of the
 * same direction that also match are marked as fired with it, so a drop through 70
 * and 55 in one reading rings once, while a later drop from 70 into 55 still escalates.
 */
object AlertEngine {
    private const val LOG_ID = "AlertEngine"

    /** Smallest gap between two firings of one alert, whatever its repeat setting. */
    private const val MIN_REFIRE_MS = 4 * 60_000L

    /** Rate magnitude (mg/dL/min) that counts as clearly recovering. */
    private const val RECOVERING_RATE = 0.5f

    /** Phone and watch both run this engine; the native alarm levels are no longer used. */
    @JvmStatic
    val isActive: Boolean
        get() = true

    data class Reading(
        val mgdl: Float,
        /** mg/dL per minute; NaN when unknown. */
        val rate: Float,
        val timeMillis: Long,
        /** Value formatted in the user's unit, as shown elsewhere in the app. */
        val displayValue: String
    )

    /**
     * Handles a new glucose reading. Returns true when an alert started ringing, so the
     * caller can skip its own spoken value.
     */
    @JvmStatic
    @Synchronized
    fun onGlucose(mgdl: Float, rate: Float, timeMillis: Long, displayValue: String): Boolean {
        if (!isActive || mgdl <= 0f) return false
        return try {
            AlertStore.ensureLoaded()
            evaluate(Reading(mgdl, rate, timeMillis, displayValue))
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "onGlucose", th)
            false
        }
    }

    private fun evaluate(reading: Reading): Boolean {
        val now = System.currentTimeMillis()
        val rules = AlertStore.rules.value
        val matching = rules.filter { it.enabled && matches(it, reading) }.toSet()

        // A fresh reading ends any signal loss episode, and closes episodes of rules
        // whose condition cleared, stopping them if they are still ringing.
        rules.forEach { rule ->
            val runtime = AlertStore.runtimeOf(rule.id)
            val cleared = rule.kind == AlertKind.SIGNAL_LOSS || rule !in matching
            if (cleared && runtime.inEpisode) {
                AlertStore.updateRuntime(rule.id) { it.copy(inEpisode = false) }
            }
            if (cleared && AlertPlayer.shownRuleId == rule.id && rule.stopWhenResolved) {
                Log.i(LOG_ID, "resolved ${rule.name}")
                AlertPlayer.stop(AlertPlayer.StopReason.RESOLVED)
            }
        }
        // Keep the ringing alert's notification current with the new value.
        AlertPlayer.updateReading(reading)

        val settings = AlertStore.settings.value
        if (!settings.enabled || settings.snoozeAllUntil > now) return false

        val localNow = LocalDateTime.now()
        val candidate = matching
            .filter { it.kind != AlertKind.SIGNAL_LOSS && isDue(it, now, localNow) }
            .minByOrNull { it.priority }
            ?: return false
        if (!AlertPlayer.canPlay(candidate)) return false

        fire(candidate, reading, now, sameDirectionAs(candidate, matching))
        return true
    }

    /**
     * Called on the periodic missing-signal check with the time of the last reading.
     * Returns the wall clock time at which the next check is needed, or 0 for none.
     */
    @JvmStatic
    @Synchronized
    fun checkSignalLoss(lastReadingMillis: Long): Long {
        if (!isActive || lastReadingMillis <= 0L) return 0L
        return try {
            AlertStore.ensureLoaded()
            val now = System.currentTimeMillis()
            val lossRules = AlertStore.rules.value.filter { it.enabled && it.kind == AlertKind.SIGNAL_LOSS }
            val settings = AlertStore.settings.value
            val localNow = LocalDateTime.now()
            val silent = !settings.enabled || settings.snoozeAllUntil > now
            val lost = lossRules.filter { now - lastReadingMillis >= it.lossMinutes * 60_000L }
            if (!silent) {
                lost.filter { isDue(it, now, localNow) }
                    .minByOrNull { it.lossMinutes }
                    ?.takeIf { AlertPlayer.canPlay(it) }
                    ?.let { rule ->
                        val minutes = ((now - lastReadingMillis) / 60_000L).toInt()
                        fire(rule, null, now, lost.toSet(), lostMinutes = minutes)
                    }
            }
            lossRules.minOfOrNull { rule ->
                val lossAt = lastReadingMillis + rule.lossMinutes * 60_000L
                if (lossAt > now) lossAt else nextDueTime(rule, now)
            } ?: 0L
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "checkSignalLoss", th)
            0L
        }
    }

    fun matches(rule: AlertRule, reading: Reading): Boolean {
        val rate = reading.rate
        val hasRate = rate.isFinite()
        return when (rule.kind) {
            AlertKind.LOW -> {
                if (rule.skipWhenRecovering && hasRate && rate >= RECOVERING_RATE) return false
                val predicted = if (rule.forecastMinutes > 0 && hasRate) reading.mgdl + rate * rule.forecastMinutes else reading.mgdl
                minOf(reading.mgdl, predicted) <= rule.thresholdMgdl
            }
            AlertKind.HIGH -> {
                if (rule.skipWhenRecovering && hasRate && rate <= -RECOVERING_RATE) return false
                val predicted = if (rule.forecastMinutes > 0 && hasRate) reading.mgdl + rate * rule.forecastMinutes else reading.mgdl
                maxOf(reading.mgdl, predicted) >= rule.thresholdMgdl
            }
            AlertKind.FALLING -> hasRate && rate <= -rule.rateMgdlPerMin &&
                (rule.thresholdMgdl <= 0f || reading.mgdl <= rule.thresholdMgdl)
            AlertKind.RISING -> hasRate && rate >= rule.rateMgdlPerMin &&
                (rule.thresholdMgdl <= 0f || reading.mgdl >= rule.thresholdMgdl)
            AlertKind.SIGNAL_LOSS -> false
        }
    }

    private fun isDue(rule: AlertRule, now: Long, localNow: LocalDateTime): Boolean {
        if (!rule.schedule.isActive(localNow)) return false
        val runtime = AlertStore.runtimeOf(rule.id)
        if (runtime.snoozedUntil > now) return false
        if (AlertPlayer.activeRuleId == rule.id) return false
        val sinceLast = now - runtime.lastFired
        if (sinceLast < MIN_REFIRE_MS) return false
        return if (rule.repeatMinutes <= 0) {
            !runtime.inEpisode
        } else {
            !runtime.inEpisode || sinceLast >= rule.repeatMinutes * 60_000L
        }
    }

    private fun nextDueTime(rule: AlertRule, now: Long): Long {
        val runtime = AlertStore.runtimeOf(rule.id)
        if (rule.repeatMinutes <= 0 && runtime.inEpisode) return 0L
        val repeatAt = runtime.lastFired + maxOf(rule.repeatMinutes * 60_000L, MIN_REFIRE_MS)
        return maxOf(repeatAt, runtime.snoozedUntil, now + 60_000L)
    }

    /** Rules that fire together with [rule]: same direction, all currently matching. */
    private fun sameDirectionAs(rule: AlertRule, matching: Set<AlertRule>): Set<AlertRule> =
        matching.filter { other ->
            when (rule.kind) {
                AlertKind.LOW, AlertKind.FALLING -> other.kind == AlertKind.LOW || other.kind == AlertKind.FALLING
                AlertKind.HIGH, AlertKind.RISING -> other.kind == AlertKind.HIGH || other.kind == AlertKind.RISING
                AlertKind.SIGNAL_LOSS -> other.kind == AlertKind.SIGNAL_LOSS
            }
        }.toSet()

    private fun fire(rule: AlertRule, reading: Reading?, now: Long, covered: Set<AlertRule>, lostMinutes: Int = 0) {
        Log.i(LOG_ID, "fire ${rule.name} value=${reading?.displayValue}")
        (covered + rule).forEach { other ->
            AlertStore.updateRuntime(other.id) { it.copy(lastFired = now, inEpisode = true) }
        }
        AlertPlayer.play(rule, reading, lostMinutes = lostMinutes)
    }
}
