package tk.glucodata.ui.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The window the home screen compares the current reading against.
 *
 * The window is a *target*, not a promise: [findDeltaReference] only accepts a reading that is
 * genuinely that old, and reports the elapsed time it actually used so the number on screen can
 * be labelled with the window it really covers. A sensor that only reports every 15 minutes
 * therefore shows a 15 minute delta instead of silently pretending to be a 1 minute one.
 */
enum class DeltaCalculation(
    val minutes: Int,
    /** Fastest reading still considered a sensible comparison for this window. */
    private val minElapsedMillis: Long,
    /** Slowest reading still considered close enough to the target window. */
    private val maxElapsedMillis: Long
) {
    ONE_MINUTE(1, 30_000L, 100_000L),
    FIVE_MINUTES(5, 120_000L, 450_000L);

    /**
     * Beyond this there is no useful delta at all: the longest scan interval a Libre sensor
     * reports with. Older readings are not used, and the delta stays hidden.
     */
    val fallbackMaxMillis: Long = 15L * 60L * 1000L

    companion object {
        fun fromMinutes(minutes: Int): DeltaCalculation =
            if (minutes >= 5) FIVE_MINUTES else ONE_MINUTE

        /**
         * Finds the reading [currentReading] should be compared against, together with how long
         * ago it was taken.
         *
         * [readings] is sorted by timestamp, so instead of filtering the whole history up to four
         * times over to build four throwaway lists - which is what this used to do, on the main
         * thread, on every recomposition of a screen that shows the entire sensor store - it
         * binary-searches for the insertion point and walks backwards from there.
         *
         * The walk is bounded by [fallbackMaxMillis] before the target time. That is exact rather
         * than approximate: anything further away is, by construction, older than the bound and
         * would already have been rejected by the elapsed-time check below.
         */
        fun findDeltaReference(
            currentReading: GlucosePoint?,
            readings: List<GlucosePoint>,
            deltaCalculation: DeltaCalculation
        ): DeltaReference? {
            if (currentReading == null || readings.isEmpty()) return null

            val nowTime = currentReading.timestamp
            val wantCalibrated = currentReading.isCalibrated

            val targetDeltaMillis = deltaCalculation.minutes * 60_000L
            val targetTime = nowTime - targetDeltaMillis
            val scanFloor = maxOf(
                targetTime - deltaCalculation.fallbackMaxMillis,
                readings[0].timestamp
            )

            // Preference order, cheapest first: same calibration state, then any stream point, then
            // any non-scan point, then anything at all. Each tier keeps its own nearest-to-target
            // candidate, mirroring the filtered-list cascade this replaces.
            var bestSameCal: GlucosePoint? = null
            var bestSameCalDistance = Long.MAX_VALUE
            var bestStream: GlucosePoint? = null
            var bestStreamDistance = Long.MAX_VALUE
            var bestNonScan: GlucosePoint? = null
            var bestNonScanDistance = Long.MAX_VALUE
            var bestAny: GlucosePoint? = null
            var bestAnyDistance = Long.MAX_VALUE

            var index = insertionIndex(readings, nowTime) - 1
            while (index >= 0) {
                val point = readings[index]
                val timestamp = point.timestamp
                if (timestamp < scanFloor) break
                if (timestamp < nowTime) {
                    val distance = abs(timestamp - targetTime)
                    if (distance < bestAnyDistance) {
                        bestAnyDistance = distance
                        bestAny = point
                    }
                    if (!point.isScan) {
                        if (distance < bestNonScanDistance) {
                            bestNonScanDistance = distance
                            bestNonScan = point
                        }
                        if (!point.isHistory) {
                            if (distance < bestStreamDistance) {
                                bestStreamDistance = distance
                                bestStream = point
                            }
                            if (point.isCalibrated == wantCalibrated && distance < bestSameCalDistance) {
                                bestSameCalDistance = distance
                                bestSameCal = point
                            }
                        }
                    }
                }
                index--
            }

            val bestPoint = bestSameCal
                ?: bestStream
                ?: bestNonScan
                ?: bestAny
                ?: return null

            // Synthetic preview/test timestamps carry no real elapsed time, so report the window
            // the user asked for instead of a nonsense number of minutes.
            if (nowTime < SYNTHETIC_TIMESTAMP_CEILING) {
                return DeltaReference(bestPoint, targetDeltaMillis, true)
            }

            val elapsed = nowTime - bestPoint.timestamp
            if (elapsed in deltaCalculation.minElapsedMillis..deltaCalculation.maxElapsedMillis) {
                return DeltaReference(bestPoint, elapsed, true)
            }
            // Nothing landed close to the target window. Rather than report a wrong number, the
            // nearest usable reading is returned marked as a fallback so the UI can say how long
            // the change really covers.
            if (elapsed > deltaCalculation.fallbackMaxMillis) return null
            return DeltaReference(bestPoint, elapsed, false)
        }

        /** Index of the first element at or after [time] in a timestamp-sorted list. */
        private fun insertionIndex(readings: List<GlucosePoint>, time: Long): Int {
            var low = 0
            var high = readings.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (readings[mid].timestamp < time) low = mid + 1 else high = mid
            }
            return low
        }

        /** Timestamps below this are relative, not epoch milliseconds. */
        private const val SYNTHETIC_TIMESTAMP_CEILING = 1_000_000_000_000L
    }
}

/**
 * The reading a delta is measured against, plus the real distance in time to it.
 */
data class DeltaReference(
    val reading: GlucosePoint,
    val elapsedMillis: Long,
    /** False when no reading was available in the requested window and this is the nearest one. */
    val exactWindow: Boolean
) {
    val minutes: Int = (elapsedMillis / 60_000.0).roundToInt().coerceAtLeast(1)
}
