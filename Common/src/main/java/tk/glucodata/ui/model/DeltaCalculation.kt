package tk.glucodata.ui.model

import kotlin.math.abs

enum class DeltaCalculation(val minutes: Int) {
    ONE_MINUTE(1),
    FIVE_MINUTES(5);

    companion object {
        fun fromMinutes(minutes: Int): DeltaCalculation =
            if (minutes >= 5) FIVE_MINUTES else ONE_MINUTE

        fun findDeltaReading(
            currentReading: GlucosePoint?,
            readings: List<GlucosePoint>,
            deltaCalculation: DeltaCalculation
        ): GlucosePoint? {
            if (currentReading == null || readings.isEmpty()) return null

            val nowTime = currentReading.timestamp
            val isStream = { p: GlucosePoint -> !p.isScan && !p.isHistory }
            val isCalibrated = currentReading.isCalibrated

            var candidates = readings.filter { it.timestamp < nowTime && isStream(it) && it.isCalibrated == isCalibrated }
            if (candidates.isEmpty()) {
                candidates = readings.filter { it.timestamp < nowTime && isStream(it) }
            }
            if (candidates.isEmpty()) {
                candidates = readings.filter { it.timestamp < nowTime && !it.isScan }
            }
            if (candidates.isEmpty()) {
                candidates = readings.filter { it.timestamp < nowTime }
            }
            if (candidates.isEmpty()) return null

            val targetDeltaMillis = deltaCalculation.minutes * 60_000L
            val targetTime = nowTime - targetDeltaMillis

            // Tolerances for acceptable delta window:
            // 1-minute delta: 20 seconds to 4 minutes
            // 5-minute delta: 2.5 minutes (150s) to 8 minutes (480s)
            val (minElapsed, maxElapsed) = when (deltaCalculation) {
                ONE_MINUTE -> Pair(20_000L, 240_000L)
                FIVE_MINUTES -> Pair(150_000L, 480_000L)
            }

            val idx = candidates.binarySearchBy(targetTime) { it.timestamp }
            val bestPoint = if (idx >= 0) {
                candidates[idx]
            } else {
                val insertIdx = -idx - 1
                val p1 = if (insertIdx > 0) candidates[insertIdx - 1] else null
                val p2 = if (insertIdx < candidates.size) candidates[insertIdx] else null
                when {
                    p1 != null && p2 != null -> {
                        if (abs(p1.timestamp - targetTime) <= abs(p2.timestamp - targetTime)) p1 else p2
                    }
                    p1 != null -> p1
                    p2 != null -> p2
                    else -> null
                }
            } ?: return null

            // For synthetic preview/test timestamps
            if (nowTime < 1_000_000_000_000L) {
                return bestPoint
            }

            val elapsed = nowTime - bestPoint.timestamp
            return if (elapsed in minElapsed..maxElapsed) bestPoint else null
        }
    }
}
