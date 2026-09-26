package tk.glucodata.ui.model

import kotlin.math.abs

enum class DeltaCalculation(val minutes: Int) {
    ONE_MINUTE(1),
    FIVE_MINUTES(5);

    companion object {
        fun fromMinutes(minutes: Int): DeltaCalculation =
            if (minutes >= 5) FIVE_MINUTES else ONE_MINUTE

        /**
         * Finds the reading [currentReading] should be compared against.
         *
         * [readings] is sorted by timestamp, so instead of filtering the whole history up to four
         * times over to build four throwaway lists - which is what this used to do, on the main
         * thread, on every recomposition of a screen that shows the entire sensor store - it
         * binary-searches for the insertion point and walks backwards from there.
         *
         * The walk is bounded by the distance at which a reading could still be considered the
         * nearest one. That is exact rather than approximate: anything further away is, by
         * construction, further from the target time than the bound, so it would already have been
         * rejected by the elapsed-time check below.
         */
        fun findDeltaReading(
            currentReading: GlucosePoint?,
            readings: List<GlucosePoint>,
            deltaCalculation: DeltaCalculation
        ): GlucosePoint? {
            if (currentReading == null || readings.isEmpty()) return null

            val nowTime = currentReading.timestamp
            val wantCalibrated = currentReading.isCalibrated

            // Tolerances for acceptable delta window:
            // 1-minute delta: 20 seconds to 4 minutes
            // 5-minute delta: 2.5 minutes (150s) to 8 minutes (480s)
            val minElapsed = when (deltaCalculation) {
                ONE_MINUTE -> 20_000L
                FIVE_MINUTES -> 150_000L
            }
            val maxElapsed = when (deltaCalculation) {
                ONE_MINUTE -> 240_000L
                FIVE_MINUTES -> 480_000L
            }

            val targetDeltaMillis = deltaCalculation.minutes * 60_000L
            val targetTime = nowTime - targetDeltaMillis
            val scanFloor = maxOf(
                targetTime - maxElapsed - 60_000L,
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

            // For synthetic preview/test timestamps
            if (nowTime < 1_000_000_000_000L) return bestPoint

            val elapsed = nowTime - bestPoint.timestamp
            return if (elapsed in minElapsed..maxElapsed) bestPoint else null
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
    }
}
